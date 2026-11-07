package demo.health.backend.http

import cats.effect.IO
import demo.health.domain.model.*
import demo.health.domain.port.*
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.slf4j.LoggerFactory

/** Adapter vào (driving): REST API JSON. Chỉ gọi port của domain (use case + repository). */
final class ApiRoutes(conversations: ConversationUseCase, repo: ClinicRepository):
  import ApiRoutes.{*, given}
  private val log = LoggerFactory.getLogger(getClass)

  /** Mỗi trình duyệt có một session id (cookie `sid`), do SessionMiddleware gắn vào. */
  private def sid(req: Request[IO]): String = req.cookies.find(_.name == Session.Cookie).map(_.content).getOrElse("anonymous")

  private def patientOr404(id: String)(f: Patient => IO[Response[IO]]): IO[Response[IO]] =
    repo.findPatient(id).fold(NotFound(ErrorBody(s"Không tìm thấy bệnh nhân $id")))(f)

  private def fail(e: Throwable): IO[Response[IO]] =
    IO(log.error("Lỗi khi xử lý lượt chat", e)) *> {
      val root = Iterator.iterate(e)(_.getCause).takeWhile(_ != null).toList.last
      InternalServerError(ErrorBody(s"Lỗi khi gọi model: ${root.getClass.getSimpleName}: ${Option(root.getMessage).getOrElse("")}"))
    }

  /** Đọc body JSON; sai định dạng thì trả 400 thay vì 500. */
  private def body[A: Decoder](req: Request[IO])(f: A => IO[Response[IO]]): IO[Response[IO]] =
    req.attemptAs[A].value.flatMap {
      case Left(err) => BadRequest(ErrorBody(s"Body JSON không hợp lệ: ${err.message}"))
      case Right(a)  => f(a)
    }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "api" / "health" => Ok(Json.obj("status" -> "ok".asJson))

    case GET -> Root / "api" / "patients" => Ok(repo.patients)

    case GET -> Root / "api" / "patients" / id => patientOr404(id)(Ok(_))

    case req @ GET -> Root / "api" / "patients" / id / "pending" =>
      patientOr404(id)(p => IO.blocking(conversations.pending(sid(req), p.id)).flatMap(pb => Ok(PendingBody(pb))))

    case req @ POST -> Root / "api" / "chat" =>
      body[ChatRequest](req) { body =>
        if body.message.trim.isEmpty then BadRequest(ErrorBody("message không được rỗng"))
        else
          patientOr404(body.patientId) { p =>
            IO.blocking(conversations.send(sid(req), p.id, body.message.trim))
              .flatMap(r => Ok(TurnBody(r, repo.findPatient(p.id).getOrElse(p))))
              .handleErrorWith(fail)
          }
      }

    case req @ POST -> Root / "api" / "booking" / "decision" =>
      body[DecisionRequest](req) { body =>
        patientOr404(body.patientId) { p =>
          IO.blocking(conversations.decide(sid(req), p.id, body.confirm))
            .flatMap(r => Ok(TurnBody(r, repo.findPatient(p.id).getOrElse(p))))
            .handleErrorWith(fail)
        }
      }

    case GET -> Root / "api" / "appointments" =>
      Ok(repo.allAppointments.map { a =>
        AppointmentView(a.id, Text.time(a.startAt), a.startAt.toString,
          repo.findPatient(a.patientId).map(_.name).getOrElse(a.patientId),
          repo.findDoctor(a.doctorId).map(_.name).getOrElse(a.doctorId),
          repo.findDoctor(a.doctorId).map(_.specialty).getOrElse(""),
          a.reason, a.status.toString, a.summary)
      })

    case GET -> Root / "api" / "graph" => Ok(Json.obj("mermaid" -> conversations.diagram.asJson))
  }

object ApiRoutes:
  // ---- DTO và JSON codec (circe). Domain không phụ thuộc circe; codec nằm ở adapter này.
  final case class ChatRequest(patientId: String, message: String)
  final case class DecisionRequest(patientId: String, confirm: Boolean)
  final case class ErrorBody(error: String)
  final case class PendingBody(pending: Option[PendingBooking])
  final case class TurnBody(turn: TurnResult, patient: Patient)
  final case class AppointmentView(id: String, time: String, startAt: String, patient: String, doctor: String,
                                   specialty: String, reason: String, status: String, summary: Option[ConsultationSummary])

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

/** Gắn cookie session cho mọi request chưa có. */
object Session:
  val Cookie = "sid"
  def middleware(routes: HttpRoutes[IO]): HttpRoutes[IO] = cats.data.Kleisli { (req: Request[IO]) =>
    req.cookies.find(_.name == Cookie) match
      case Some(_) => routes(req)
      case None =>
        val sid  = java.util.UUID.randomUUID().toString.take(12)
        val req2 = req.addCookie(Cookie, sid)
        routes(req2).map(_.addCookie(ResponseCookie(Cookie, sid, path = Some("/"), httpOnly = true)))
  }
