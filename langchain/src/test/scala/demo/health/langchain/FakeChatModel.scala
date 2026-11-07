package demo.health.langchain

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.data.message.*
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.output.TokenUsage
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*

/** ChatModel giả, trả lời theo luật để test adapter mà không tốn token và không cần mạng.
  * Backend cũng dùng nó trong DevServer để chạy thử giao diện khi chưa có API key.
  */
final class FakeChatModel extends ChatModel:
  val medicationAnswers = AtomicInteger(0)

  override def listeners(): java.util.List[ChatModelListener] = java.util.List.of(ModelCallTracer)

  override def doChat(request: ChatRequest): ChatResponse =
    val msgs   = request.messages().asScala.toList
    val system = msgs.collect { case m: SystemMessage => m.text() }.mkString("\n")
    val user   = msgs.collect { case m: UserMessage => m.singleText() }.lastOption.getOrElse("")
    val userText = user.split("\nYou must answer").head.toLowerCase
    val lastTool = msgs.lastOption.collect { case t: ToolExecutionResultMessage => t }

    def text(t: String) = respond(AiMessage.from(t))
    def call(name: String, args: String) =
      respond(AiMessage.from(ToolExecutionRequest.builder().id(s"call-$name").name(name).arguments(args).build()))

    if system.contains("bộ định tuyến") then
      val intent =
        if userText.contains("đặt lịch") then "BOOKING"
        else if userText.contains("thuốc") then "MEDICATION"
        else if userText.contains("mở cửa") then "FAQ"
        else if userText.contains("đau") then "TRIAGE"
        else "OTHER"
      text(s"""{"intent":"$intent","reason":"luật giả lập"}""")
    else if system.contains("Trích xuất thông tin sàng lọc") then
      if userText.contains("ngày") then
        text("""{"symptoms":["đau bụng"],"duration":"3 ngày","severity":5,"age":40,"missingInfo":[]}""")
      else text("""{"symptoms":["đau bụng"],"duration":"","severity":0,"age":40,"missingInfo":["Bạn bị bao lâu rồi?","Mức độ đau từ 1 đến 10?"]}""")
    else if system.contains("sàng lọc ban đầu") then
      text("""{"urgency":"VISIT_THIS_WEEK","specialty":"Tiêu hoá","explanation":"Đau bụng kéo dài nên được khám.","advice":["Ăn nhẹ","Uống đủ nước"]}""")
    else if system.contains("SOAP") then
      text("""{"subjective":"Đau bụng 3 ngày","objective":"40 tuổi","assessment":"Gợi ý: cần khám tiêu hoá","plan":"Khám Tiêu hoá"}""")
    else if system.contains("phụ trách đặt lịch") then
      lastTool.map(_.toolName()) match
        case None                      => call("findDoctors", """{"specialty":"Tiêu hoá"}""")
        case Some("findDoctors")       => call("getAvailableSlots", """{"doctorId":"BS04"}""")
        case Some("getAvailableSlots") =>
          val slot = lastTool.get.text().linesIterator.drop(1).next().takeWhile(_ != ':')
          call("proposeBooking", s"""{"slotId":"$slot","reason":"đau bụng"}""")
        case Some(_)                   => text("Mình đã đề xuất lịch khám, bạn vui lòng bấm xác nhận.")
    else if system.contains("phụ trách câu hỏi về thuốc") then
      lastTool match
        case None => call("checkInteractions", """{"drugs":"ibuprofen"}""")
        case Some(_) =>
          // lần đầu cố tình nêu liều để kích hoạt output guardrail (reprompt)
          if medicationAnswers.getAndIncrement() == 0 then text("Bạn uống ibuprofen 400 mg là được.")
          else text("Aspirin và ibuprofen có tương tác. Hãy hỏi bác sĩ hoặc dược sĩ về liều dùng.")
    else text("Phòng khám mở cửa từ 7:30 đến 17:00, thứ Hai đến thứ Bảy.")

  private def respond(m: AiMessage) =
    ChatResponse.builder().aiMessage(m).tokenUsage(TokenUsage(100, 20)).build()
