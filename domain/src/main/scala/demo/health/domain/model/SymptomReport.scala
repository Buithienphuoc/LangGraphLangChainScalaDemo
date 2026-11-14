package demo.health.domain.model

// Kiểu dữ liệu mà các port AI trả về. Domain định nghĩa chúng; adapter LangChain4j phải chuyển kết quả
// của LLM về đúng các kiểu này, nên phần điều phối (LangGraph4j) không biết gì về LangChain4j.

final case class SymptomReport(symptoms: List[String], duration: String, severity: Int, age: Int, missingInfo: List[String]):
  def isComplete: Boolean = symptoms.nonEmpty && missingInfo.isEmpty
  def describe: String =
    s"""Triệu chứng: ${symptoms.mkString(", ")}
       |Thời gian: $duration
       |Mức độ (1-10): $severity
       |Tuổi: $age
       |Còn thiếu: ${missingInfo.mkString("; ")}""".stripMargin
