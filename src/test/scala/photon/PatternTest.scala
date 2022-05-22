package photon

import org.scalatest.FunSuite
import photon.base.{EValue, Pattern, PatternMatchResult}
import photon.core.$Int

class PatternTest extends FunSuite {
  def expectMatch(pattern: Pattern, value: EValue, expected: PatternMatchResult) = {
    val actual = pattern.matchValue(value)

    assert(actual.contains(expected))
  }

  def expectNoMatch(pattern: Pattern, value: EValue) = {
    val actual = pattern.matchValue(value)

    assert(actual.isEmpty)
  }

  test("matches simple specific values") {
    expectMatch(
      Pattern.SpecificValue($Int.Value(42, None)),
      $Int.Value(42, None),
      PatternMatch(Seq.empty)
    )
  }
}