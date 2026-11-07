package demo.health.domain.inmemory

import demo.health.domain.model.*
import demo.health.domain.port.ClinicRepository
import java.time.LocalDate
import java.util.UUID

/** Adapter in-memory của ClinicRepository: dùng khi chạy không có Postgres và trong test của mọi module.
  * Đặt cạnh domain vì nó không phụ thuộc thư viện nào và giúp test các adapter khác dễ dàng.
  */
final class InMemoryClinicRepository(today: LocalDate = LocalDate.now()) extends ClinicRepository:
  private var patientsById: Map[String, Patient] = SeedData.patients.map(p => p.id -> p).toMap
  private var slotsById: Map[String, Slot]       = SeedData.slots(today).map(s => s.id -> s).toMap
  private var appts: Vector[Appointment]         = Vector.empty

  def patients: List[Patient]                  = synchronized(patientsById.values.toList.sortBy(_.id))
  def findPatient(id: String): Option[Patient] = synchronized(patientsById.get(id))

  def addToProfile(patientId: String, field: ProfileField, value: String): Either[String, Patient] = synchronized {
    patientsById.get(patientId).toRight(s"Không tìm thấy bệnh nhân $patientId").map { p =>
      def add(xs: List[String]) = if xs.exists(_.equalsIgnoreCase(value)) then xs else xs :+ value
      val updated = field match
        case ProfileField.Allergy    => p.copy(allergies = add(p.allergies))
        case ProfileField.Condition  => p.copy(conditions = add(p.conditions))
        case ProfileField.Medication => p.copy(medications = add(p.medications))
      patientsById += patientId -> updated
      updated
    }
  }

  def doctors: List[Doctor]                  = SeedData.doctors
  def findDoctor(id: String): Option[Doctor] = doctors.find(_.id == id)
  def doctorsBySpecialty(specialty: String): List[Doctor] =
    val s = Text.normalize(specialty)
    doctors.filter(d => Text.normalize(d.specialty).contains(s) || s.contains(Text.normalize(d.specialty)))

  def availableSlots(doctorId: String, limit: Int): List[Slot] = synchronized {
    slotsById.values.filter(s => s.doctorId == doctorId && !s.booked).toList.sortBy(_.startAt).take(limit)
  }
  def findSlot(id: String): Option[Slot] = synchronized(slotsById.get(id))

  def book(patientId: String, slotId: String, reason: String): Either[String, Appointment] = synchronized {
    for
      _    <- patientsById.get(patientId).toRight(s"Không tìm thấy bệnh nhân $patientId")
      slot <- slotsById.get(slotId).toRight(s"Không tìm thấy khung giờ $slotId")
      _    <- Either.cond(!slot.booked, (), "Khung giờ này vừa có người đặt")
    yield
      slotsById += slotId -> slot.copy(booked = true)
      val a = Appointment(UUID.randomUUID().toString.take(8), patientId, slotId, slot.doctorId, slot.startAt, reason,
        AppointmentStatus.Booked, None)
      appts :+= a
      a
  }

  def cancel(patientId: String, appointmentId: String): Either[String, Appointment] = synchronized {
    appts.find(a => a.id == appointmentId && a.patientId == patientId && a.status == AppointmentStatus.Booked)
      .toRight(s"Không tìm thấy lịch hẹn $appointmentId đang hoạt động")
      .map { a =>
        val c = a.copy(status = AppointmentStatus.Cancelled)
        appts = appts.map(x => if x.id == a.id then c else x)
        slotsById.get(a.slotId).foreach(s => slotsById += s.id -> s.copy(booked = false))
        c
      }
  }

  def appointmentsOf(patientId: String): List[Appointment] = synchronized(appts.filter(_.patientId == patientId).toList)
  def allAppointments: List[Appointment]                   = synchronized(appts.toList.sortBy(_.startAt))
  def saveSummary(appointmentId: String, summary: ConsultationSummary): Unit = synchronized {
    appts = appts.map(a => if a.id == appointmentId then a.copy(summary = Some(summary)) else a)
  }
