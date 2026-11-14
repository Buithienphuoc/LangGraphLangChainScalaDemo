package demo.health.backend.http

import demo.health.domain.model.*
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

/** JSON codec (circe) cho DTO và model. Domain không phụ thuộc circe; codec nằm ở adapter HTTP này. */
object JsonCodecs:
  given Decoder[ChatRequest]               = deriveDecoder
  given Decoder[DecisionRequest]           = deriveDecoder
  given Encoder[ErrorBody]                 = deriveEncoder
  given Encoder[Patient]                   = deriveEncoder
  given Encoder[PendingBooking]            = deriveEncoder
  given Encoder[PendingBody]               = deriveEncoder
  given Encoder[TraceEvent]                = deriveEncoder
  given Encoder[TurnResult]                = deriveEncoder
  given Encoder[TurnBody]                  = deriveEncoder
  given Encoder[ConsultationSummary]       = deriveEncoder
  given Encoder[AppointmentView]           = deriveEncoder
