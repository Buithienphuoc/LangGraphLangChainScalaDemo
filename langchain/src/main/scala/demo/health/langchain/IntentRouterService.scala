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
