package demo.health.langgraph

import demo.health.domain.model.*
import demo.health.domain.port.*

/** Cài đặt giả của các port AI, viết tay bằng Scala thuần.
  * Nhờ Hexagonal, test graph không cần LangChain4j, không cần model giả ở mức HTTP/JSON.
  */
final class FakeAiPorts(repo: ClinicRepository):
  var bookingCalls = 0

  val ports: AiPorts = AiPorts(
    intents = msg =>
      val m = msg.toLowerCase
      val intent =
        if m.contains("đặt lịch") then Intent.BOOKING
        else if m.contains("thuốc") then Intent.MEDICATION
        else if m.contains("mở cửa") then Intent.FAQ
        else if m.contains("đau") then Intent.TRIAGE
        else Intent.OTHER
      IntentDecision(intent, "giả lập"),
    knowledge = (_, _) => AssistantReply("Phòng khám mở cửa 7:30 đến 17:00.", List("Phuoc's experiment clinic system")),
    triage = new SymptomTriage:
      def extract(conversation: String) =
        if conversation.contains("ngày") then SymptomReport(List("đau bụng"), "3 ngày", 5, 40, Nil)
        else SymptomReport(List("đau bụng"), "", 0, 40, List("Bạn bị bao lâu rồi?"))
      def classify(specialties: List[String], report: String) =
        TriageResult(Urgency.VisitThisWeek, "Tiêu hoá", "Nên khám tiêu hoá.", List("Ăn nhẹ"))
    ,
    booking = (_, _) =>
      bookingCalls += 1
      val slot = repo.availableSlots("BS04", 1).head
      BookingReply(AssistantReply("Mình đã đề xuất lịch, bạn bấm xác nhận nhé.",
        toolCalls = List(ToolCall("proposeBooking", s"""{"slotId":"${slot.id}"}""", "ok"))),
        Some(BookingProposal(slot.id, "đau bụng"))),
    medication = (_, _) => AssistantReply("Hãy hỏi dược sĩ."),
    summarizer = _ => ConsultationSummary("Đau bụng", "40 tuổi", "Gợi ý: tiêu hoá", "Khám Tiêu hoá"),
    tracer = TurnTracer.noop
  )
