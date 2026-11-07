package demo.health.backend.e2e

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import demo.health.backend.{AppConfig, Main, Storage, Wiring}
import demo.health.langchain.FakeChatModel
import io.circe.Json
import io.circe.parser.parse
import org.http4s.*
import org.http4s.implicits.*
import java.util.concurrent.ConcurrentLinkedQueue

/** Một phản hồi HTTP đã đọc xong body. */
final case class Resp(status: Status, body: String, headers: Headers):
  lazy val json: Json = parse(body).fold(e => throw AssertionError(s"Body không phải JSON ($status): $body"), identity)
  def str(path: String*): String = at(path*).asString.getOrElse(throw AssertionError(s"${path.mkString(".")} không phải chuỗi: $json"))
  def at(path: String*): Json = path.foldLeft(json)((j, k) => j.hcursor.downField(k).focus.getOrElse(Json.Null))
  def arr(path: String*): List[Json] = at(path*).asArray.map(_.toList).getOrElse(Nil)
  def strs(path: String*): List[String] = arr(path*).flatMap(_.asString)

/** Ứng dụng thật (đủ route, middleware session, graph, LangChain4j) với model giả và dữ liệu in-memory.
  * Gọi thẳng HttpApp, không qua socket, nên nhanh và không xung đột cổng.
  */
final class TestApp(mk: => Wiring = Wiring(AppConfig.fromEnv().copy(storage = Storage.Memory), Some(FakeChatModel()))):
  val wiring: Wiring  = mk
  val app: HttpApp[IO] = Main.httpApp(wiring)

  /** Mọi lời gọi (method, path) và mã trạng thái, để kiểm tra độ phủ endpoint và "không có 500". */
  val calls = ConcurrentLinkedQueue[(String, String, Int)]()

  def client(): Browser = Browser(this)

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

object TestApp:
  /** Một instance dùng chung cho toàn bộ bộ test (khởi tạo embedding + index RAG mất vài giây). */
  lazy val shared: TestApp = TestApp()
