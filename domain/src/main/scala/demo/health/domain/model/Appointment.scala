package demo.health.domain.model

import java.time.LocalDateTime

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
