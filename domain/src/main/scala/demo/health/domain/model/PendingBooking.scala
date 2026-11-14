package demo.health.domain.model

// Kiểu dữ liệu mà các port AI trả về. Domain định nghĩa chúng; adapter LangChain4j phải chuyển kết quả
// của LLM về đúng các kiểu này, nên phần điều phối (LangGraph4j) không biết gì về LangChain4j.

/** Lịch hẹn đang chờ bệnh nhân xác nhận. */
final case class PendingBooking(slotId: String, doctor: String, time: String, reason: String)
