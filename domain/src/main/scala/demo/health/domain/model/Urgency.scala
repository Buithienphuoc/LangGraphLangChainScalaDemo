package demo.health.domain.model

// Kiểu dữ liệu mà các port AI trả về. Domain định nghĩa chúng; adapter LangChain4j phải chuyển kết quả
// của LLM về đúng các kiểu này, nên phần điều phối (LangGraph4j) không biết gì về LangChain4j.

enum Urgency(val label: String):
  case SelfCare      extends Urgency("Có thể tự theo dõi tại nhà")
  case VisitThisWeek extends Urgency("Nên đi khám trong tuần này")
  case VisitSoon     extends Urgency("Nên đi khám sớm, trong 24 đến 48 giờ")
