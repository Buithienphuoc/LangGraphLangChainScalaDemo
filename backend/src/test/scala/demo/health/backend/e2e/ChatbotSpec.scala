package demo.health.backend.e2e

import demo.health.domain.inmemory.InMemoryClinicRepository
import demo.health.langchain.{BookingTools, MedicalSafetyOutputGuardrail, ProposalInbox}
import org.http4s.Status

/** Hành vi chatbot qua POST /api/chat với model giả (BOT-xx trong test specification).
  * Model giả route theo từ khoá ("đặt lịch", "thuốc", "mở cửa", "đau"); mọi node, tool, guardrail,
  * interrupt và checkpoint đều là code thật.
  */
class ChatbotSpec extends munit.FunSuite:
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private lazy val t = TestApp()
  private def fresh() = t.client()

  extension (r: Resp)
    def reply: String        = r.str("turn", "reply")
    def path: List[String]   = r.strs("turn", "path")
    def steps: List[String]  = r.strs("turn", "steps")
    def sources: List[String] = r.strs("turn", "sources")
    def emergency: Boolean   = r.at("turn", "emergency").asBoolean.getOrElse(false)
    def hasPending: Boolean  = !r.at("turn", "pending").isNull
    def intent: String       = r.steps.find(_.startsWith("router:")).map(_.drop(8).trim.takeWhile(_.isLetter)).getOrElse("-")
    def modelCalls: Int      = r.arr("turn", "modelCalls").size

  private def ok(r: Resp): Resp = { assertEquals(r.status, Status.Ok, r.body); r }
  private def appointments(b: Browser) = b.get("/api/appointments").json.asArray.get.toList
  private def noDosage(text: String) =
    assert(MedicalSafetyOutputGuardrail.Dosage.findFirstIn(text).isEmpty, s"câu trả lời chứa liều thuốc: $text")

  // ---------------------------------------------------------------- Router (F6)

  test("BOT-01 Router: câu hỏi giờ mở cửa → FAQ") {
    val r = ok(fresh().chat("BN002", "Phòng khám mở cửa mấy giờ?"))
    assertEquals(r.intent, "FAQ"); assertEquals(r.path, List("guardrail", "router", "faq"))
  }

  test("BOT-02 Router: kể triệu chứng → TRIAGE") {
    val r = ok(fresh().chat("BN002", "Tôi bị đau đầu 2 ngày nay"))
    assertEquals(r.intent, "TRIAGE"); assert(r.path.exists(_.startsWith("triage")), r.path)
  }

  test("BOT-03 Router: yêu cầu đặt lịch → BOOKING") {
    val b = fresh()
    val r = ok(b.chat("BN002", "Đặt lịch khám tim mạch thứ 3"))
    assertEquals(r.intent, "BOOKING"); assert(r.path.contains("booking"), r.path)
    b.decide("BN002", confirm = false)
  }

  test("BOT-04 Router: hỏi về thuốc → MEDICATION") {
    val r = ok(fresh().chat("BN002", "Uống thuốc ibuprofen cùng aspirin được không?"))
    assertEquals(r.intent, "MEDICATION"); assertEquals(r.path, List("guardrail", "router", "medication"))
    noDosage(r.reply)
  }

  test("BOT-05 Router: ngoài phạm vi → OTHER, trả lời mẫu, không gọi agent") {
    val r = ok(fresh().chat("BN002", "Giá vé máy bay đi Đà Nẵng bao nhiêu?"))
    assertEquals(r.intent, "OTHER"); assertEquals(r.path.last, "other")
    assert(r.reply.contains("chỉ hỗ trợ"), r.reply)
  }

  // ---------------------------------------------------------------- RAG (F1)

  test("BOT-06 RAG: trả lời giờ làm việc đúng tài liệu và có trích dẫn tài liệu thật") {
    val r = ok(fresh().chat("BN001", "Phòng khám mở cửa lúc mấy giờ vậy?"))
    assert(r.reply.contains("7:30") && r.reply.contains("17:00"), r.reply)
    assert(r.sources.nonEmpty, "thiếu trích dẫn")
    val titles = demo.health.langchain.KnowledgeBase.loadDocs().map(_.title).toSet
    r.sources.foreach(s => assert(titles.contains(s), s"trích dẫn '$s' không phải tài liệu có thật"))
    assert(r.steps.exists(_.startsWith("faq: RAG dùng")), r.steps)
  }

  // ---------------------------------------------------------------- Triage (F2)

  test("BOT-07 Triage thiếu thông tin: hỏi lại, chưa phân loại") {
    val r = ok(fresh().chat("BN002", "Tôi bị đau bụng"))
    assert(r.path.exists(_.endsWith("triage_ask")), r.path)
    assert(r.reply.contains("cho mình biết thêm"), r.reply)
    assert(!r.reply.contains("Chuyên khoa gợi ý"))
  }

  test("BOT-08 Triage nhiều lượt: lượt 2 giữ luồng triage dù không nhắc lại 'đau', đủ thông tin thì phân loại") {
    val b = fresh()
    ok(b.chat("BN002", "Tôi bị đau bụng"))
    val r = ok(b.chat("BN002", "Khoảng 3 ngày rồi"))
    assert(r.steps.exists(_.contains("tiếp tục luồng triage")), r.steps)
    assert(r.reply.contains("Chuyên khoa gợi ý") && r.reply.contains("Tiêu hoá"), r.reply)
    assert(r.reply.contains("không phải chẩn đoán"), "thiếu câu miễn trừ chẩn đoán")
  }

  test("BOT-09 Triage: trả lời mơ hồ mãi thì dừng hỏi sau tối đa 3 vòng và vẫn phân loại") {
    val b = fresh()
    val replies = List("Tôi bị đau", "vẫn đau", "đau lắm", "đau nhiều", "đau").map(m => ok(b.chat("BN003", m)))
    val asks = replies.takeWhile(!_.reply.contains("Chuyên khoa gợi ý"))
    assert(asks.size <= 3, s"hỏi lại ${asks.size} vòng")
    assert(replies.exists(_.reply.contains("Chuyên khoa gợi ý")), "không bao giờ phân loại")
  }

  test("BOT-10 Triage đủ thông tin ngay lượt đầu: phân loại và đề nghị đặt lịch") {
    val r = ok(fresh().chat("BN001", "Tôi đau bụng âm ỉ 3 ngày nay, mức vừa, không sốt"))
    assert(r.reply.contains("Nên đi khám trong tuần này"), r.reply)
    assert(r.reply.contains("đặt lịch"), r.reply)
  }

  test("BOT-11 Triage → đồng ý đặt lịch: trả lời 'Có' cho câu hỏi của bot phải chuyển sang đặt lịch") {
    val b = fresh()
    ok(b.chat("BN002", "Tôi đau bụng 3 ngày nay"))
    val r = ok(b.chat("BN002", "Có"))
    assertEquals(r.intent, "BOOKING", s"path=${r.path} reply=${r.reply}")
    assert(r.hasPending, r.body)
    b.decide("BN002", confirm = false)
  }

  // ---------------------------------------------------------------- Red flag / guardrail (F9)

  test("BOT-12 Red flag: đau ngực, khó thở → cảnh báo 115, dừng ở guardrail, không gọi LLM") {
    val r = ok(fresh().chat("BN001", "Tôi đau ngực dữ dội và khó thở"))
    assert(r.emergency); assert(r.reply.contains("115"))
    assertEquals(r.path, List("guardrail")); assertEquals(r.modelCalls, 0)
  }

  test("BOT-13 Red flag viết không dấu và giữa luồng triage vẫn được ưu tiên") {
    val b = fresh()
    ok(b.chat("BN002", "Tôi bị đau bụng"))
    val r = ok(b.chat("BN002", "gio toi thay kho tho qua"))
    assert(r.emergency, r.body); assertEquals(r.path, List("guardrail"))
  }

  test("BOT-14 Red flag khi đang có lịch chờ xác nhận vẫn phải cảnh báo 115") {
    val b = fresh()
    assert(ok(b.chat("BN003", "đặt lịch khám")).hasPending)
    val r = ok(b.chat("BN003", "Tôi đột nhiên bị đau ngực và khó thở"))
    assert(r.emergency && r.reply.contains("115"), s"red flag bị bỏ qua: ${r.reply}")
    b.decide("BN003", confirm = false)
  }

  test("BOT-15 Guardrail: xin kê đơn và liều kháng sinh → không đưa liều") {
    val r = ok(fresh().chat("BN001", "Kê đơn và cho tôi liều kháng sinh amoxicillin"))
    noDosage(r.reply)
    assert(!r.reply.toLowerCase.contains("đơn thuốc của bạn"), r.reply)
  }

  test("BOT-16 Output guardrail: model nêu liều thuốc thì bị reprompt, câu cuối không còn liều") {
    // FakeChatModel cố tình nêu "400 mg" ở câu trả lời thuốc đầu tiên của mỗi instance
    val app = TestApp()
    val r = ok(app.client().chat("BN003", "Tôi có uống thuốc ibuprofen được không?"))
    noDosage(r.reply)
    assert(r.reply.contains("bác sĩ") || r.reply.contains("dược sĩ"), r.reply)
    assert(r.modelCalls >= 3, s"không thấy lời gọi reprompt, modelCalls=${r.modelCalls}")
  }

  // ---------------------------------------------------------------- Booking (F3)

  test("BOT-17 Booking: gọi findDoctors → getAvailableSlots → proposeBooking, dừng ở interrupt, chưa tạo lịch") {
    val b = fresh()
    val before = appointments(b).size
    val r = ok(b.chat("BN001", "Tôi muốn đặt lịch khám tiêu hoá"))
    val tools = r.steps.filter(_.startsWith("tool ")).map(_.drop(5).takeWhile(_ != '('))
    assertEquals(tools, List("findDoctors", "getAvailableSlots", "proposeBooking"))
    assertEquals(r.path.last, "booking")
    assert(r.hasPending)
    assert(r.str("turn", "pending", "doctor").contains("Đặng Quốc Huy"), r.body)
    assert(!r.reply.toLowerCase.contains("đã đặt lịch"), s"nói đã đặt khi chưa xác nhận: ${r.reply}")
    assertEquals(appointments(b).size, before)
    b.decide("BN001", confirm = false)
  }

  test("BOT-18 Booking: đang chờ xác nhận thì tin nhắn mới không chạy lại agent") {
    val b = fresh()
    ok(b.chat("BN002", "đặt lịch"))
    val r = ok(b.chat("BN002", "ừ"))
    assert(r.reply.contains("chờ xác nhận"), r.reply); assert(r.hasPending)
    b.decide("BN002", confirm = false)
  }

  test("BOT-19 Booking resume: xác nhận → confirm_booking → summarize, tạo đúng 1 lịch, slot hết trống") {
    val b = fresh()
    val before = appointments(b).size
    val r1 = ok(b.chat("BN001", "đặt lịch khám tiêu hoá"))
    val slot = r1.str("turn", "pending", "slotId")
    val r2 = ok(b.decide("BN001", confirm = true))
    assertEquals(r2.path, List("confirm_booking", "summarize"))
    assert(r2.reply.contains("Đã đặt lịch"), r2.reply)
    assertEquals(appointments(b).size, before + 1)
    assert(t.wiring.repo.findSlot(slot).exists(_.booked), s"slot $slot chưa bị đánh dấu đã đặt")
  }

  test("BOT-20 Booking: bấm xác nhận 2 lần không tạo lịch trùng") {
    val b = fresh()
    ok(b.chat("BN003", "đặt lịch"))
    ok(b.decide("BN003", confirm = true))
    val n = appointments(b).size
    val again = ok(b.decide("BN003", confirm = true))
    assert(again.reply.contains("Không có lịch"), again.reply)
    assertEquals(appointments(b).size, n)
  }

  test("BOT-21 Booking: lần đặt kế tiếp không đề xuất lại slot đã đặt") {
    val b = fresh()
    val first = ok(b.chat("BN002", "đặt lịch")).str("turn", "pending", "slotId")
    ok(b.decide("BN002", confirm = true))
    val second = ok(b.chat("BN002", "đặt lịch thêm một buổi nữa")).str("turn", "pending", "slotId")
    assertNotEquals(second, first)
    b.decide("BN002", confirm = false)
  }

  test("BOT-22 Booking tool: chuyên khoa không có bác sĩ → báo không có và liệt kê chuyên khoa") {
    val tools = BookingTools(InMemoryClinicRepository(), ProposalInbox())
    val r = tools.findDoctors("Nhi khoa")
    assert(r.contains("Không có bác sĩ") && r.contains("Tim mạch"), r)
    assert(tools.proposeBooking("BN001:booking", "KHONG-CO", "x").contains("Không tìm thấy"))
  }

  // ---------------------------------------------------------------- Thuốc (F5)

  test("BOT-23 Thuốc: BN003 đang dùng aspirin hỏi ibuprofen → tool thấy tương tác aspirin + ibuprofen") {
    val r = ok(fresh().chat("BN003", "Tôi muốn uống thêm thuốc ibuprofen"))
    val tool = r.steps.find(_.startsWith("tool checkInteractions")).getOrElse(fail(s"không gọi tool: ${r.steps}"))
    assert(tool.contains("aspirin + ibuprofen"), tool)
    noDosage(r.reply)
  }

  test("BOT-24 Thuốc: bệnh nhân không dùng thuốc nào → tool báo không thấy tương tác") {
    val r = ok(fresh().chat("BN002", "Tôi uống thuốc ibuprofen được không?"))
    assert(r.steps.exists(s => s.startsWith("tool checkInteractions") && s.contains("Không thấy tương tác")), r.steps)
  }

  // ---------------------------------------------------------------- Memory / tách thread (F4)

  test("BOT-25 Tách thread: luồng triage của BN001 không lẫn sang BN002 cùng trình duyệt") {
    val b = fresh()
    ok(b.chat("BN001", "Tôi bị đau bụng"))
    val r = ok(b.chat("BN002", "Khoảng 3 ngày rồi"))
    assert(!r.steps.exists(_.contains("tiếp tục luồng triage")), s"BN002 kế thừa triage của BN001: ${r.steps}")
  }

  test("BOT-26 Tách session: trình duyệt khác cùng bệnh nhân không kế thừa luồng triage") {
    val a = fresh(); val other = fresh()
    ok(a.chat("BN003", "Tôi bị đau bụng"))
    val r = ok(other.chat("BN003", "Khoảng 3 ngày rồi"))
    assert(!r.steps.exists(_.contains("tiếp tục luồng triage")), r.steps)
  }

  test("BOT-27 Lượt mới không lẫn kết quả lượt trước (sources, trace, emergency)") {
    val b = fresh()
    ok(b.chat("BN001", "Tôi đau ngực"))
    val r = ok(b.chat("BN001", "Phòng khám mở cửa mấy giờ?"))
    assert(!r.emergency); assert(!r.steps.exists(_.contains("RED FLAG")), r.steps)
    assertEquals(r.path, List("guardrail", "router", "faq"))
  }

  // ---------------------------------------------------------------- SOAP (F7) và quan sát (F8)

  test("BOT-28 SOAP: sau triage rồi đặt lịch, tóm tắt đủ S/O/A/P và lưu vào lịch hẹn") {
    val b = fresh()
    ok(b.chat("BN001", "Tôi đau bụng 3 ngày nay"))
    ok(b.chat("BN001", "đặt lịch khám giúp tôi"))
    val r = ok(b.decide("BN001", confirm = true))
    assert(r.steps.exists(_.startsWith("summarize")), r.steps)
    assert(r.reply.contains("tóm tắt cho bác sĩ"), r.reply)
    val a = appointments(b).last.hcursor.downField("summary")
    List("subjective", "objective", "assessment", "plan").foreach(k =>
      assert(a.get[String](k).exists(_.nonEmpty), s"SOAP thiếu $k"))
  }

  test("BOT-29 Quan sát: lượt đặt lịch ghi lời gọi model và tool theo đúng thứ tự node") {
    val b = fresh()
    val r = ok(b.chat("BN002", "đặt lịch"))
    assertEquals(r.path, List("guardrail", "router", "booking"))
    assert(r.modelCalls >= 4, s"modelCalls=${r.modelCalls} (router + 3 tool + câu trả lời)")
    val idx = List("guardrail:", "router:", "tool findDoctors", "booking:").map(p => r.steps.indexWhere(_.startsWith(p)))
    assert(idx.forall(_ >= 0) && idx == idx.sorted, s"thứ tự trace sai: ${r.steps}")
    b.decide("BN002", confirm = false)
  }
