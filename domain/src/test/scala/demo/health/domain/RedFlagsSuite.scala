package demo.health.domain

import demo.health.domain.model.*

class RedFlagsSuite extends munit.FunSuite:
  test("phát hiện đau ngực có dấu và không dấu") {
    assertEquals(RedFlags.detect("Tôi bị ĐAU NGỰC trái từ sáng").map(_.label), List("Đau ngực"))
    assertEquals(RedFlags.detect("toi bi dau nguc").map(_.label), List("Đau ngực"))
  }
  test("câu bình thường không bị gắn cờ") {
    assert(RedFlags.detect("Tôi bị sổ mũi và hắt hơi 2 ngày").isEmpty)
  }
  test("normalize xử lý chữ đ") {
    assertEquals(Text.normalize("Đột quỵ"), "dot quy")
  }
