package demo.health.backend.http

import cats.effect.IO
import org.http4s.*

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
