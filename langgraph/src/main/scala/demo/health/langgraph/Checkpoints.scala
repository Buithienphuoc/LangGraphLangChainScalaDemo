package demo.health.langgraph

import org.bsc.langgraph4j.*
import org.bsc.langgraph4j.checkpoint.{BaseCheckpointSaver, MemorySaver, PostgresSaver}
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer

/** Cài đặt use case hội thoại (port vào của domain) bằng LangGraph4j.
  * Mỗi (phiên trình duyệt, bệnh nhân) là một threadId của checkpoint saver.
  */

/** Chọn nơi lưu checkpoint của graph. */
object Checkpoints:
  def inMemory(): BaseCheckpointSaver = MemorySaver()

  def postgres(host: String, port: Int, database: String, user: String, password: String): BaseCheckpointSaver =
    PostgresSaver.builder()
      .host(host).port(port).database(database).user(user).password(password)
      .stateSerializer(ObjectStreamStateSerializer[HealthState](HealthState.factory(_)))
      .createTables(true)
      .build()
      .asInstanceOf[BaseCheckpointSaver]
