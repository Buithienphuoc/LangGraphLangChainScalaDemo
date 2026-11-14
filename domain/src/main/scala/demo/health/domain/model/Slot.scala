package demo.health.domain.model

import java.time.LocalDateTime

final case class Slot(id: String, doctorId: String, startAt: LocalDateTime, booked: Boolean)
