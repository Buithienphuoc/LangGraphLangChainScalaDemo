package demo.health.langchain

import demo.health.langchain.structured as js
import dev.langchain4j.service.*

// ---------------------------------------------------------------------------------------------
// Khai báo AiService của LangChain4j bằng trait Scala. Trait biên dịch thành interface Java nên
// AiServices tạo được proxy. Kiểu trả về có cấu trúc là record Java (package `structured`), vì
// LangChain4j dùng Jackson + reflection để sinh JSON schema và parse kết quả.
// Các trait này là chi tiết bên trong adapter; bên ngoài chỉ thấy các port của domain.
// ---------------------------------------------------------------------------------------------

trait SummarizerService:
  @SystemMessage(value = Array(
    "Viết tóm tắt dạng SOAP ngắn gọn bằng tiếng Việt cho bác sĩ trước buổi khám. Chỉ dùng thông tin được cung",
    "cấp, ghi 'chưa rõ' nếu thiếu. Phần Assessment phải ghi rõ đây là gợi ý của hệ thống sàng lọc tự động."))
  def summarize(@UserMessage context: String): js.ConsultationSummary
