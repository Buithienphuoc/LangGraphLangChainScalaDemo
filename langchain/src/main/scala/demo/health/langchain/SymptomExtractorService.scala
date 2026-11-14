package demo.health.langchain

import demo.health.langchain.structured as js
import dev.langchain4j.service.*

// ---------------------------------------------------------------------------------------------
// Khai báo AiService của LangChain4j bằng trait Scala. Trait biên dịch thành interface Java nên
// AiServices tạo được proxy. Kiểu trả về có cấu trúc là record Java (package `structured`), vì
// LangChain4j dùng Jackson + reflection để sinh JSON schema và parse kết quả.
// Các trait này là chi tiết bên trong adapter; bên ngoài chỉ thấy các port của domain.
// ---------------------------------------------------------------------------------------------

trait SymptomExtractorService:
  @SystemMessage(value = Array(
    "Trích xuất thông tin sàng lọc từ lời kể của bệnh nhân. Không suy đoán. Thông tin cần có: triệu chứng,",
    "thời gian kéo dài, mức độ (1-10), tuổi. Ghi vào missingInfo những mục quan trọng còn thiếu, viết dạng",
    "câu hỏi ngắn bằng tiếng Việt, tối đa 2 mục. Nếu đã có hồ sơ với năm sinh thì coi như đã biết tuổi."))
  def extract(@UserMessage conversation: String): js.SymptomReport
