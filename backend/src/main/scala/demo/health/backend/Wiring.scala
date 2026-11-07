package demo.health.backend

import demo.health.backend.persistence.*
import demo.health.domain.inmemory.InMemoryClinicRepository
import demo.health.domain.port.*
import demo.health.langchain.*
import demo.health.langgraph.{Checkpoints, GraphConversationService}
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore
import org.slf4j.LoggerFactory

/** Composition root: nơi DUY NHẤT biết mọi adapter và nối chúng vào các port.
  * `chatModelOverride` chỉ dùng khi chạy thử với model giả (DevServer).
  */
final class Wiring(val cfg: AppConfig, chatModelOverride: Option[ChatModel] = None):
  private val log = LoggerFactory.getLogger(getClass)

  // ---- adapter lưu trữ
  private lazy val dataSource = Db.dataSource(cfg.db)

  val repo: ClinicRepository = cfg.storage match
    case Storage.Memory => InMemoryClinicRepository()
    case Storage.Postgres =>
      val r = PostgresClinicRepository(dataSource)
      r.init()
      r

  // ---- adapter AI (LangChain4j)
  private val embeddingModel = Models.embeddingModel(cfg.ai)

  private val embeddingStore: EmbeddingStore[TextSegment] = cfg.storage match
    case Storage.Memory => InMemoryEmbeddingStore[TextSegment]()
    case Storage.Postgres =>
      PgVectorEmbeddingStore.builder()
        .host(cfg.db.host).port(cfg.db.port).database(cfg.db.database)
        .user(cfg.db.user).password(cfg.db.password)
        .table("embeddings")
        .dimension(embeddingModel.dimension())
        .dropTableFirst(true) // demo: index lại tài liệu mỗi lần khởi động
        .createTable(true)
        .build()

  private val chunks = KnowledgeBase.ingest(embeddingStore, embeddingModel)
  log.info(s"RAG: đã index $chunks chunk từ ${KnowledgeBase.loadDocs().size} tài liệu")

  private val memoryStore = cfg.storage match
    case Storage.Memory   => Memory.inMemoryStore()
    case Storage.Postgres => JdbcChatMemoryStore(dataSource)

  private val chatModel = chatModelOverride.getOrElse(Models.chatModel(cfg.ai, List(ModelCallTracer)))

  val ai: AiPorts =
    LangChainAi.build(chatModel, KnowledgeBase.retriever(embeddingStore, embeddingModel), Memory.provider(memoryStore), repo)

  // ---- điều phối (LangGraph4j)
  private val saver = cfg.storage match
    case Storage.Memory   => Checkpoints.inMemory()
    case Storage.Postgres => Checkpoints.postgres(cfg.db.host, cfg.db.port, cfg.db.database, cfg.db.user, cfg.db.password)

  val conversations: ConversationUseCase = GraphConversationService(ai, repo, saver)
