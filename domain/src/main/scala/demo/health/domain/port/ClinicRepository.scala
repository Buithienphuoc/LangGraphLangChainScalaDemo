package demo.health.domain.port

import demo.health.domain.model.*

/** Port ra (driven): lưu trữ dữ liệu phòng khám. Backend cung cấp 2 adapter: in-memory và Postgres. */
trait ClinicRepository:
  def patients: List[Patient]
  def findPatient(id: String): Option[Patient]
  def addToProfile(patientId: String, field: ProfileField, value: String): Either[String, Patient]

  def doctors: List[Doctor]
  def findDoctor(id: String): Option[Doctor]
  def doctorsBySpecialty(specialty: String): List[Doctor]

  def availableSlots(doctorId: String, limit: Int = 6): List[Slot]
  def findSlot(id: String): Option[Slot]

  def book(patientId: String, slotId: String, reason: String): Either[String, Appointment]
  def cancel(patientId: String, appointmentId: String): Either[String, Appointment]
  def appointmentsOf(patientId: String): List[Appointment]
  def allAppointments: List[Appointment]
  def saveSummary(appointmentId: String, summary: ConsultationSummary): Unit

  def specialties: List[String] = doctors.map(_.specialty).distinct
