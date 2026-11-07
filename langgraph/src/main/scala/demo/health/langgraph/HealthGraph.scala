package demo.health.langgraph

import demo.health.domain.model.*
import demo.health.domain.port.*
import demo.health.langgraph.HealthState.*
import org.bsc.langgraph4j.*
import org.bsc.langgraph4j.GraphDefinition.{END, START}
import org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async
import org.bsc.langgraph4j.action.AsyncNodeAction.node_async
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver
import scala.jdk.CollectionConverters.*

/** Supervisor graph (F6). Chỉ dùng port của domain: graph không biết LLM được gọi bằng thư viện nào.
  *
  *   START → guardrail ─(khẩn cấp)→ END
  *                     └→ router ─→ faq | triage (subgraph) | booking | medication | other
  *   booking ─(có đề xuất)→ confirm_booking   [interrupt trước node này: chờ người dùng bấm]
  *   confirm_booking ─(đã đặt)→ summarize → END
  */
final class HealthGraph(ai: AiPorts, repo: ClinicRepository):

  // ---------------------------------------------------------------- nodes

  /** F9: kiểm tra red flag bằng luật của domain, trước mọi lời gọi LLM. */
  private def guardrail(s: HealthState): java.util.Map[String, Object] =
    RedFlags.detect(s.input) match
      case Nil => out(Emergency -> "false", steps("guardrail: không thấy dấu hiệu khẩn cấp"))
      case hits =>
        out(Emergency -> "true", Reply -> RedFlags.emergencyMessage, ActiveFlow -> "",
          steps(s"guardrail: RED FLAG (${hits.map(_.label).mkString(", ")}) → dừng, khuyên gọi 115"))

  /** Router: phân loại ý định. Nếu đang giữa luồng triage thì ưu tiên ở lại. */
  private def router(s: HealthState): java.util.Map[String, Object] =
    val decision = ai.intents.classify(s.input)
    val inTriage = s.str(ActiveFlow) == "triage"
    // Router chỉ thấy tin nhắn hiện tại. Câu trả lời ngắn kiểu "Có" cho lời mời đặt lịch của triage
    // không mang ý định riêng, nên phải dựa vào ngữ cảnh lượt trước.
    val acceptsOffer = s.flag(BookingOffered) && decision.intent == Intent.OTHER && HealthGraph.isAffirmative(s.input)
    val intent =
      if acceptsOffer then Intent.BOOKING
      else if inTriage && (decision.intent == Intent.OTHER || decision.intent == Intent.TRIAGE) then Intent.TRIAGE
      else decision.intent
    val note =
      if acceptsOffer then " (bệnh nhân đồng ý lời mời đặt lịch sau sàng lọc)"
      else if inTriage && intent == Intent.TRIAGE then " (tiếp tục luồng triage đang dở)" else ""
    val clear = if intent != Intent.TRIAGE then Seq(ActiveFlow -> "", TriageNotes -> "", AskCount -> 0) else Nil
    out((Seq(IntentKey -> intent.toString, BookingOffered -> "false",
      steps(s"router: $intent$note, lý do: ${decision.reason}")) ++ clear)*)

  private def toolSteps(r: AssistantReply): List[String] =
    r.toolCalls.map(t => s"tool ${t.name}(${t.arguments}) → ${t.result.replace('\n', ' ').take(140)}")

  /** F1: hỏi đáp có RAG. */
  private def faq(s: HealthState): java.util.Map[String, Object] =
    val r = ai.knowledge.answer(s.patientId, s.input)
    out(Reply -> r.text, Sources -> r.sources,
      steps(s"faq: RAG dùng ${r.sources.size} tài liệu" + (if r.sources.nonEmpty then s": ${r.sources.mkString("; ")}" else "")))

  private def other(s: HealthState): java.util.Map[String, Object] =
    out(Reply -> ("Mình là trợ lý của phòng khám nên chỉ hỗ trợ được: thông tin phòng khám, kiến thức sức khoẻ, " +
      "sàng lọc triệu chứng, đặt lịch và câu hỏi về thuốc. Bạn cần giúp gì trong các việc này?"),
      steps("other: ngoài phạm vi, trả lời mẫu (không gọi LLM)"))

  // --- Triage subgraph (F2)

  private def triageExtract(s: HealthState): java.util.Map[String, Object] =
    val notes   = (s.str(TriageNotes) + "\n" + s.input).trim
    val profile = repo.findPatient(s.patientId).map(_.profileText).getOrElse("")
    val report  = ai.triage.extract(s"$profile\n\nLời kể của bệnh nhân:\n$notes")
    out(TriageNotes -> notes, TriageReport -> report.describe,
      "triageComplete" -> report.isComplete.toString, "triageMissing" -> report.missingInfo,
      steps(s"triage/extract: SymptomReport, đủ thông tin = ${report.isComplete}"))

  private def triageAsk(s: HealthState): java.util.Map[String, Object] =
    val missing = s.value[java.util.List[String]]("triageMissing").map(_.asScala.toList).orElse(Nil)
    val reply   = ("Để sàng lọc chính xác hơn, bạn cho mình biết thêm:" :: missing.map("- " + _)).mkString("\n")
    out(Reply -> reply, ActiveFlow -> "triage", AskCount -> (s.int(AskCount) + 1),
      steps(s"triage/ask: hỏi thêm (vòng ${s.int(AskCount) + 1}/2), dừng lượt chờ bệnh nhân trả lời"))

  private def triageClassify(s: HealthState): java.util.Map[String, Object] =
    val r = ai.triage.classify(repo.specialties, s.str(TriageReport))
    val reply =
      s"""**${r.urgency.label}**. Chuyên khoa gợi ý: **${r.specialty}**.
         |
         |${r.explanation}
         |${r.advice.map("- " + _).mkString("\n")}
         |
         |Bạn có muốn mình đặt lịch khám ${r.specialty} không?
         |
         |_Đây là sàng lọc tự động, không phải chẩn đoán._""".stripMargin
    out(Reply -> reply, ActiveFlow -> "", TriageNotes -> "", AskCount -> 0, SuggestedSpecialty -> r.specialty,
      BookingOffered -> "true",
      TriageSummary -> s"${s.str(TriageReport)}\nMức ưu tiên: ${r.urgency.label}\nChuyên khoa gợi ý: ${r.specialty}",
      steps(s"triage/classify: ${r.urgency} → ${r.specialty}"))

  /** Subgraph: một StateGraph riêng, gắn vào supervisor như một node. */
  private def triageGraph: StateGraph[HealthState] =
    StateGraph[HealthState](HealthState.channels, s => HealthState.factory(s))
      .addNode("triage_extract", node(triageExtract))
      .addNode("triage_ask", node(triageAsk))
      .addNode("triage_classify", node(triageClassify))
      .addEdge(START, "triage_extract")
      .addConditionalEdges("triage_extract",
        edge_async[HealthState](s => if s.flag("triageComplete") || s.int(AskCount) >= 2 then "classify" else "ask"),
        Map("classify" -> "triage_classify", "ask" -> "triage_ask").asJava)
      .addEdge("triage_ask", END)
      .addEdge("triage_classify", END)

  // --- Booking (F3) với human-in-the-loop

  private def booking(s: HealthState): java.util.Map[String, Object] =
    val hint = Option(s.str(SuggestedSpecialty)).filter(_.nonEmpty)
      .map(sp => s"\n\n(Ghi chú hệ thống: kết quả sàng lọc gần nhất gợi ý chuyên khoa $sp.)").getOrElse("")
    val msg  = if HealthGraph.isAffirmative(s.input) && hint.nonEmpty then s"Có, đặt lịch khám giúp tôi. (${s.input})" else s.input
    val r = ai.booking.chat(s.patientId, msg + hint)
    r.proposal match
      case Some(p) =>
        out(Reply -> r.reply.text, PendingSlot -> p.slotId, PendingReason -> p.reason, Decision -> "",
          steps((toolSteps(r.reply) :+ s"booking: có đề xuất ${p.slotId} → confirm_booking (graph dừng chờ xác nhận)")*))
      case None =>
        out(Reply -> r.reply.text, PendingSlot -> "", steps((toolSteps(r.reply) :+ "booking: chưa có đề xuất đặt lịch")*))

  /** Node nằm sau điểm interrupt: chỉ chạy khi người dùng đã bấm Xác nhận hoặc Huỷ. */
  private def confirmBooking(s: HealthState): java.util.Map[String, Object] =
    if s.str(Decision) != "confirm" then
      out(Reply -> "Đã huỷ đề xuất đặt lịch. Bạn có thể chọn khung giờ khác nếu muốn.", PendingSlot -> "",
        steps("confirm_booking: người dùng HUỶ"))
    else
      repo.book(s.patientId, s.str(PendingSlot), s.str(PendingReason)) match
        case Left(err) => out(Reply -> s"Không đặt được lịch: $err", PendingSlot -> "", steps(s"confirm_booking: lỗi $err"))
        case Right(a) =>
          val doctor = repo.findDoctor(a.doctorId).map(_.name).getOrElse(a.doctorId)
          out(Reply -> s"✅ Đã đặt lịch **${a.id}** với $doctor lúc ${Text.time(a.startAt)}.",
            AppointmentId -> a.id, PendingSlot -> "",
            steps(s"confirm_booking: người dùng XÁC NHẬN → repo.book ${a.id}"))

  /** F7: tóm tắt SOAP cho bác sĩ, lưu vào lịch hẹn. */
  private def summarize(s: HealthState): java.util.Map[String, Object] =
    val profile = repo.findPatient(s.patientId).map(_.profileText).getOrElse("")
    val context =
      s"""$profile
         |Lý do khám: ${s.str(PendingReason)}
         |Kết quả sàng lọc (nếu có):
         |${s.str(TriageSummary)}""".stripMargin
    repo.saveSummary(s.str(AppointmentId), ai.summarizer.summarize(context))
    out(Reply -> (s.str(Reply) + "\n\nMình đã gửi bản tóm tắt cho bác sĩ để buổi khám nhanh hơn."),
      steps("summarize: tạo ConsultationSummary (SOAP) và lưu vào lịch hẹn"))

  private def medication(s: HealthState): java.util.Map[String, Object] =
    val r = ai.medication.chat(s.patientId, s.input)
    out(Reply -> r.text, steps((toolSteps(r) :+ "medication: trả lời")*))

  // ---------------------------------------------------------------- wiring

  /** Bọc NodeAction: gắn lượt chat hiện tại vào thread đang chạy node (để ghi lại lời gọi LLM, F8). */
  private def node(f: HealthState => java.util.Map[String, Object]) =
    node_async[HealthState](s => ai.tracer.inTurn(s.str(TurnId))(f(s)))

  def definition: StateGraph[HealthState] =
    StateGraph[HealthState](HealthState.channels, s => HealthState.factory(s))
      .addNode("guardrail", node(guardrail))
      .addNode("router", node(router))
      .addNode("faq", node(faq))
      .addNode("triage", triageGraph)
      .addNode("booking", node(booking))
      .addNode("confirm_booking", node(confirmBooking))
      .addNode("summarize", node(summarize))
      .addNode("medication", node(medication))
      .addNode("other", node(other))
      .addEdge(START, "guardrail")
      .addConditionalEdges("guardrail", edge_async[HealthState](s => if s.flag(Emergency) then "end" else "ok"),
        Map("end" -> END, "ok" -> "router").asJava)
      .addConditionalEdges("router", edge_async[HealthState](_.str(IntentKey)),
        Map("FAQ" -> "faq", "TRIAGE" -> "triage", "BOOKING" -> "booking", "MEDICATION" -> "medication",
          "OTHER" -> "other").asJava)
      .addConditionalEdges("booking", edge_async[HealthState](s => if s.str(PendingSlot).nonEmpty then "confirm" else "end"),
        Map("confirm" -> "confirm_booking", "end" -> END).asJava)
      .addConditionalEdges("confirm_booking",
        edge_async[HealthState](s => if s.str(AppointmentId).nonEmpty && s.str(Decision) == "confirm" then "summarize" else "end"),
        Map("summarize" -> "summarize", "end" -> END).asJava)
      .addEdge("faq", END)
      .addEdge("triage", END)
      .addEdge("summarize", END)
      .addEdge("medication", END)
      .addEdge("other", END)

  def compile(saver: BaseCheckpointSaver): CompiledGraph[HealthState] =
    definition.compile(
      CompileConfig.builder()
        .checkpointSaver(saver)
        .interruptBefore("confirm_booking") // human-in-the-loop
        .releaseThread(false)               // giữ checkpoint sau khi chạy xong để lượt sau đọc lại (mặc định là xoá)
        .build())

  def mermaid: String =
    definition.getGraph(GraphRepresentation.Type.MERMAID, "Health care supervisor", false).content()

object HealthGraph:
  private val Yes = List("co", "dong y", "ok", "oke", "u", "uh", "vang", "duoc", "dat di", "yes", "chac chan")
  /** Câu trả lời ngắn mang nghĩa đồng ý ("Có", "Ừ", "Đồng ý", "Vâng đặt đi"...). */
  def isAffirmative(text: String): Boolean =
    val t = Text.normalize(text).replaceAll("[^a-z ]", " ").trim.replaceAll("\\s+", " ")
    t.nonEmpty && t.split(" ").length <= 6 && Yes.exists(y => t == y || t.startsWith(y + " "))
