package demo.health.backend.http

/** DTO JSON của REST API (xem [[ApiRoutes]]). */
final case class ChatRequest(patientId: String, message: String)
