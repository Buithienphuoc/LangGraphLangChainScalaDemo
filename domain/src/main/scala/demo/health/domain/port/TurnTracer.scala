package demo.health.domain.port

import demo.health.domain.model.*

// Port ra (driven) cho các năng lực AI. Adapter `langchain` cài đặt chúng bằng LangChain4j.
// Phần điều phối (`langgraph`) chỉ biết các trait này, nên có thể test graph bằng cài đặt giả viết tay.

/** F8: gom các lần gọi LLM của một lượt chat. */
trait TurnTracer:
  /** Chạy `f` như một lượt chat có id `turnId`, trả về kết quả cùng các sự kiện đã ghi. */
  def during[A](turnId: String)(f: => A): (A, List[TraceEvent])
  /** Gắn lượt `turnId` vào thread hiện tại trong lúc chạy `f` (dùng trong mỗi node của graph). */
  def inTurn[A](turnId: String)(f: => A): A
