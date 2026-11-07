package demo.health.langchain

import dev.langchain4j.data.message.{ChatMessage, ChatMessageDeserializer, ChatMessageSerializer}
import dev.langchain4j.memory.chat.{ChatMemoryProvider, MessageWindowChatMemory}
import dev.langchain4j.store.memory.chat.{ChatMemoryStore, InMemoryChatMemoryStore}
import javax.sql.DataSource
import scala.util.Using

/** Chat memory ngắn hạn (F4): mỗi (bệnh nhân, agent) có một cửa sổ 20 tin, lưu qua ChatMemoryStore. */
object Memory:
  def provider(store: ChatMemoryStore, maxMessages: Int = 20): ChatMemoryProvider =
    (memoryId: Object) =>
      MessageWindowChatMemory.builder().id(memoryId).maxMessages(maxMessages).chatMemoryStore(store).build()

  def inMemoryStore(): ChatMemoryStore = InMemoryChatMemoryStore()

/** ChatMemoryStore bền vững trên Postgres (bảng chat_memory): lưu danh sách tin dạng JSON bằng serializer
  * của LangChain4j. Bảng được tạo bởi schema.sql của backend.
  */
final class JdbcChatMemoryStore(ds: DataSource) extends ChatMemoryStore:
  private def sql[A](q: String)(f: java.sql.PreparedStatement => A): A =
    Using.resource(ds.getConnection)(c => Using.resource(c.prepareStatement(q))(f))

  override def getMessages(memoryId: Object): java.util.List[ChatMessage] =
    sql("SELECT messages_json FROM chat_memory WHERE memory_id = ?") { st =>
      st.setString(1, memoryId.toString)
      Using.resource(st.executeQuery()) { rs =>
        if rs.next() then ChatMessageDeserializer.messagesFromJson(rs.getString(1)) else java.util.ArrayList[ChatMessage]()
      }
    }

  override def updateMessages(memoryId: Object, messages: java.util.List[ChatMessage]): Unit =
    sql("""INSERT INTO chat_memory (memory_id, messages_json, updated_at) VALUES (?, ?, now())
          |ON CONFLICT (memory_id) DO UPDATE SET messages_json = EXCLUDED.messages_json, updated_at = now()""".stripMargin) { st =>
      st.setString(1, memoryId.toString)
      st.setString(2, ChatMessageSerializer.messagesToJson(messages))
      st.executeUpdate()
    }

  override def deleteMessages(memoryId: Object): Unit =
    sql("DELETE FROM chat_memory WHERE memory_id = ?") { st =>
      st.setString(1, memoryId.toString)
      st.executeUpdate()
    }
