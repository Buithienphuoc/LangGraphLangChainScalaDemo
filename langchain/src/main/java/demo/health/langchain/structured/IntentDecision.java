package demo.health.langchain.structured;

import dev.langchain4j.model.output.structured.Description;

/**
 * Structured output: record Java thay vì case class Scala, vì LangChain4j dùng Jackson + reflection
 * để sinh JSON schema và parse kết quả. Record Java là cách ít ma sát nhất.
 */
public record IntentDecision(
        @Description("Ý định chính của tin nhắn mới nhất") Intent intent,
        @Description("Lý do ngắn gọn, tối đa 1 câu") String reason) {}
