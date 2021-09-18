package photon

import org.scalatest.FunSuite
import photon.TestHelpers.{expectEvalCompileTime, expectFailCompileTime}

class TypeSystemTest extends FunSuite {
  test("typechecking primitive types") {
    expectEvalCompileTime("42: Int", "42")
    expectFailCompileTime("42: String", "Incompatible types")
  }

//  test("typechecking custom types") {
//    val types = """
//      PositiveInt = Struct(
//        assignableFrom = (self, otherType) self == otherType,
//
//        # TODO: Check if number is positive
//        # TODO: Need `let` here...
//        call = (number) Struct($type = PositiveInt, number = number)
//      )
//    """
//
//    expectEvalCompileTime(s"$types; PositiveInt(42): PositiveInt", "42")
//    expectFailCompileTime(s"$types; PositiveInt(42): Int", "Incompatible types")
//    expectFailCompileTime(s"$types; 'test': PositiveInt", "Incompatible types")
//  }
}
