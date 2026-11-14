package demo.health.domain.port

import demo.health.domain.model.*

// Port ra (driven) cho các năng lực AI. Adapter `langchain` cài đặt chúng bằng LangChain4j.
// Phần điều phối (`langgraph`) chỉ biết các trait này, nên có thể test graph bằng cài đặt giả viết tay.

/** F3: agent đặt lịch. Chỉ ĐỀ XUẤT lịch, việc đặt thật do use case làm sau khi bệnh nhân xác nhận. */
trait BookingAssistant:
  def chat(patientId: String, message: String): BookingReply
