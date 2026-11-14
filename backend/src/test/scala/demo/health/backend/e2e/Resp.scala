package demo.health.backend.e2e

import io.circe.Json
import io.circe.parser.parse
import org.http4s.*
import org.http4s.implicits.*

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
