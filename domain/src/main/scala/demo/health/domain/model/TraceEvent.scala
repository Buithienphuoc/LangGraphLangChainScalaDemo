package demo.health.domain.model

// Kiểu dữ liệu mà các port AI trả về. Domain định nghĩa chúng; adapter LangChain4j phải chuyển kết quả
// của LLM về đúng các kiểu này, nên phần điều phối (LangGraph4j) không biết gì về LangChain4j.

/** Một sự kiện hiển thị trên panel quan sát (F8). */
final case class TraceEvent(kind: String, title: String, detail: String = "")
