package demo.health.domain.port

import demo.health.domain.model.TraceEvent

/** TurnTracer không ghi gì, dùng khi không cần theo dõi các lần gọi LLM (ví dụ trong test). */
object NoopTurnTracer extends TurnTracer:
  def during[A](turnId: String)(f: => A): (A, List[TraceEvent]) = (f, Nil)
  def inTurn[A](turnId: String)(f: => A): A                      = f
