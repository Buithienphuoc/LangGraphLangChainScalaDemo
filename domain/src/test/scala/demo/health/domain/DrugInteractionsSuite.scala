package demo.health.domain

import demo.health.domain.model.*

class DrugInteractionsSuite extends munit.FunSuite:
  test("tìm thấy tương tác aspirin + ibuprofen, không phân biệt hoa thường") {
    assertEquals(DrugInteractions.check(List("Aspirin", "IBUPROFEN")).map(_.severity), List("trung bình"))
  }
  test("một thuốc thì không có tương tác") {
    assert(DrugInteractions.check(List("metformin")).isEmpty)
  }
