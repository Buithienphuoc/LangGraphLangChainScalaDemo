package demo.health.langgraph

import demo.health.domain.model.*
import demo.health.domain.port.SymptomTriage

/** SymptomTriage giả cho test graph: hỏi thêm thời gian nếu hội thoại chưa nhắc "ngày". */
object FakeSymptomTriage extends SymptomTriage:
  def extract(conversation: String): SymptomReport =
    if conversation.contains("ngày") then SymptomReport(List("đau bụng"), "3 ngày", 5, 40, Nil)
    else SymptomReport(List("đau bụng"), "", 0, 40, List("Bạn bị bao lâu rồi?"))

  def classify(specialties: List[String], report: String): TriageResult =
    TriageResult(Urgency.VisitThisWeek, "Tiêu hoá", "Nên khám tiêu hoá.", List("Ăn nhẹ"))
