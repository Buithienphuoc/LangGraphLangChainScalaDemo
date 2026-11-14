package demo.health.domain.model

// Kiểu dữ liệu mà các port AI trả về. Domain định nghĩa chúng; adapter LangChain4j phải chuyển kết quả
// của LLM về đúng các kiểu này, nên phần điều phối (LangGraph4j) không biết gì về LangChain4j.

final case class TriageResult(urgency: Urgency, specialty: String, explanation: String, advice: List[String])
