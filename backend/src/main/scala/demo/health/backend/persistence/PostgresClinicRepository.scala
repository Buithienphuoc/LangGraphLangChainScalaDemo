package demo.health.backend.persistence

import demo.health.domain.inmemory.SeedData
import demo.health.domain.model.*
import demo.health.domain.port.ClinicRepository
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import java.sql.{Connection, ResultSet, Timestamp}
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import scala.collection.mutable.ListBuffer
import scala.util.Using

/** Adapter Postgres của port ClinicRepository, viết bằng JDBC thuần cho dễ đọc. */
final class PostgresClinicRepository(ds: DataSource) extends ClinicRepository:
  import PostgresClinicRepository.*

  /** Tạo bảng và seed dữ liệu giả nếu DB còn trống. */
  def init(today: LocalDate = LocalDate.now()): Unit =
    Db.migrate(ds)
    Db.withConnection(ds) { c =>
      val empty = query(c, "SELECT count(*) FROM patients")(_.getInt(1)).head == 0
      if empty then
        SeedData.patients.foreach { p =>
          update(c, "INSERT INTO patients VALUES (?,?,?,?,?,?)", p.id, p.name, Int.box(p.birthYear),
            arr(c, p.allergies), arr(c, p.conditions), arr(c, p.medications))
        }
        SeedData.doctors.foreach(d => update(c, "INSERT INTO doctors VALUES (?,?,?)", d.id, d.name, d.specialty))
      // luôn bổ sung lịch cho 7 ngày tới
      SeedData.slots(today).foreach { s =>
        update(c, "INSERT INTO slots VALUES (?,?,?,false) ON CONFLICT (id) DO NOTHING", s.id, s.doctorId,
          Timestamp.valueOf(s.startAt))
      }
    }

  private def tx[A](f: Connection => A): A = Db.withConnection(ds) { c =>
    c.setAutoCommit(false)
    try
      val a = f(c)
      c.commit()
      a
    catch
      case e: Throwable =>
        c.rollback()
        throw e
  }

  def patients: List[Patient] = Db.withConnection(ds)(c => query(c, "SELECT * FROM patients ORDER BY id")(patient))
  def findPatient(id: String): Option[Patient] =
    Db.withConnection(ds)(c => query(c, "SELECT * FROM patients WHERE id = ?", id)(patient).headOption)

  def addToProfile(patientId: String, field: ProfileField, value: String): Either[String, Patient] =
    val col = field match
      case ProfileField.Allergy    => "allergies"
      case ProfileField.Condition  => "conditions"
      case ProfileField.Medication => "medications"
    Db.withConnection(ds) { c =>
      update(c, s"UPDATE patients SET $col = array_append($col, ?) WHERE id = ? AND NOT (? = ANY($col))",
        value, patientId, value)
    }
    findPatient(patientId).toRight(s"Không tìm thấy bệnh nhân $patientId")

  def doctors: List[Doctor] = Db.withConnection(ds)(c => query(c, "SELECT * FROM doctors ORDER BY id")(doctor))
  def findDoctor(id: String): Option[Doctor] = doctors.find(_.id == id)
  def doctorsBySpecialty(specialty: String): List[Doctor] =
    val s = Text.normalize(specialty)
    doctors.filter(d => Text.normalize(d.specialty).contains(s) || s.contains(Text.normalize(d.specialty)))

  def availableSlots(doctorId: String, limit: Int): List[Slot] = Db.withConnection(ds) { c =>
    query(c, "SELECT * FROM slots WHERE doctor_id = ? AND NOT is_booked AND start_at > now() ORDER BY start_at LIMIT ?",
      doctorId, Int.box(limit))(slot)
  }
  def findSlot(id: String): Option[Slot] =
    Db.withConnection(ds)(c => query(c, "SELECT * FROM slots WHERE id = ?", id)(slot).headOption)

  def book(patientId: String, slotId: String, reason: String): Either[String, Appointment] = tx { c =>
    // UPDATE có điều kiện: chỉ một người đặt được một slot
    val n = update(c, "UPDATE slots SET is_booked = true WHERE id = ? AND NOT is_booked", slotId)
    if n == 0 then Left("Khung giờ này không còn trống")
    else
      val id = UUID.randomUUID().toString.take(8)
      update(c, "INSERT INTO appointments VALUES (?,?,?,?,?,NULL)", id, patientId, slotId, reason,
        AppointmentStatus.Booked.toString)
      Right(query(c, apptSql + " WHERE a.id = ?", id)(appointment).head)
  }

  def cancel(patientId: String, appointmentId: String): Either[String, Appointment] = tx { c =>
    query(c, apptSql + " WHERE a.id = ? AND a.patient_id = ? AND a.status = 'Booked'", appointmentId, patientId)(appointment)
      .headOption.toRight(s"Không tìm thấy lịch hẹn $appointmentId đang hoạt động").map { a =>
        update(c, "UPDATE appointments SET status = 'Cancelled' WHERE id = ?", a.id)
        update(c, "UPDATE slots SET is_booked = false WHERE id = ?", a.slotId)
        a.copy(status = AppointmentStatus.Cancelled)
      }
  }

  def appointmentsOf(patientId: String): List[Appointment] =
    Db.withConnection(ds)(c => query(c, apptSql + " WHERE a.patient_id = ? ORDER BY s.start_at", patientId)(appointment))
  def allAppointments: List[Appointment] =
    Db.withConnection(ds)(c => query(c, apptSql + " ORDER BY s.start_at")(appointment))
  def saveSummary(appointmentId: String, summary: ConsultationSummary): Unit =
    Db.withConnection(ds)(c => update(c, "UPDATE appointments SET summary_json = ? WHERE id = ?", summary.asJson.noSpaces, appointmentId))

