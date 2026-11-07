package demo.health.domain.port

import demo.health.domain.model.*

// Port ra (driven) cho các năng lực AI. Adapter `langchain` cài đặt chúng bằng LangChain4j.
// Phần điều phối (`langgraph`) chỉ biết các trait này, nên có thể test graph bằng cài đặt giả viết tay.

/** F6: phân loại ý định của tin nhắn. */
trait IntentClassifier:
  def classify(message: String): IntentDecision

/** F1: hỏi đáp dựa trên tài liệu (RAG), có trích nguồn. */
trait KnowledgeAssistant:
  def answer(patientId: String, question: String): AssistantReply

/** F2: sàng lọc triệu chứng. */
trait SymptomTriage:
  def extract(conversation: String): SymptomReport
  def classify(specialties: List[String], report: String): TriageResult

/** F3: agent đặt lịch. Chỉ ĐỀ XUẤT lịch, việc đặt thật do use case làm sau khi bệnh nhân xác nhận. */
trait BookingAssistant:
  def chat(patientId: String, message: String): BookingReply

/** F5: agent thuốc. */
trait MedicationAssistant:
  def chat(patientId: String, message: String): AssistantReply

/** F7: tóm tắt SOAP. */
trait ConsultationSummarizer:
  def summarize(context: String): ConsultationSummary

/** F8: gom các lần gọi LLM của một lượt chat. */
trait TurnTracer:
  /** Chạy `f` như một lượt chat có id `turnId`, trả về kết quả cùng các sự kiện đã ghi. */
  def during[A](turnId: String)(f: => A): (A, List[TraceEvent])
  /** Gắn lượt `turnId` vào thread hiện tại trong lúc chạy `f` (dùng trong mỗi node của graph). */
  def inTurn[A](turnId: String)(f: => A): A

object TurnTracer:
  val noop: TurnTracer = new TurnTracer:
    def during[A](turnId: String)(f: => A): (A, List[TraceEvent]) = (f, Nil)
    def inTurn[A](turnId: String)(f: => A): A                      = f

/** Gom tất cả port AI để truyền vào phần điều phối. */
final case class AiPorts(
    intents: IntentClassifier,
    knowledge: KnowledgeAssistant,
    triage: SymptomTriage,
    booking: BookingAssistant,
    medication: MedicationAssistant,
    summarizer: ConsultationSummarizer,
    tracer: TurnTracer
)
