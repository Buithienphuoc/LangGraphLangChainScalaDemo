package demo.health.domain.model

// Kiểu dữ liệu mà các port AI trả về. Domain định nghĩa chúng; adapter LangChain4j phải chuyển kết quả
// của LLM về đúng các kiểu này, nên phần điều phối (LangGraph4j) không biết gì về LangChain4j.

enum Intent:
  case FAQ, TRIAGE, BOOKING, MEDICATION, OTHER

final case class IntentDecision(intent: Intent, reason: String)

final case class SymptomReport(symptoms: List[String], duration: String, severity: Int, age: Int, missingInfo: List[String]):
  def isComplete: Boolean = symptoms.nonEmpty && missingInfo.isEmpty
  def describe: String =
    s"""Triệu chứng: ${symptoms.mkString(", ")}
       |Thời gian: $duration
       |Mức độ (1-10): $severity
       |Tuổi: $age
       |Còn thiếu: ${missingInfo.mkString("; ")}""".stripMargin

enum Urgency(val label: String):
  case SelfCare      extends Urgency("Có thể tự theo dõi tại nhà")
  case VisitThisWeek extends Urgency("Nên đi khám trong tuần này")
  case VisitSoon     extends Urgency("Nên đi khám sớm, trong 24 đến 48 giờ")

final case class TriageResult(urgency: Urgency, specialty: String, explanation: String, advice: List[String])

final case class ToolCall(name: String, arguments: String, result: String)

final case class AssistantReply(text: String, sources: List[String] = Nil, toolCalls: List[ToolCall] = Nil)

final case class BookingProposal(slotId: String, reason: String)

final case class BookingReply(reply: AssistantReply, proposal: Option[BookingProposal])

/** Một sự kiện hiển thị trên panel quan sát (F8). */
final case class TraceEvent(kind: String, title: String, detail: String = "")

/** Lịch hẹn đang chờ bệnh nhân xác nhận. */
final case class PendingBooking(slotId: String, doctor: String, time: String, reason: String)

/** Kết quả một lượt chat. */
final case class TurnResult(
    reply: String,
    sources: List[String],
    emergency: Boolean,
    pending: Option[PendingBooking],
    path: List[String],          // các node của graph đã chạy, theo thứ tự
    steps: List[String],         // ghi chú của từng node
    modelCalls: List[TraceEvent] // các lần gọi LLM
)
