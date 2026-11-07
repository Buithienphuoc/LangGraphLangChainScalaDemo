package demo.health.backend

import cats.effect.*
import demo.health.langchain.FakeChatModel

/** Chạy website với model giả (không cần API key, dữ liệu in-memory) để thử giao diện và API:
  *   sbt "backend/Test/runMain demo.health.backend.DevServer"
  */
object DevServer extends IOApp.Simple:
  def run: IO[Unit] =
    val cfg = AppConfig.fromEnv().copy(storage = Storage.Memory)
    IO.blocking(Wiring(cfg, Some(FakeChatModel()))).flatMap(w => Main.serve(cfg.port, w))
