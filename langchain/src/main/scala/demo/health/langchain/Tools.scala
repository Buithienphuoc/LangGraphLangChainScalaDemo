package demo.health.langchain

import demo.health.domain.model.*
import demo.health.domain.port.ClinicRepository
import dev.langchain4j.agent.tool.{P, Tool, ToolMemoryId}
import java.util.concurrent.ConcurrentHashMap

/** Memory id có dạng "<patientId>:<agent>", ví dụ "BN001:booking". Tool lấy patientId từ đó
  * (qua @ToolMemoryId) thay vì để LLM tự khai, nên agent không thể thao tác trên hồ sơ người khác.
  */
object MemoryIds:
  def of(patientId: String, agent: String): String = s"$patientId:$agent"
  def patientOf(memoryId: Any): String           = String.valueOf(memoryId).takeWhile(_ != ':')

/** Nơi tool `proposeBooking` gửi đề xuất; adapter BookingAssistant lấy ra sau mỗi lượt. */
final class ProposalInbox:
  private val byPatient = ConcurrentHashMap[String, BookingProposal]()
  def put(patientId: String, p: BookingProposal): Unit  = byPatient.put(patientId, p)
  def take(patientId: String): Option[BookingProposal] = Option(byPatient.remove(patientId))

// Lưu ý interop: Scala 3 không ghi tên tham số vào bytecode (không có MethodParameters như javac -parameters),
// nên LangChain4j sẽ chỉ thấy arg0, arg1... Vì vậy mọi tham số tool đều khai báo @P(name = ..., value = ...).
// Tương tự, annotation Java có nhiều thuộc tính phải ghi rõ `value = ...` khi dùng trong Scala.

final class BookingTools(repo: ClinicRepository, inbox: ProposalInbox):

  @Tool(value = Array("Liệt kê các chuyên khoa của phòng khám"))
  def listSpecialties(): String = repo.specialties.mkString(", ")

  @Tool(value = Array("Tìm bác sĩ theo chuyên khoa. Trả về mã bác sĩ (dạng BSxx), tên và chuyên khoa."))
  def findDoctors(@P(name = "specialty", value = "Tên chuyên khoa, ví dụ: Tim mạch, Tiêu hoá") specialty: String): String =
    repo.doctorsBySpecialty(specialty) match
      case Nil => s"Không có bác sĩ chuyên khoa '$specialty'. Các chuyên khoa hiện có: ${listSpecialties()}"
      case ds  => ds.map(d => s"${d.id}: ${d.name} (${d.specialty})").mkString("\n")

  @Tool(value = Array("Xem các khung giờ còn trống của một bác sĩ. Trả về mã khung giờ (slotId) và thời gian."))
  def getAvailableSlots(@P(name = "doctorId", value = "Mã bác sĩ, ví dụ BS03") doctorId: String): String =
    repo.findDoctor(doctorId) match
      case None => s"Không tìm thấy bác sĩ $doctorId"
      case Some(d) =>
        val slots = repo.availableSlots(doctorId, 6)
        if slots.isEmpty then s"${d.name} không còn lịch trống trong 7 ngày tới."
        else slots.map(s => s"${s.id}: ${Text.time(s.startAt)}").mkString(s"Lịch trống của ${d.name}:\n", "\n", "")

  @Tool(value = Array(
    "Đề xuất một lịch hẹn để bệnh nhân xác nhận. Tool này CHƯA đặt lịch: hệ thống sẽ hiện thẻ xác nhận cho",
    "bệnh nhân. Sau khi gọi tool, hãy nói với bệnh nhân rằng lịch đang chờ họ xác nhận."))
  def proposeBooking(
      @ToolMemoryId memoryId: String,
      @P(name = "slotId", value = "Mã khung giờ (slotId) lấy từ getAvailableSlots") slotId: String,
      @P(name = "reason", value = "Lý do khám, tóm tắt ngắn gọn bằng lời của bệnh nhân") reason: String
  ): String =
    repo.findSlot(slotId) match
      case None                => s"Không tìm thấy khung giờ $slotId. Hãy gọi getAvailableSlots để lấy mã đúng."
      case Some(s) if s.booked => "Khung giờ này đã có người đặt, hãy chọn khung giờ khác."
      case Some(s) =>
        inbox.put(MemoryIds.patientOf(memoryId), BookingProposal(slotId, reason))
        val doctor = repo.findDoctor(s.doctorId).map(_.name).getOrElse(s.doctorId)
        s"Đã gửi đề xuất: $doctor lúc ${Text.time(s.startAt)}. Đang chờ bệnh nhân bấm xác nhận."

  @Tool(value = Array("Liệt kê các lịch hẹn của bệnh nhân hiện tại"))
  def listMyAppointments(@ToolMemoryId memoryId: String): String =
    repo.appointmentsOf(MemoryIds.patientOf(memoryId)) match
      case Nil => "Bệnh nhân chưa có lịch hẹn nào."
      case xs =>
        xs.map { a =>
          val d = repo.findDoctor(a.doctorId).map(_.name).getOrElse(a.doctorId)
          s"${a.id}: $d, ${Text.time(a.startAt)}, trạng thái ${a.status}"
        }.mkString("\n")

  @Tool(value = Array("Huỷ một lịch hẹn của bệnh nhân hiện tại"))
  def cancelAppointment(
      @ToolMemoryId memoryId: String,
      @P(name = "appointmentId", value = "Mã lịch hẹn") appointmentId: String
  ): String =
    repo.cancel(MemoryIds.patientOf(memoryId), appointmentId).fold(identity, a => s"Đã huỷ lịch hẹn ${a.id}.")

