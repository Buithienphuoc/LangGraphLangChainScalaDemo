package demo.health.langchain

/** Cấu hình phần AI, đọc từ biến môi trường ở module web. */
enum LlmProvider:
  case Anthropic, Ollama
  /** Model tự viết, không cần API key (xem `FakeChatModel`). */
  case Fake