object PostgresClinicRepository:
  private val apptSql =
    "SELECT a.*, s.doctor_id, s.start_at FROM appointments a JOIN slots s ON s.id = a.slot_id"

  private def strings(rs: ResultSet, col: String): List[String] =
    rs.getArray(col).getArray.asInstanceOf[Array[String]].toList

  private def patient(rs: ResultSet) = Patient(rs.getString("id"), rs.getString("name"), rs.getInt("birth_year"),
    strings(rs, "allergies"), strings(rs, "conditions"), strings(rs, "medications"))
  private def doctor(rs: ResultSet) = Doctor(rs.getString("id"), rs.getString("name"), rs.getString("specialty"))
  private def slot(rs: ResultSet) = Slot(rs.getString("id"), rs.getString("doctor_id"),
    rs.getTimestamp("start_at").toLocalDateTime, rs.getBoolean("is_booked"))
  private def appointment(rs: ResultSet) = Appointment(rs.getString("id"), rs.getString("patient_id"),
    rs.getString("slot_id"), rs.getString("doctor_id"), rs.getTimestamp("start_at").toLocalDateTime,
    rs.getString("reason"), AppointmentStatus.valueOf(rs.getString("status")),
    Option(rs.getString("summary_json")).flatMap(j => decode[ConsultationSummary](j).toOption))

  private def arr(c: Connection, xs: List[String]) = c.createArrayOf("text", xs.toArray[AnyRef])

  private[persistence] def query[A](c: Connection, sql: String, params: AnyRef*)(read: ResultSet => A): List[A] =
    Using.resource(c.prepareStatement(sql)) { st =>
      params.zipWithIndex.foreach((p, i) => st.setObject(i + 1, p))
      Using.resource(st.executeQuery()) { rs =>
        val out = ListBuffer.empty[A]
        while rs.next() do out += read(rs)
        out.toList
      }
    }

  private[persistence] def update(c: Connection, sql: String, params: AnyRef*): Int =
    Using.resource(c.prepareStatement(sql)) { st =>
      params.zipWithIndex.foreach((p, i) => st.setObject(i + 1, p))
      st.executeUpdate()
    }
