package photon

import org.scalatest.FunSuite
import photon.TestHelpers.expectEvalCompileTime

class PartialEvaluationTest extends FunSuite {
  test("evaluates some functions compile-time inside of lambdas during compile-time") {
    expectEvalCompileTime(
      """
          () {
            unknown = () { 42 }.runTimeOnly
            add = (a, b) { a + b }
            var1 = unknown()
            var2 = add(1, 10)

            add(var1, var2)
          }
      """,
      """
          () {
            unknown = () { 42 }
            add = (a, b) { a + b }
            var1 = unknown()
            var2 = 11

            add(var1, var2)
          }
      """
    )
  }
}
