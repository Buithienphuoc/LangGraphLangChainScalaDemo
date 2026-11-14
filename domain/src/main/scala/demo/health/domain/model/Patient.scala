package demo.health.domain.model

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
