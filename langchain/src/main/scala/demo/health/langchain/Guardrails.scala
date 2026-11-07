package demo.health.langchain

import demo.health.domain.model.RedFlags
import dev.langchain4j.data.message.{AiMessage, UserMessage}
import dev.langchain4j.guardrail.*

/** Input guardrail (F9): chặn trước khi tin nhắn tới LLM nếu có dấu hiệu khẩn cấp.
  * Graph cũng kiểm tra red flag ở node đầu tiên; guardrail này là lớp phòng thủ thứ hai cho mọi AiService.
  */
final class RedFlagInputGuardrail extends InputGuardrail:
  private val RagMarker = java.util.regex.Pattern.quote("\n\nAnswer using the following information:")

  override def validate(userMessage: UserMessage): InputGuardrailResult =
    // Guardrail chạy SAU bước RAG, nên tin nhắn đã bị nối thêm nội dung tài liệu. Chỉ xét phần người dùng viết.
    val original = userMessage.singleText().split(RagMarker).head
    val hits     = RedFlags.detect(original)
    if hits.isEmpty then success()
    else fatal(s"Phát hiện dấu hiệu khẩn cấp: ${hits.map(_.label).mkString(", ")}")

/** Output guardrail (F9): không cho câu trả lời chứa liều thuốc cụ thể.
  * `reprompt` yêu cầu LLM viết lại với chỉ dẫn bổ sung, thay vì trả lỗi cho người dùng.
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
