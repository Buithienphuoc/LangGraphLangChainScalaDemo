package demo.health.langgraph

import demo.health.domain.model.*
import demo.health.domain.port.*
import demo.health.langgraph.HealthState.*
import org.bsc.langgraph4j.*
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

/** Cài đặt use case hội thoại (port vào của domain) bằng LangGraph4j.
  * Mỗi (phiên trình duyệt, bệnh nhân) là một threadId của checkpoint saver.
  */

final class GraphConversationService(ai: AiPorts, repo: ClinicRepository, saver: BaseCheckpointSaver)
    extends ConversationUseCase:
  private val graph    = HealthGraph(ai, repo)
  private val compiled = graph.compile(saver)

  private def threadId(sessionId: String, patientId: String) = s"$sessionId-$patientId"
  private def config(thread: String)                         = RunnableConfig.builder().threadId(thread).build()

  /** Graph đang dừng trước confirm_booking nghĩa là có lịch chờ xác nhận. */
  def pending(sessionId: String, patientId: String): Option[PendingBooking] =
    pendingOf(threadId(sessionId, patientId))

  private def pendingOf(thread: String): Option[PendingBooking] =
    compiled.lastStateOf(config(thread)).toScala
      .filter(_.nextNodeId() == "confirm_booking")
      .flatMap { snap =>
        val s = snap.state()
        Option(s.str(PendingSlot)).filter(_.nonEmpty).flatMap(repo.findSlot).map { slot =>
          val doctor = repo.findDoctor(slot.doctorId).map(d => s"${d.name} (${d.specialty})").getOrElse(slot.doctorId)
          PendingBooking(slot.id, doctor, Text.time(slot.startAt), s.str(PendingReason))
        }
      }

  def send(sessionId: String, patientId: String, message: String): TurnResult = synchronized {
    val thread = threadId(sessionId, patientId)
    val redFlags = RedFlags.detect(message)
    if pendingOf(thread).isDefined && redFlags.nonEmpty then
      // Red flag luôn được ưu tiên trước mọi luồng, kể cả khi graph đang dừng chờ xác nhận đặt lịch.
      // Không chạy lại graph (sẽ mất điểm interrupt); lịch chờ vẫn giữ nguyên.
      TurnResult(RedFlags.emergencyMessage, Nil, true, pendingOf(thread), List("guardrail"),
        List(s"guardrail: RED FLAG (${redFlags.map(_.label).mkString(", ")}) → dừng, khuyên gọi 115 (lịch chờ xác nhận được giữ)"), Nil)
    else if pendingOf(thread).isDefined then
      TurnResult("Bạn đang có một lịch hẹn chờ xác nhận. Hãy bấm Xác nhận hoặc Huỷ trước khi tiếp tục.",
        Nil, false, pendingOf(thread), Nil, Nil, Nil)
    else
      // Với releaseThread(false), mỗi lần invoke mới trên cùng threadId bắt đầu lại từ START nhưng KẾ THỪA
      // state của checkpoint cuối (activeFlow, triageNotes... nhờ vậy hội thoại nhiều lượt hoạt động).
      // Các khoá chỉ có nghĩa trong một lượt thì phải đặt lại ở đây.
      val turnId = java.util.UUID.randomUUID().toString
      val input  = (PerTurnReset ++ List(Input -> message, PatientId -> patientId, TurnId -> turnId)).toMap.asJava
      run(thread, turnId, GraphInput.args(input), config(thread))
  }

  def decide(sessionId: String, patientId: String, confirm: Boolean): TurnResult = synchronized {
    val thread = threadId(sessionId, patientId)
    if pendingOf(thread).isEmpty then TurnResult("Không có lịch nào đang chờ xác nhận.", Nil, false, None, Nil, Nil, Nil)
    else
      // Ghi quyết định của người dùng vào state đã checkpoint, rồi chạy tiếp từ điểm dừng
      val turnId = java.util.UUID.randomUUID().toString
      val cfg = compiled.updateState(config(thread),
        Map[String, Object](Decision -> (if confirm then "confirm" else "cancel"), TurnId -> turnId).asJava)
      run(thread, turnId, GraphInput.resume(), cfg)
  }

  private def run(thread: String, turnId: String, input: GraphInput, cfg: RunnableConfig): TurnResult =
    // stream() trả về output của từng node theo thứ tự, ta dùng nó để biết graph đã đi qua những node nào
    val (outputs, modelCalls) = ai.tracer.during(turnId)(compiled.stream(input, cfg).asScala.toList)
    val path = outputs.map(_.node()).filterNot(n => n == GraphDefinition.START || n == GraphDefinition.END)
    val last = outputs.lastOption.map(_.state()).getOrElse(throw IllegalStateException("graph không trả về state"))
    TurnResult(
      reply = last.str(Reply),
      sources = last.value[java.util.List[String]](Sources).map(_.asScala.toList).orElse(Nil),
      emergency = last.flag(Emergency),
      pending = pendingOf(thread),
      path = path,
      steps = last.trace,
      modelCalls = modelCalls
    )

  def diagram: String = graph.mermaid
