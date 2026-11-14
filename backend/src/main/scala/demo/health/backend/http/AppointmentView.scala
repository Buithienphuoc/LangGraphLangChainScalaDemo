package demo.health.backend.http

import demo.health.domain.model.*

/** DTO JSON của REST API (xem [[ApiRoutes]]). */
final case class AppointmentView(
    id: String,
    time: String,
    startAt: String,
    patient: String,
    doctor: String,
    specialty: String,
    reason: String,
    status: String,
    summary: Option[ConsultationSummary]
)
