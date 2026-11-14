package demo.health.langchain


/** Memory id có dạng "<patientId>:<agent>", ví dụ "BN001:booking". Tool lấy patientId từ đó
  * (qua @ToolMemoryId) thay vì để LLM tự khai, nên agent không thể thao tác trên hồ sơ người khác.
  */

object MemoryIds:
  def of(patientId: String, agent: String): String = s"$patientId:$agent"
  def patientOf(memoryId: Any): String           = String.valueOf(memoryId).takeWhile(_ != ':')
