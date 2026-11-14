package demo.health.langchain

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.guardrail.*

/** Input guardrail (F9): chặn trước khi tin nhắn tới LLM nếu có dấu hiệu khẩn cấp.
  * Graph cũng kiểm tra red flag ở node đầu tiên; guardrail này là lớp phòng thủ thứ hai cho mọi AiService.
  */

final class MedicalSafetyOutputGuardrail extends OutputGuardrail:
  import MedicalSafetyOutputGuardrail.*

  override def validate(aiMessage: AiMessage): OutputGuardrailResult =
    val text = Option(aiMessage.text()).getOrElse("")
    if Dosage.findFirstIn(text).isDefined then
      reprompt("Câu trả lời chứa liều thuốc cụ thể",
        "Không được nêu liều lượng thuốc cụ thể (mg, ml, số viên mỗi ngày). Hãy viết lại và khuyên bệnh nhân hỏi bác sĩ hoặc dược sĩ về liều dùng.")
    else success()

object MedicalSafetyOutputGuardrail:
  val Dosage = "(?iu)\\b\\d+([.,]\\d+)?\\s?(mg|mcg|µg|ml|iu)\\b|\\b\\d+\\s?viên\\s?(mỗi|/)\\s?(ngày|lần)".r