final class ProfileTools(repo: ClinicRepository):

  @Tool(value = Array("Xem hồ sơ của bệnh nhân hiện tại: dị ứng, bệnh nền, thuốc đang dùng"))
  def getPatientProfile(@ToolMemoryId memoryId: String): String =
    repo.findPatient(MemoryIds.patientOf(memoryId)).map(_.profileText).getOrElse("Không tìm thấy hồ sơ.")

  @Tool(value = Array(
    "Ghi thêm thông tin vào hồ sơ bệnh nhân khi họ nói rõ về dị ứng, bệnh nền hoặc thuốc đang dùng.",
    "Chỉ ghi điều bệnh nhân tự nói, không suy đoán."))
  def updatePatientProfile(
      @ToolMemoryId memoryId: String,
      @P(name = "field", value = "Một trong: ALLERGY, CONDITION, MEDICATION") field: String,
      @P(name = "value", value = "Giá trị cần thêm, ví dụ: penicillin") value: String
  ): String =
    ProfileField.parse(field) match
      case None => "field phải là ALLERGY, CONDITION hoặc MEDICATION"
      case Some(f) =>
        repo.addToProfile(MemoryIds.patientOf(memoryId), f, value.trim).fold(identity, _ => s"Đã cập nhật hồ sơ: $value")

final class MedicationTools(repo: ClinicRepository):

  @Tool(value = Array(
    "Kiểm tra tương tác giữa các thuốc (bảng dữ liệu mẫu). Tự động gộp thêm thuốc bệnh nhân đang dùng trong hồ sơ."))
  def checkInteractions(
      @ToolMemoryId memoryId: String,
      @P(name = "drugs", value = "Tên các thuốc cần kiểm tra, phân tách bằng dấu phẩy") drugs: String
  ): String =
    val current = repo.findPatient(MemoryIds.patientOf(memoryId)).map(_.medications).getOrElse(Nil)
    val all     = (drugs.split(",").map(_.trim).toList ++ current).filter(_.nonEmpty).distinct
    DrugInteractions.check(all) match
      case Nil => s"Không thấy tương tác nào trong bảng mẫu cho: ${all.mkString(", ")}. Bảng mẫu không đầy đủ."
      case xs  => xs.map(i => s"${i.a} + ${i.b} (mức ${i.severity}): ${i.note}").mkString("\n")
