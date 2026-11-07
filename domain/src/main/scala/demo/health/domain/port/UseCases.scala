package demo.health.domain.port

import demo.health.domain.model.*

/** Port vào (driving): use case hội thoại. Adapter HTTP của backend gọi vào đây;
  * module `langgraph` cài đặt nó bằng LangGraph4j.
  */
trait ConversationUseCase:
  def send(sessionId: String, patientId: String, message: String): TurnResult
  def decide(sessionId: String, patientId: String, confirm: Boolean): TurnResult
  def pending(sessionId: String, patientId: String): Option[PendingBooking]
  /** Sơ đồ quy trình (Mermaid), để hiển thị trên giao diện. */
  def diagram: String
