package demo.health.domain.model

// Kiểu dữ liệu mà các port AI trả về. Domain định nghĩa chúng; adapter LangChain4j phải chuyển kết quả
// của LLM về đúng các kiểu này, nên phần điều phối (LangGraph4j) không biết gì về LangChain4j.

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
