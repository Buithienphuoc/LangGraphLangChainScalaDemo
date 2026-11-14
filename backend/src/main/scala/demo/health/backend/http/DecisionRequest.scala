package demo.health.backend.http

/** DTO JSON của REST API (xem [[ApiRoutes]]). */
final case class DecisionRequest(patientId: String, confirm: Boolean)
