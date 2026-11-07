package demo.health.langchain

/** Cấu hình phần AI, đọc từ biến môi trường ở module web. */
enum LlmProvider:
  case Anthropic, OpenAi, Ollama

enum EmbeddingChoice:
  /** all-MiniLM-L6-v2 (quantized) chạy in-process bằng ONNX, không cần mạng hay API key. */
  case Local
  /** Embedding qua Ollama, ví dụ "bge-m3" (đa ngôn ngữ, tốt hơn cho tiếng Việt). */
  case Ollama(model: String)

final case class AiConfig(
    provider: LlmProvider,
    anthropicApiKey: Option[String],
    anthropicModel: String,
    openAiApiKey: Option[String],
    openAiModel: String,
    openAiBaseUrl: Option[String],
    ollamaBaseUrl: String,
    ollamaModel: String,
    embedding: EmbeddingChoice
)
