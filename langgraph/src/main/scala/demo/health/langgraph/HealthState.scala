package demo.health.langgraph

import org.bsc.langgraph4j.state.{AgentState, Channel, Channels}
import scala.jdk.CollectionConverters.*

/** State của graph. LangGraph4j lưu state dạng Map[String, Object]; lớp này thêm accessor có kiểu.
  * Mọi giá trị là String/Integer/List để serializer mặc định (Java serialization) và checkpoint Postgres
  * lưu được mà không cần cấu hình thêm.
  */
final class HealthState(data: java.util.Map[String, Object]) extends AgentState(data):
  import HealthState.*
  def str(key: String): String = value[String](key).orElse("")
  def int(key: String): Int    = value[Integer](key).map(_.intValue).orElse(0)
  def flag(key: String): Boolean = str(key) == "true"
  def trace: List[String] = value[java.util.List[String]](Trace).map(_.asScala.toList).orElse(Nil)

  def input: String     = str(Input)
  def patientId: String = str(PatientId)

object HealthState:
  // Khoá của state
  val Input              = "input"
  val PatientId          = "patientId"
  val IntentKey          = "intent"
  val Reply              = "reply"
  val Sources            = "sources"
  val Emergency          = "emergency"
  val ActiveFlow         = "activeFlow"         // "triage" khi đang hỏi thêm triệu chứng qua nhiều lượt
  val TriageNotes        = "triageNotes"        // lời kể tích luỹ của bệnh nhân trong luồng triage
  val TriageReport       = "triageReport"
  val AskCount           = "askCount"
  val TriageSummary      = "triageSummary"
  val SuggestedSpecialty = "suggestedSpecialty"
  val BookingOffered     = "bookingOffered"     // "true" khi lượt trước (triage) vừa hỏi "có muốn đặt lịch không?"
  val PendingSlot        = "pendingSlot"
  val PendingReason      = "pendingReason"
  val Decision           = "decision"           // "confirm" | "cancel", do người dùng bấm
  val AppointmentId      = "appointmentId"
  val Trace              = "trace"
  val TurnId             = "turnId"             // id của lượt chat, để gom các lời gọi LLM (F8)

  /** Giá trị đặt lại đầu mỗi lượt chat, để kết quả của lượt trước không lẫn vào lượt này.
    * `MARK_FOR_RESET` làm rỗng channel appender `trace`.
    */
  val PerTurnReset: List[(String, Object)] = List(
    Reply -> "", Sources -> java.util.ArrayList[String](), Emergency -> "false", IntentKey -> "",
    PendingSlot -> "", PendingReason -> "", Decision -> "", AppointmentId -> "", Trace -> AgentState.MARK_FOR_RESET
  )

  /** Channel: `trace` là appender (mỗi node nối thêm), các khoá khác mặc định ghi đè. */
  val channels: java.util.Map[String, Channel[?]] =
    Map[String, Channel[?]](Trace -> Channels.appender[String](() => java.util.ArrayList[String]())).asJava

  val factory: java.util.function.Function[java.util.Map[String, Object], HealthState] = HealthState(_)

  /** Tiện ích tạo output của node. */
  def out(kv: (String, Any)*): java.util.Map[String, Object] =
    val m = java.util.HashMap[String, Object]()
    kv.foreach {
      case (k, v: Int)     => m.put(k, Integer.valueOf(v))
      case (k, v: List[?]) => m.put(k, java.util.ArrayList[Object](v.map(_.asInstanceOf[Object]).asJava))
      case (k, v)          => m.put(k, v.asInstanceOf[Object])
    }
    m

  def steps(texts: String*): (String, Any) = Trace -> texts.toList
