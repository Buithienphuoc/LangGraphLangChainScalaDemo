package demo.health.langchain

import demo.health.domain.model.*
import demo.health.domain.port.ClinicRepository
import dev.langchain4j.agent.tool.{P, Tool, ToolMemoryId}

/** Memory id có dạng "<patientId>:<agent>", ví dụ "BN001:booking". Tool lấy patientId từ đó
  * (qua @ToolMemoryId) thay vì để LLM tự khai, nên agent không thể thao tác trên hồ sơ người khác.
  */

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
