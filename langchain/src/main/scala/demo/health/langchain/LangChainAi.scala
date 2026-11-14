package demo.health.langchain

import demo.health.domain.model.*
import demo.health.domain.port.*
import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.service.{AiServices, Result}
import dev.langchain4j.service.tool.{ToolArgumentsErrorHandler, ToolExecutionErrorHandler}
import scala.jdk.CollectionConverters.*

/** Adapter AI: dựng các AiService của LangChain4j và bọc chúng thành các port của domain. */
object LangChainAi:
  private val Safety =
    "Bạn là trợ lý của Phòng khám An Khang (phòng khám ảo dùng cho demo). Trả lời bằng tiếng Việt, ngắn gọn, thân thiện. " +
      "Không chẩn đoán khẳng định, không kê đơn, không nêu liều thuốc. Nếu có dấu hiệu nguy hiểm, khuyên gọi 115."

  def build(model: ChatModel, retriever: ContentRetriever, memory: ChatMemoryProvider, repo: ClinicRepository): AiPorts =
    // System message kèm hồ sơ bệnh nhân (memory dài hạn, F4), lấy theo memoryId của mỗi lần gọi
    def withProfile(base: String): Object => String = memoryId =>
      val profile = repo.findPatient(MemoryIds.patientOf(memoryId)).map(_.profileText).getOrElse("")
      s"$base\n\nHồ sơ bệnh nhân hiện tại:\n$profile"

    // ---- AiServices

    val faq = AiServices.builder(classOf[FaqService])
      .chatModel(model)
      .chatMemoryProvider(memory)
      .contentRetriever(retriever) // RAG
      .systemMessageProvider(m => withProfile(Safety +
        " Trả lời dựa trên tài liệu được cung cấp trong tin nhắn. Nếu tài liệu không có thông tin, nói rõ là không biết.")(m))
      .inputGuardrails(RedFlagInputGuardrail())
      .outputGuardrails(MedicalSafetyOutputGuardrail())
      .build()

    val inbox = ProposalInbox()
    val booking = AiServices.builder(classOf[AgentService])
      .chatModel(model)
      .chatMemoryProvider(memory)
      .tools(BookingTools(repo, inbox))
      .maxToolCallingRoundTrips(8)
      // LLM gửi sai tham số: báo lỗi cho LLM để nó gọi lại; tool ném exception: dừng, không lộ chi tiết nội bộ cho LLM
      .toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())
      .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())
      .systemMessageProvider(m => withProfile(Safety + " Bạn phụ trách đặt lịch khám. Quy trình: xác định chuyên khoa, " +
        "tìm bác sĩ (findDoctors), xem lịch trống (getAvailableSlots), hỏi bệnh nhân chọn giờ nếu chưa chọn, rồi gọi " +
        "proposeBooking. Không bao giờ nói lịch đã được đặt xong: bệnh nhân phải bấm xác nhận. Ngày hôm nay: " +
        java.time.LocalDate.now() + ".")(m))
      .inputGuardrails(RedFlagInputGuardrail())
      .build()

    val medication = AiServices.builder(classOf[AgentService])
      .chatModel(model)
      .chatMemoryProvider(memory)
      .tools(MedicationTools(repo), ProfileTools(repo))
      .toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())
      .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())
      .systemMessageProvider(m => withProfile(Safety + " Bạn phụ trách câu hỏi về thuốc. Dùng checkInteractions để " +
        "kiểm tra tương tác, dùng updatePatientProfile khi bệnh nhân báo dị ứng hoặc thuốc mới. Luôn nhắc rằng bảng " +
        "tương tác chỉ là dữ liệu mẫu và cần hỏi bác sĩ, dược sĩ.")(m))
      .inputGuardrails(RedFlagInputGuardrail())
      .outputGuardrails(MedicalSafetyOutputGuardrail())
      .build()

    val router     = AiServices.create(classOf[IntentRouterService], model)
    val extractor  = AiServices.create(classOf[SymptomExtractorService], model)
    val classifier = AiServices.create(classOf[TriageClassifierService], model)
    val summarizer = AiServices.create(classOf[SummarizerService], model)

    // ---- Bọc thành port của domain (chuyển record Java → case class Scala)

    AiPorts(
      intents = msg =>
        val d = router.classify(msg)
        IntentDecision(Intent.valueOf(d.intent().name), Option(d.reason()).getOrElse("")),
      knowledge = (patientId, question) => toReply(faq.answer(MemoryIds.of(patientId, "faq"), question)),
      triage = LangChainSymptomTriage(extractor, classifier),
      booking = (patientId, message) =>
        val reply = toReply(booking.chat(MemoryIds.of(patientId, "booking"), message))
        BookingReply(reply, inbox.take(patientId)),
      medication = (patientId, message) => toReply(medication.chat(MemoryIds.of(patientId, "medication"), message)),
      summarizer = context =>
        val s = summarizer.summarize(context)
        ConsultationSummary(s.subjective(), s.objective(), s.assessment(), s.plan()),
      tracer = ModelCallTracer
    )

  private def list[A](xs: java.util.List[A]): List[A] = Option(xs).map(_.asScala.toList).getOrElse(Nil)

  private def toReply(r: Result[String]): AssistantReply =
    val sources = list(r.sources()).flatMap(c => Option(c.textSegment().metadata().getString("title"))).distinct
    val tools = list(r.toolExecutions()).map(t => ToolCall(t.request().name(), t.request().arguments(), Option(t.result()).getOrElse("")))
    AssistantReply(Option(r.content()).getOrElse(""), sources, tools)
