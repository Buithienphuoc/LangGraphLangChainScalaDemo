package demo.health.backend

import cats.effect.*
import com.comcast.ip4s.*
import demo.health.backend.http.{ApiRoutes, Session, StaticRoutes}
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.slf4j.LoggerFactory
import scala.concurrent.duration.*

object Main extends IOApp.Simple:
  private val log = LoggerFactory.getLogger(getClass)

  def run: IO[Unit] =
    IO(AppConfig.fromEnv()).flatMap { cfg =>
      IO(log.info(s"Khởi động: storage=${cfg.storage}, llm=${cfg.ai.provider}, embedding=${cfg.ai.embedding}")) *>
        IO.blocking(Wiring(cfg)).flatMap(w => serve(cfg.port, w))
    }

  def httpApp(w: Wiring): HttpApp[IO] =
    import cats.syntax.semigroupk.*
    Session.middleware(ApiRoutes(w.conversations, w.repo).routes <+> StaticRoutes.routes).orNotFound

  def serve(port: Int, w: Wiring): IO[Unit] =
    EmberServerBuilder.default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromInt(port).getOrElse(port"8081"))
      .withHttpApp(httpApp(w))
      .withIdleTimeout(5.minutes)
      .build
      .evalTap(_ => IO(log.info(s"Mở trình duyệt: http://localhost:$port")))
      .useForever
