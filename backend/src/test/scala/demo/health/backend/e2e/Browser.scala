package demo.health.backend.e2e

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import org.http4s.*

/** Một "trình duyệt": giữ cookie sid giữa các lời gọi. */
final class Browser(t: TestApp):
  private var cookies = Map.empty[String, String]

  def sid: Option[String] = cookies.get("sid")

  def send(method: Method, path: String, body: Option[String] = None): Resp =
    val base = Request[IO](method, Uri.unsafeFromString(path))
    val withBody = body.fold(base)(b => base.withEntity(b).withContentType(headers.`Content-Type`(MediaType.application.json)))
    val req = cookies.foldLeft(withBody) { case (r, (k, v)) => r.addCookie(k, v) }
    val res = t.app.run(req).unsafeRunSync()
    res.cookies.foreach(c => cookies += c.name -> c.content)
    val text = res.bodyText.compile.string.unsafeRunSync()
    t.calls.add((method.name, Uri.unsafeFromString(path).path.renderString, res.status.code))
    Resp(res.status, text, res.headers)

  def get(path: String): Resp = send(Method.GET, path)
  def post(path: String, json: String): Resp = send(Method.POST, path, Some(json))

  def chat(patientId: String, message: String): Resp =
    post("/api/chat", Json.obj("patientId" -> Json.fromString(patientId), "message" -> Json.fromString(message)).noSpaces)

  def decide(patientId: String, confirm: Boolean): Resp =
    post("/api/booking/decision", s"""{"patientId":"$patientId","confirm":$confirm}""")
