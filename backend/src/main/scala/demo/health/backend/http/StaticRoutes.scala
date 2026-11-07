package demo.health.backend.http

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*

/** Phục vụ các file tĩnh của module frontend (nằm trên classpath dưới thư mục `public/`). */
object StaticRoutes:
  private def resource(path: String, req: Request[IO]): IO[Response[IO]] =
    StaticFile.fromResource[IO](s"/public/$path", Some(req)).getOrElseF(NotFound())

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root            => resource("index.html", req)
    case req @ GET -> Root / "doctor" => resource("doctor.html", req)
    case req @ GET -> Root / "graph"  => resource("graph.html", req)
    case req @ GET -> Root / "assets" / file if !file.contains("..") => resource(s"assets/$file", req)
  }
