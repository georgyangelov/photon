package photon

import org.scalatest.FunSuite
import photon.TestHelpers.expectEval

class ListTest extends FunSuite {
  test("supports construction with List.of") {
    expectEval(
      """
        numbers = List.of(1, 2, 3)
        numbers.get(1)
      """,
      "2"
    )
  }

  test("supports empty lists") {
    expectEval(
      "List.of().size",
      "0"
    )

    expectEval(
      "List.empty.size",
      "0"
    )
  }

  test("supports map") {
    expectEval(
      "List.of(1, 2, 3, 4).map (x: Int) x + 1",
      "List.of(2, 3, 4, 5)"
    )
  }

  test("supports reduce") {
    expectEval(
      "List.of(1, 2, 3, 4).reduce 0, (x: Int, y: Int) x + y",
      "10"
    )

    expectEval(
      "List.empty.reduce 42, (x: Int, y: Int) x + y",
      "42"
    )

    expectEval(
      "List.of(1).reduce 41, (x: Int, y: Int) x + y",
      "42"
    )
  }
}
