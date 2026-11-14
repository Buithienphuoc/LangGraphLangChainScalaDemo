package demo.health.langchain

import demo.health.domain.model.*
import demo.health.domain.port.ClinicRepository
import dev.langchain4j.agent.tool.{P, Tool, ToolMemoryId}

/** Memory id có dạng "<patientId>:<agent>", ví dụ "BN001:booking". Tool lấy patientId từ đó
  * (qua @ToolMemoryId) thay vì để LLM tự khai, nên agent không thể thao tác trên hồ sơ người khác.
  */

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
