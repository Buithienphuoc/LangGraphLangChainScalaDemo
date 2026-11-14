package demo.health.backend.e2e

import cats.effect.IO
import demo.health.backend.{AppConfig, Main, Storage, Wiring}
import demo.health.langchain.LlmProvider
import org.http4s.*
import java.util.concurrent.ConcurrentLinkedQueue

final class TestApp(mk: => Wiring = Wiring(TestApp.fakeConfig)):
  val wiring: Wiring  = mk
  val app: HttpApp[IO] = Main.httpApp(wiring)

  /** Mọi lời gọi (method, path) và mã trạng thái, để kiểm tra độ phủ endpoint và "không có 500". */
  val calls = ConcurrentLinkedQueue[(String, String, Int)]()

  def client(): Browser = Browser(this)

object TestApp:
  /** Cấu hình như khi deploy với `LLM_PROVIDER=fake` và `STORAGE=memory`. */
  def fakeConfig: AppConfig =
    val c = AppConfig.fromEnv()
    c.copy(storage = Storage.Memory, ai = c.ai.copy(provider = LlmProvider.Fake))

  /** Một instance dùng chung cho toàn bộ bộ test (khởi tạo embedding + index RAG mất vài giây). */
  lazy val shared: TestApp = TestApp()
