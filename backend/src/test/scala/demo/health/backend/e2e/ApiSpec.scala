package demo.health.backend.e2e

import org.http4s.{Method, Status}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.*

/** Test tích hợp REST API (API-01 … API-99 trong test specification). */
class ApiSpec extends munit.FunSuite:
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private lazy val t = TestApp()
  private def fresh() = t.client()

  test("API-01 GET /api/health trả 200 {status: ok}") {
    val r = fresh().get("/api/health")
    assertEquals(r.status, Status.Ok)
    assertEquals(r.str("status"), "ok")
  }

  test("API-02 GET /api/patients trả 3 bệnh nhân seed, đủ trường hồ sơ") {
    val r = fresh().get("/api/patients")
    assertEquals(r.status, Status.Ok)
    val ps = r.json.asArray.get
    assertEquals(ps.flatMap(_.hcursor.get[String]("id").toOption).toList, List("BN001", "BN002", "BN003"))
    ps.foreach { p =>
      List("id", "name", "birthYear", "allergies", "conditions", "medications").foreach(k =>
        assert(p.hcursor.downField(k).succeeded, s"thiếu $k trong $p"))
    }
  }

  test("API-03 GET /api/patients/{id} đúng id trả hồ sơ") {
    val r = fresh().get("/api/patients/BN003")
    assertEquals(r.status, Status.Ok)
    assertEquals(r.str("name"), "Lê Minh Châu")
    assert(r.strs("medications").contains("aspirin"))
  }

  test("API-04 GET /api/patients/{id} sai id trả 404 JSON lỗi") {
    val r = fresh().get("/api/patients/BN999")
    assertEquals(r.status, Status.NotFound)
    assert(r.str("error").contains("BN999"))
  }

  test("API-05 GET /api/patients/{id}/pending khi chưa có gì trả {pending: null}") {
    val r = fresh().get("/api/patients/BN002/pending")
    assertEquals(r.status, Status.Ok)
    assert(r.at("pending").isNull, r.body)
  }

  test("API-06 GET /api/patients/{id}/pending sai id trả 404") {
    assertEquals(fresh().get("/api/patients/XX/pending").status, Status.NotFound)
  }

  test("API-07 POST /api/chat hợp lệ trả turn + patient, gắn cookie sid") {
    val b = fresh()
    val r = b.chat("BN002", "Phòng khám mở cửa mấy giờ?")
    assertEquals(r.status, Status.Ok, r.body)
    List("reply", "sources", "emergency", "pending", "path", "steps", "modelCalls").foreach(k =>
      assert(r.json.hcursor.downField("turn").downField(k).succeeded, s"turn thiếu $k"))
    assertEquals(r.str("patient", "id"), "BN002")
    assert(b.sid.isDefined, "server phải gắn cookie sid")
  }

  test("API-08 POST /api/chat body sai: JSON hỏng, thiếu trường, message rỗng → 400") {
    val b = fresh()
    List("{oops", "{}", """{"patientId":"BN001"}""", """{"patientId":"BN001","message":"   "}""", "").foreach { body =>
      val r = b.post("/api/chat", body)
      assertEquals(r.status, Status.BadRequest, s"body=$body → ${r.body}")
      assert(r.json.hcursor.downField("error").succeeded)
    }
  }

  test("API-09 POST /api/chat sai bệnh nhân → 404") {
    assertEquals(fresh().chat("BN999", "xin chào").status, Status.NotFound)
  }

  test("API-10 POST /api/booking/decision khi thread đang chờ: xác nhận tạo appointment") {
    val b = fresh()
    val before = b.get("/api/appointments").json.asArray.get.size
    val r1 = b.chat("BN001", "Tôi muốn đặt lịch khám tiêu hoá")
    assertEquals(r1.status, Status.Ok, r1.body)
    assert(!r1.at("turn", "pending").isNull, r1.body)
    val p = b.get("/api/patients/BN001/pending")
    assert(!p.at("pending").isNull, "pending phải đọc lại được qua API")
    val r2 = b.decide("BN001", confirm = true)
    assertEquals(r2.status, Status.Ok, r2.body)
    assert(r2.str("turn", "reply").contains("Đã đặt lịch"), r2.body)
    assert(b.get("/api/patients/BN001/pending").at("pending").isNull)
    assertEquals(b.get("/api/appointments").json.asArray.get.size, before + 1)
  }

  test("API-11 POST /api/booking/decision khi không có gì chờ: không tạo appointment") {
    val b = fresh()
    val before = b.get("/api/appointments").json.asArray.get.size
    val r = b.decide("BN002", confirm = true)
    assertEquals(r.status, Status.Ok)
    assert(r.str("turn", "reply").contains("Không có lịch"), r.body)
    assertEquals(b.get("/api/appointments").json.asArray.get.size, before)
  }

  test("API-12 POST /api/booking/decision huỷ: không tạo appointment, hết pending") {
    val b = fresh()
    val before = b.get("/api/appointments").json.asArray.get.size
    b.chat("BN003", "đặt lịch khám")
    val r = b.decide("BN003", confirm = false)
    assertEquals(r.status, Status.Ok)
    assert(r.str("turn", "reply").toLowerCase.contains("huỷ"), r.body)
    assertEquals(b.get("/api/appointments").json.asArray.get.size, before)
    assert(b.get("/api/patients/BN003/pending").at("pending").isNull)
  }

  test("API-13 POST /api/booking/decision body sai → 400, sai bệnh nhân → 404") {
    val b = fresh()
    assertEquals(b.post("/api/booking/decision", """{"patientId":"BN001"}""").status, Status.BadRequest)
    assertEquals(b.post("/api/booking/decision", """{"patientId":"BN001","confirm":"yes"}""").status, Status.BadRequest)
    assertEquals(b.decide("NOPE", confirm = true).status, Status.NotFound)
  }

  test("API-14 GET /api/appointments có lịch vừa đặt kèm tóm tắt SOAP") {
    val b = fresh()
    b.chat("BN002", "đặt lịch khám")
    b.decide("BN002", confirm = true)
    val r = b.get("/api/appointments")
    assertEquals(r.status, Status.Ok)
    val mine = r.json.asArray.get.filter(_.hcursor.get[String]("patient").contains("Trần Thị Bình"))
    assert(mine.nonEmpty, r.body)
    val a = mine.last.hcursor
    List("id", "time", "startAt", "doctor", "specialty", "reason", "status").foreach(k =>
      assert(a.get[String](k).isRight, s"thiếu $k"))
    List("subjective", "objective", "assessment", "plan").foreach(k =>
      assert(a.downField("summary").get[String](k).exists(_.nonEmpty), s"SOAP thiếu $k: ${mine.last}"))
  }

  test("API-15 GET /api/graph trả Mermaid có đủ node") {
    val r = fresh().get("/api/graph")
    assertEquals(r.status, Status.Ok)
    val m = r.str("mermaid")
    List("guardrail", "router", "faq", "triage", "booking", "confirm_booking", "summarize", "medication").foreach(n =>
      assert(m.contains(n), s"Mermaid thiếu node $n"))
  }

  test("API-16 Trang tĩnh /, /doctor, /graph và assets trả 200, có banner an toàn") {
    val b = fresh()
    List("/", "/doctor", "/graph", "/assets/chat.js", "/assets/common.js").foreach { p =>
      val r = b.get(p)
      assertEquals(r.status, Status.Ok, p)
    }
    assert(b.get("/").body.contains("chat-input"))
    assert(b.get("/assets/common.js").body.contains("Không thay thế bác sĩ"))
  }

  test("API-17 Đường dẫn sai, method sai, path traversal → 404/405, không 500") {
    val b = fresh()
    List("/api/nope", "/api/patients/BN001/xyz", "/assets/..%2Fbuild.sbt", "/assets/nope.js").foreach { p =>
      val r = b.get(p)
      assert(Set(404, 405).contains(r.status.code), s"$p → ${r.status}")
    }
    assert(Set(404, 405).contains(b.send(Method.DELETE, "/api/patients/BN001").status.code))
    assert(Set(404, 405).contains(b.send(Method.GET, "/api/chat").status.code))
  }

  test("API-18 Session: hai trình duyệt khác nhau không thấy pending của nhau") {
    val a = fresh(); val other = fresh()
    a.chat("BN001", "đặt lịch")
    assert(!a.get("/api/patients/BN001/pending").at("pending").isNull)
    assert(other.get("/api/patients/BN001/pending").at("pending").isNull, "pending bị lộ sang session khác")
    a.decide("BN001", confirm = false)
  }

  // ---- API-99: mọi route khai báo trong backend phải được gọi ít nhất một lần, và không có 500 nào
  private val routeRx = """case (?:req @ )?(GET|POST|PUT|DELETE|PATCH) -> Root((?: / (?:"[^"]*"|\w+))*)""".r

  private def declaredRoutes: List[(String, String)] =
    List("ApiRoutes.scala", "StaticRoutes.scala").flatMap { f =>
      // test có thể chạy từ thư mục gốc hoặc (khi fork) từ thư mục module backend
      val rel = "src/main/scala/demo/health/backend/http/" + f
      val file = List(Paths.get(rel), Paths.get("backend", rel)).find(Files.exists(_)).getOrElse(Paths.get(rel))
      val src = Files.readString(file)
      routeRx.findAllMatchIn(src).map { m =>
        val segs = m.group(2).split(" / ").map(_.trim).filter(_.nonEmpty).map(s =>
          if s.startsWith("\"") then s.stripPrefix("\"").stripSuffix("\"") else "{" + s + "}")
        m.group(1) -> ("/" + segs.mkString("/"))
      }.toList
    }

  private def matches(template: String, path: String): Boolean =
    val ts = template.split("/").filter(_.nonEmpty); val ps = path.split("/").filter(_.nonEmpty)
    ts.length == ps.length && ts.zip(ps).forall((tt, pp) => tt.startsWith("{") || tt == pp)

  test("API-99 Độ phủ: mọi route khai báo đã được gọi thành công, không có phản hồi 500") {
    val declared = declaredRoutes
    assert(declared.size >= 12, s"chỉ đọc được ${declared.size} route: $declared")
    val calls = t.calls.asScala.toList
    val uncovered = declared.filterNot((m, tpl) => calls.exists((cm, p, code) => cm == m && matches(tpl, p) && code < 400))
    assert(uncovered.isEmpty, s"Route chưa được gọi thành công: $uncovered")
    val errors = calls.filter(_._3 >= 500)
    assert(errors.isEmpty, s"Có phản hồi 5xx: $errors")
    println(s"[API-99] ${declared.size} route khai báo, ${calls.size} lời gọi, tất cả đã phủ")
  }
