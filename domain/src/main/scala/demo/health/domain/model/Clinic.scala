package demo.health.domain.model

import java.time.LocalDateTime

final case class Patient(
    id: String,
    name: String,
    birthYear: Int,
    allergies: List[String],
    conditions: List[String],
    medications: List[String]
):
  def profileText: String =
    def show(xs: List[String]) = if xs.isEmpty then "không có" else xs.mkString(", ")
    s"""Bệnh nhân: $name (mã $id), sinh năm $birthYear
       |Dị ứng: ${show(allergies)}
       |Bệnh nền: ${show(conditions)}
       |Thuốc đang dùng: ${show(medications)}""".stripMargin

final case class Doctor(id: String, name: String, specialty: String)

final case class Slot(id: String, doctorId: String, startAt: LocalDateTime, booked: Boolean)

enum AppointmentStatus:
  case Booked, Cancelled

final case class Appointment(
    id: String,
    patientId: String,
    slotId: String,
    doctorId: String,
    startAt: LocalDateTime,
    reason: String,
    status: AppointmentStatus,
    summary: Option[ConsultationSummary]
)

/** Tóm tắt SOAP cho bác sĩ (F7). */
final case class ConsultationSummary(subjective: String, objective: String, assessment: String, plan: String)

/** Các trường hồ sơ mà agent được phép cập nhật (F4). */
enum ProfileField:
  case Allergy, Condition, Medication

object ProfileField:
  def parse(s: String): Option[ProfileField] = s.trim.toUpperCase match
    case "ALLERGY"    => Some(Allergy)
    case "CONDITION"  => Some(Condition)
    case "MEDICATION" => Some(Medication)
    case _            => None

object Text:
  /** Bỏ dấu tiếng Việt và viết thường, để so khớp từ khoá không phụ thuộc cách gõ. */
  def normalize(s: String): String =
    java.text.Normalizer.normalize(s.toLowerCase, java.text.Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "").replace('đ', 'd')

  private val timeFmt = java.time.format.DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy HH:mm", java.util.Locale.forLanguageTag("vi"))
  def time(t: LocalDateTime): String = t.format(timeFmt)
