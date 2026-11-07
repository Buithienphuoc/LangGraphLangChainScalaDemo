package demo.health.langchain

import demo.health.domain.inmemory.InMemoryClinicRepository
import demo.health.domain.model.*
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore

/** Test adapter LangChain4j với ChatModel giả và embedding model thật (in-process). */
class LangChainAiSuite extends munit.FunSuite:
  private lazy val embedding = AllMiniLmL6V2QuantizedEmbeddingModel()

  private def fixture() =
    val repo  = InMemoryClinicRepository()
    val model = FakeChatModel()
    val store = InMemoryEmbeddingStore[TextSegment]()
    KnowledgeBase.ingest(store, embedding)
    val ports = LangChainAi.build(model, KnowledgeBase.retriever(store, embedding), Memory.provider(Memory.inMemoryStore()), repo)
    (repo, model, ports)

  test("structured output: record Java → IntentDecision của domain") {
    val (_, _, ai) = fixture()
    assertEquals(ai.intents.classify("Tôi muốn đặt lịch khám").intent, Intent.BOOKING)
  }

  test("RAG: câu trả lời có nguồn tài liệu, input guardrail không bắt nhầm nội dung tài liệu") {
    val (_, _, ai) = fixture()
    val r = ai.knowledge.answer("BN002", "Phòng khám mở cửa mấy giờ?")
    assert(r.text.contains("7:30"))
    assert(r.sources.nonEmpty)
  }

  test("tool calling: agent đặt lịch chỉ đề xuất, không đặt thật") {
    val (repo, _, ai) = fixture()
    val r = ai.booking.chat("BN001", "đặt lịch khám tiêu hoá")
    assertEquals(r.reply.toolCalls.map(_.name), List("findDoctors", "getAvailableSlots", "proposeBooking"))
    assert(r.proposal.exists(_.slotId.startsWith("BS04-")), r.proposal)
    assert(repo.allAppointments.isEmpty)
  }

  test("output guardrail: câu trả lời nêu liều thuốc bị yêu cầu viết lại") {
    val (_, model, ai) = fixture()
    val r = ai.medication.chat("BN003", "uống thêm ibuprofen được không")
    assert(!r.text.contains("mg"), r.text)
    assertEquals(model.medicationAnswers.get(), 2)
    assert(r.toolCalls.head.result.contains("aspirin + ibuprofen"), r.toolCalls) // BN003 đang dùng aspirin
  }

  test("input guardrail chặn red flag") {
    val (_, _, ai) = fixture()
    intercept[dev.langchain4j.guardrail.InputGuardrailException](ai.booking.chat("BN001", "tôi đau ngực quá"))
  }

  test("ModelCallTracer ghi lại lời gọi model trong một lượt") {
    val (_, _, ai) = fixture()
    val (_, events) = ModelCallTracer.during("turn-1")(ModelCallTracer.inTurn("turn-1")(ai.intents.classify("đặt lịch")))
    assertEquals(events.map(_.kind), List("model"))
  }
