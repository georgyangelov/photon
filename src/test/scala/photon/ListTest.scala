package photon

import org.scalatest.FunSuite
import photon.TestHelpers._

class ListTest extends FunSuite {
  test("supports construction with List.of") {
    expectEval(
      """
        val numbers = List.of(1, 2, 3)
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

  test("evaluates unused list items") {
    expectEvalCompileTime(
      """
        List.of(
          1, 2, 3,
          (answer: Int) { answer + 1 }(41)
        )
      """,
      "List.of(1, 2, 3, 42)"
    )

    expectFailCompileTime(
      """
        val list = List.of(
          1, 2, 3,
          (s: String) { s }(42)
        )

        list.get(0)
      """,
      "???"
    )
  }

  ignore("does not support self-referencing lists") {}

  ignore("supports map") {
    expectEval(
      "List.of(1, 2, 3, 4).map (x: Int) x + 1",
      "List.of(2, 3, 4, 5)"
    )
  }

  ignore("supports reduce") {
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
