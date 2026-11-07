package demo.health.langchain

import demo.health.domain.model.TraceEvent
import demo.health.domain.port.TurnTracer
import dev.langchain4j.model.chat.listener.*
import scala.collection.mutable.ListBuffer

/** Cài đặt port TurnTracer bằng ChatModelListener của LangChain4j (F8).
  *
  * LangGraph4j chạy node trên thread của executor riêng, không phải thread gọi graph. Vì vậy:
  *  - use case đăng ký một bộ đệm theo `turnId` (`during`),
  *  - mỗi node gắn bộ đệm đó vào thread của nó trong lúc chạy (`inTurn`),
  *  - AiServices gọi ChatModel trên chính thread của node, nên listener tìm được bộ đệm qua ThreadLocal.
  */
object ModelCallTracer extends TurnTracer with ChatModelListener:
  private val buffers = java.util.concurrent.ConcurrentHashMap[String, ListBuffer[TraceEvent]]()
  private val current = ThreadLocal[ListBuffer[TraceEvent]]()

  def during[A](turnId: String)(f: => A): (A, List[TraceEvent]) =
    val buf = ListBuffer.empty[TraceEvent]
    buffers.put(turnId, buf)
    try
      val a = f
      (a, buf.synchronized(buf.toList))
    finally buffers.remove(turnId)

  def inTurn[A](turnId: String)(f: => A): A =
    Option(buffers.get(turnId)) match
      case None => f
      case Some(buf) =>
        current.set(buf)
        try f
        finally current.remove()

  private def add(e: TraceEvent): Unit = Option(current.get()).foreach(b => b.synchronized(b += e))

  // ---- ChatModelListener

  private val StartKey = "trace.start"

  override def onRequest(ctx: ChatModelRequestContext): Unit =
    ctx.attributes().put(StartKey, Long.box(System.nanoTime()))

  override def onResponse(ctx: ChatModelResponseContext): Unit =
    val ms = Option(ctx.attributes().get(StartKey)).map(s => (System.nanoTime() - s.asInstanceOf[Long]) / 1_000_000)
    val usage = Option(ctx.chatResponse().tokenUsage())
      .map(u => s"${u.inputTokenCount()} token vào, ${u.outputTokenCount()} token ra").getOrElse("")
    val tools = Option(ctx.chatResponse().aiMessage().toolExecutionRequests()).map(_.size).getOrElse(0)
    val what  = if tools > 0 then s"model yêu cầu gọi $tools tool" else "model trả lời"
    add(TraceEvent("model", what, (usage :: ms.map(m => s"$m ms").toList).filter(_.nonEmpty).mkString(" · ")))

  override def onError(ctx: ChatModelErrorContext): Unit =
    add(TraceEvent("error", "lỗi khi gọi model", String.valueOf(ctx.error().getMessage)))
