package demo.health.langchain

import demo.health.langchain.structured as js
import dev.langchain4j.service.*

// ---------------------------------------------------------------------------------------------
// Khai báo AiService của LangChain4j bằng trait Scala. Trait biên dịch thành interface Java nên
// AiServices tạo được proxy. Kiểu trả về có cấu trúc là record Java (package `structured`), vì
// LangChain4j dùng Jackson + reflection để sinh JSON schema và parse kết quả.
// Các trait này là chi tiết bên trong adapter; bên ngoài chỉ thấy các port của domain.
// ---------------------------------------------------------------------------------------------

trait IntentRouterService:
  @SystemMessage(value = Array(
    "Bạn là bộ định tuyến của trợ lý phòng khám. Phân loại tin nhắn mới nhất của bệnh nhân vào đúng một ý định:",
    "FAQ: hỏi giờ mở cửa, giá, bảo hiểm, chuẩn bị đi khám, hoặc kiến thức sức khoẻ chung (ăn uống, bệnh thường gặp).",
    "TRIAGE: bệnh nhân mô tả triệu chứng của mình và muốn biết nên làm gì hoặc khám khoa nào.",
    "BOOKING: đặt lịch, xem lịch, huỷ lịch, hỏi lịch trống của bác sĩ.",
    "MEDICATION: hỏi về thuốc đang dùng, tương tác thuốc, hoặc báo dị ứng/thuốc để cập nhật hồ sơ.",
    "OTHER: không liên quan đến sức khoẻ hay phòng khám."))
  def classify(@UserMessage message: String): js.IntentDecision

/** Result[String] cho biết các đoạn tài liệu đã dùng (sources) và các tool đã gọi. */
trait FaqService:
  def answer(@MemoryId memoryId: String, @UserMessage question: String): Result[String]

trait SymptomExtractorService:
  @SystemMessage(value = Array(
    "Trích xuất thông tin sàng lọc từ lời kể của bệnh nhân. Không suy đoán. Thông tin cần có: triệu chứng,",
    "thời gian kéo dài, mức độ (1-10), tuổi. Ghi vào missingInfo những mục quan trọng còn thiếu, viết dạng",
    "câu hỏi ngắn bằng tiếng Việt, tối đa 2 mục. Nếu đã có hồ sơ với năm sinh thì coi như đã biết tuổi."))
  def extract(@UserMessage conversation: String): js.SymptomReport

trait TriageClassifierService:
  @SystemMessage(value = Array(
    "Bạn hỗ trợ sàng lọc ban đầu cho phòng khám. Bạn KHÔNG chẩn đoán và KHÔNG kê thuốc.",
    "Chọn mức độ ưu tiên thận trọng: nếu phân vân giữa hai mức, chọn mức khẩn hơn.",
    "Chỉ chọn chuyên khoa trong danh sách được cung cấp."))
  @UserMessage(value = Array("Chuyên khoa có sẵn: {{specialties}}\n\nThông tin sàng lọc:\n{{report}}"))
  def classify(@V("specialties") specialties: String, @V("report") report: String): js.TriageResult

trait AgentService:
  def chat(@MemoryId memoryId: String, @UserMessage message: String): Result[String]

trait SummarizerService:
  @SystemMessage(value = Array(
    "Viết tóm tắt dạng SOAP ngắn gọn bằng tiếng Việt cho bác sĩ trước buổi khám. Chỉ dùng thông tin được cung",
    "cấp, ghi 'chưa rõ' nếu thiếu. Phần Assessment phải ghi rõ đây là gợi ý của hệ thống sàng lọc tự động."))
  def summarize(@UserMessage context: String): js.ConsultationSummary
