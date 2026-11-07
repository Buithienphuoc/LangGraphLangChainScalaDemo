package demo.health.backend

import demo.health.backend.persistence.DbConfig
import demo.health.langchain.*

enum Storage:
  case Memory, Postgres

final case class AppConfig(port: Int, storage: Storage, db: DbConfig, ai: AiConfig)

/** Đọc cấu hình từ biến môi trường (xem README). */
object AppConfig:
  private def env(k: String): Option[String] = sys.env.get(k).map(_.trim).filter(_.nonEmpty)

  def fromEnv(): AppConfig =
    val provider = env("LLM_PROVIDER").map(_.toLowerCase) match
      case Some("ollama") => LlmProvider.Ollama
      case Some("fake")   => LlmProvider.Fake
      case _              => LlmProvider.Anthropic
    val embedding = env("EMBEDDING").map(_.toLowerCase) match
      case Some(s) if s.startsWith("ollama:") => EmbeddingChoice.Ollama(s.stripPrefix("ollama:"))
      case _                                  => EmbeddingChoice.Local
    AppConfig(
      port = env("PORT").map(_.toInt).getOrElse(8081),
      storage = if env("STORAGE").exists(_.equalsIgnoreCase("postgres")) then Storage.Postgres else Storage.Memory,
      db = DbConfig(
        host = env("DB_HOST").getOrElse("localhost"),
        port = env("DB_PORT").map(_.toInt).getOrElse(5432),
        database = env("DB_NAME").getOrElse("healthcare"),
        user = env("DB_USER").getOrElse("healthcare"),
        password = env("DB_PASSWORD").getOrElse("healthcare")
      ),
      ai = AiConfig(
        provider = provider,
        anthropicApiKey = env("ANTHROPIC_API_KEY"),
        anthropicModel = env("ANTHROPIC_MODEL").getOrElse("claude-sonnet-5-5"),
        ollamaBaseUrl = env("OLLAMA_BASE_URL").getOrElse("http://localhost:11434"),
        ollamaModel = env("OLLAMA_MODEL").getOrElse("qwen2.5:7b"),
        embedding = embedding
      )
    )
