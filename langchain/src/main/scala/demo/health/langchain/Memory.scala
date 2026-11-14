package demo.health.langchain

import dev.langchain4j.memory.chat.{ChatMemoryProvider, MessageWindowChatMemory}
import dev.langchain4j.store.memory.chat.{ChatMemoryStore, InMemoryChatMemoryStore}

/** Chat memory ngắn hạn (F4): mỗi (bệnh nhân, agent) có một cửa sổ 20 tin, lưu qua ChatMemoryStore. */
object Memory:
  def provider(store: ChatMemoryStore, maxMessages: Int = 20): ChatMemoryProvider =
    (memoryId: Object) =>
      MessageWindowChatMemory.builder().id(memoryId).maxMessages(maxMessages).chatMemoryStore(store).build()

  def inMemoryStore(): ChatMemoryStore = InMemoryChatMemoryStore()

/** ChatMemoryStore bền vững trên Postgres (bảng chat_memory): lưu danh sách tin dạng JSON bằng serializer
  * của LangChain4j. Bảng được tạo bởi schema.sql của backend.
  */
