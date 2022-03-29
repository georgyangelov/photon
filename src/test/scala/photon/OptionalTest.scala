package photon

import org.scalatest.FunSuite
import photon.TestHelpers._

class OptionalTest extends FunSuite {
  ignore("inlining unknown values") {
    expectEval(
      """
        val unknown = { 42 }.runTimeOnly
        val maybeFn = Optional(Int).of(unknown())

        maybeFn.assert
      """,
      """
        val unknown = { 42 }

        unknown()
      """
    )
  }
}
