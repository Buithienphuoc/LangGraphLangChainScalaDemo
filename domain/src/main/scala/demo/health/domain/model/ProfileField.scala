package demo.health.domain.model

/** Các trường hồ sơ mà agent được phép cập nhật (F4). */
enum ProfileField:
  case Allergy, Condition, Medication

object ProfileField:
  def parse(s: String): Option[ProfileField] = s.trim.toUpperCase match
    case "ALLERGY"    => Some(Allergy)
    case "CONDITION"  => Some(Condition)
    case "MEDICATION" => Some(Medication)
    case _            => None
