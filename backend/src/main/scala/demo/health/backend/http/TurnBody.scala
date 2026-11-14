package demo.health.backend.http

import demo.health.domain.model.*

/** DTO JSON của REST API (xem [[ApiRoutes]]). */
final case class TurnBody(turn: TurnResult, patient: Patient)
