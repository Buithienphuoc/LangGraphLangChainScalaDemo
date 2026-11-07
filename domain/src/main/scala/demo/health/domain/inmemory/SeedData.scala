package demo.health.domain.inmemory

import demo.health.domain.model.*
import java.time.{LocalDate, LocalDateTime, LocalTime}

/** Dữ liệu giả để demo. Không có người hay bác sĩ nào là thật. */
object SeedData:
  val patients: List[Patient] = List(
    Patient("BN001", "Nguyễn Văn An", 1985, List("penicillin"), List("tăng huyết áp"), List("amlodipine")),
    Patient("BN002", "Trần Thị Bình", 1992, Nil, Nil, Nil),
    Patient("BN003", "Lê Minh Châu", 1958, Nil, List("tiểu đường type 2"), List("metformin", "aspirin"))
  )

  val doctors: List[Doctor] = List(
    Doctor("BS01", "BS. Phạm Thu Hà", "Nội tổng quát"),
    Doctor("BS02", "BS. Hoàng Minh Đức", "Nội tổng quát"),
    Doctor("BS03", "BS. Vũ Thanh Tâm", "Tim mạch"),
    Doctor("BS04", "BS. Đặng Quốc Huy", "Tiêu hoá"),
    Doctor("BS05", "BS. Bùi Ngọc Lan", "Hô hấp"),
    Doctor("BS06", "BS. Ngô Hải Yến", "Da liễu"),
    Doctor("BS07", "BS. Trịnh Văn Long", "Thần kinh"),
    Doctor("BS08", "BS. Mai Anh Thư", "Nội tiết")
  )

  val specialties: List[String] = doctors.map(_.specialty).distinct

  /** Mỗi bác sĩ có lịch 7 ngày tới (trừ Chủ nhật), 4 khung giờ mỗi ngày. */
  def slots(from: LocalDate): List[Slot] =
    val hours = List(LocalTime.of(8, 0), LocalTime.of(9, 30), LocalTime.of(14, 0), LocalTime.of(15, 30))
    for
      d    <- doctors
      day  <- (1 to 7).map(from.plusDays(_)).toList if day.getDayOfWeek.getValue != 7
      time <- hours
    yield
      val start = LocalDateTime.of(day, time)
      Slot(s"${d.id}-${day}-${time.toString.replace(":", "")}", d.id, start, booked = false)
