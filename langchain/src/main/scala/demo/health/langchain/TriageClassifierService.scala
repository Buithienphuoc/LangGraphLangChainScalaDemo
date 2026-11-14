package demo.health.langchain

import demo.health.langchain.structured as js
import dev.langchain4j.service.*

// ---------------------------------------------------------------------------------------------
// Khai báo AiService của LangChain4j bằng trait Scala. Trait biên dịch thành interface Java nên
// AiServices tạo được proxy. Kiểu trả về có cấu trúc là record Java (package `structured`), vì
// LangChain4j dùng Jackson + reflection để sinh JSON schema và parse kết quả.
// Các trait này là chi tiết bên trong adapter; bên ngoài chỉ thấy các port của domain.
// ---------------------------------------------------------------------------------------------

trait TriageClassifierService:
  @SystemMessage(value = Array(
    "Bạn hỗ trợ sàng lọc ban đầu cho phòng khám. Bạn KHÔNG chẩn đoán và KHÔNG kê thuốc.",
    "Chọn mức độ ưu tiên thận trọng: nếu phân vân giữa hai mức, chọn mức khẩn hơn.",
    "Chỉ chọn chuyên khoa trong danh sách được cung cấp."))
  @UserMessage(value = Array("Chuyên khoa có sẵn: {{specialties}}\n\nThông tin sàng lọc:\n{{report}}"))
  def classify(@V("specialties") specialties: String, @V("report") report: String): js.TriageResult
