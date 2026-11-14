package demo.health.domain.port

import demo.health.domain.model.*

// Port ra (driven) cho các năng lực AI. Adapter `langchain` cài đặt chúng bằng LangChain4j.
// Phần điều phối (`langgraph`) chỉ biết các trait này, nên có thể test graph bằng cài đặt giả viết tay.

/** F2: sàng lọc triệu chứng. */
trait SymptomTriage:
  def extract(conversation: String): SymptomReport
  def classify(specialties: List[String], report: String): TriageResult
