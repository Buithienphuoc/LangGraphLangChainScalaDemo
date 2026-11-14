package demo.health.langchain

import demo.health.domain.model.*
import java.util.concurrent.ConcurrentHashMap

/** Memory id có dạng "<patientId>:<agent>", ví dụ "BN001:booking". Tool lấy patientId từ đó
  * (qua @ToolMemoryId) thay vì để LLM tự khai, nên agent không thể thao tác trên hồ sơ người khác.
  */

/** Nơi tool `proposeBooking` gửi đề xuất; adapter BookingAssistant lấy ra sau mỗi lượt. */
final class ProposalInbox:
  private val byPatient = ConcurrentHashMap[String, BookingProposal]()
  def put(patientId: String, p: BookingProposal): Unit  = byPatient.put(patientId, p)
  def take(patientId: String): Option[BookingProposal] = Option(byPatient.remove(patientId))

// Lưu ý interop: Scala 3 không ghi tên tham số vào bytecode (không có MethodParameters như javac -parameters),
// nên LangChain4j sẽ chỉ thấy arg0, arg1... Vì vậy mọi tham số tool đều khai báo @P(name = ..., value = ...).
// Tương tự, annotation Java có nhiều thuộc tính phải ghi rõ `value = ...` khi dùng trong Scala.
