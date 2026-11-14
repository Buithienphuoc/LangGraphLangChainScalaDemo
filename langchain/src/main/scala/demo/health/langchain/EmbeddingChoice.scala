package demo.health.langchain

enum EmbeddingChoice:
  /** all-MiniLM-L6-v2 (quantized) chạy in-process bằng ONNX, không cần mạng hay API key. */
  case Local
  /** Embedding qua Ollama, ví dụ "bge-m3" (đa ngôn ngữ, tốt hơn cho tiếng Việt). */
  case Ollama(model: String)
