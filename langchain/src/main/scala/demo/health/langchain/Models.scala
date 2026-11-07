package demo.health.langchain

import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel
import dev.langchain4j.model.ollama.{OllamaChatModel, OllamaEmbeddingModel}
import dev.langchain4j.model.openai.OpenAiChatModel
import java.time.Duration
import scala.jdk.CollectionConverters.*

/** Tạo ChatModel/EmbeddingModel. Mọi code phía sau chỉ thấy interface ChatModel, nên đổi provider chỉ là đổi config. */
object Models:
  def chatModel(cfg: AiConfig, listeners: List[ChatModelListener]): ChatModel = cfg.provider match
    case LlmProvider.Anthropic =>
      val key = cfg.anthropicApiKey.getOrElse(
        throw IllegalStateException("Thiếu ANTHROPIC_API_KEY. Đặt biến môi trường, dùng OPENAI_API_KEY / LLM_PROVIDER=ollama, hoặc chạy model giả: sbt \"backend/Test/runMain demo.health.backend.DevServer\""))
      AnthropicChatModel.builder()
        .apiKey(key)
        .modelName(cfg.anthropicModel)
        .temperature(0.2)
        .maxTokens(1500)
        .timeout(Duration.ofSeconds(90))
        .listeners(listeners.asJava)
        .build()
    case LlmProvider.OpenAi =>
      val key = cfg.openAiApiKey.getOrElse(
        throw IllegalStateException("Thiếu OPENAI_API_KEY. Đặt biến môi trường hoặc đổi LLM_PROVIDER."))
      val b = OpenAiChatModel.builder()
        .apiKey(key)
        .modelName(cfg.openAiModel)
        .temperature(0.2)
        .timeout(Duration.ofSeconds(90))
        .listeners(listeners.asJava)
      cfg.openAiBaseUrl.foreach(b.baseUrl) // tuỳ chọn: Azure/OpenRouter/LM Studio... (API tương thích OpenAI)
      b.build()
    case LlmProvider.Ollama =>
      OllamaChatModel.builder()
        .baseUrl(cfg.ollamaBaseUrl)
        .modelName(cfg.ollamaModel)
        .temperature(0.2)
        .timeout(Duration.ofSeconds(180))
        .listeners(listeners.asJava)
        .build()

  def embeddingModel(cfg: AiConfig): EmbeddingModel = cfg.embedding match
    case EmbeddingChoice.Local => AllMiniLmL6V2QuantizedEmbeddingModel()
    case EmbeddingChoice.Ollama(model) =>
      OllamaEmbeddingModel.builder().baseUrl(cfg.ollamaBaseUrl).modelName(model).build()
