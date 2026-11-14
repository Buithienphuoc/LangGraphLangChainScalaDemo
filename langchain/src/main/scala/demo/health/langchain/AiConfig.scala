package demo.health.langchain

final case class AiConfig(
    provider: LlmProvider,
    anthropicApiKey: Option[String],
    anthropicModel: String,
    ollamaBaseUrl: String,
    ollamaModel: String,
    embedding: EmbeddingChoice
)
