package demo.health.langgraph

import demo.health.domain.inmemory.InMemoryClinicRepository

class GraphConversationSuite extends munit.FunSuite:
  private def fixture() =
    val repo = InMemoryClinicRepository()
    val fake = FakeAiPorts(repo)
    (repo, fake, GraphConversationService(fake.ports, repo, Checkpoints.inMemory()))

  test("red flag: dừng ngay ở guardrail") {
    val (_, _, svc) = fixture()
    val r = svc.send("s", "BN001", "Tôi bị đau ngực và khó thở")
    assert(r.emergency)
    assertEquals(r.path, List("guardrail"))
    assert(r.reply.contains("115"))
  }

  test("FAQ: guardrail → router → faq, có nguồn") {
    val (_, _, svc) = fixture()
    val r = svc.send("s", "BN002", "Phòng khám mở cửa mấy giờ?")
    assertEquals(r.path, List("guardrail", "router", "faq"))
    assertEquals(r.sources, List("Phòng khám An Khang"))
  }

  test("Triage nhiều lượt: lượt 1 hỏi thêm, lượt 2 router giữ luồng triage và phân loại") {
    val (_, _, svc) = fixture()
    val r1 = svc.send("s", "BN002", "Tôi bị đau bụng")
    assert(r1.path.exists(_.endsWith("triage_ask")), r1.path)
    val r2 = svc.send("s", "BN002", "Đã 3 ngày rồi")
    assert(r2.steps.exists(_.contains("tiếp tục luồng triage")), r2.steps)
    assert(r2.reply.contains("Tiêu hoá"))
  }

  test("Booking: interrupt trước confirm_booking, resume thì đặt lịch và lưu tóm tắt") {
    val (repo, fake, svc) = fixture()
    val r1 = svc.send("s", "BN001", "đặt lịch khám tiêu hoá")
    assertEquals(r1.path.last, "booking")
    assert(r1.pending.isDefined)
    assert(svc.pending("s", "BN001").isDefined, "pending đọc lại được từ checkpoint")
    assert(repo.allAppointments.isEmpty)

    val blocked = svc.send("s", "BN001", "ừ")
    assert(blocked.reply.contains("chờ xác nhận"))
    assertEquals(fake.bookingCalls, 1)

    val r2 = svc.decide("s", "BN001", confirm = true)
    assertEquals(r2.path, List("confirm_booking", "summarize"))
    assert(r2.pending.isEmpty)
    assertEquals(repo.allAppointments.flatMap(_.summary).map(_.plan), List("Khám Tiêu hoá"))
  }

  test("Booking: huỷ thì không có lịch hẹn") {
    val (repo, _, svc) = fixture()
    svc.send("s", "BN001", "đặt lịch")
    val r = svc.decide("s", "BN001", confirm = false)
    assert(r.reply.contains("huỷ"))
    assert(repo.allAppointments.isEmpty)
  }

  test("Kết quả của lượt trước không lẫn vào lượt sau (sources, steps)") {
    val (_, _, svc) = fixture()
    svc.send("s", "BN002", "Phòng khám mở cửa mấy giờ?")
    val r = svc.send("s", "BN002", "hello")
    assertEquals(r.path, List("guardrail", "router", "other"))
    assertEquals(r.sources, Nil)
    assertEquals(r.steps.size, 3, r.steps)
  }

  test("Sau khi đặt lịch xong, lượt sau không còn pending và không đặt lại") {
    val (repo, _, svc) = fixture()
    svc.send("s", "BN001", "đặt lịch")
    svc.decide("s", "BN001", confirm = true)
    val r = svc.send("s", "BN001", "hello")
    assert(r.pending.isEmpty)
    assertEquals(repo.allAppointments.size, 1)
    assert(!r.steps.exists(_.startsWith("confirm_booking")), r.steps)
  }

  test("Mỗi bệnh nhân là một thread riêng") {
    val (_, _, svc) = fixture()
    svc.send("s", "BN001", "đặt lịch")
    assert(svc.pending("s", "BN002").isEmpty)
  }

  test("Sơ đồ Mermaid do LangGraph4j sinh ra") {
    val (_, _, svc) = fixture()
    assert(svc.diagram.contains("confirm_booking"))
  }
