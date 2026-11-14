package demo.health.langchain

import dev.langchain4j.service.*

// ---------------------------------------------------------------------------------------------
// Khai báo AiService của LangChain4j bằng trait Scala. Trait biên dịch thành interface Java nên
// AiServices tạo được proxy. Kiểu trả về có cấu trúc là record Java (package `structured`), vì
// LangChain4j dùng Jackson + reflection để sinh JSON schema và parse kết quả.
// Các trait này là chi tiết bên trong adapter; bên ngoài chỉ thấy các port của domain.
// ---------------------------------------------------------------------------------------------

/** Result[String] cho biết các đoạn tài liệu đã dùng (sources) và các tool đã gọi. */
trait FaqService:
  def answer(@MemoryId memoryId: String, @UserMessage question: String): Result[String]
