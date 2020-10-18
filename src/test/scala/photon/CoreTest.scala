package photon

import com.typesafe.scalalogging.Logger
import org.scalatest._
import org.scalatest.Matchers._
import photon.transforms.AssignmentTransform

import scala.io.Source

class CoreTest extends FunSuite {
  def parseAsBlock(
    code: String,
    fileName: String,
    macroHandler: Parser.MacroHandler = Parser.BlankMacroHandler
  ): Value = {
    val parser = new Parser(new Lexer(fileName, code), macroHandler)
    val values = parser.parseAll()

    Value.Operation(Operation.Block(values), None)
  }

  def eval(code: String): Value = {
    val interpreter = new Interpreter()
    val core = Source.fromResource("runtime/core.y").mkString
    val preludeValue = parseAsBlock(core, "core.y", interpreter.macroHandler)

    interpreter.evaluate(preludeValue)

    val value = parseAsBlock(code, "<testing>", interpreter.macroHandler)
    interpreter.evaluate(value)
  }

  def expect(actualCode: String, expectedCode: String): Unit = {
    assert(s"{ ${eval(actualCode).toString} }" == parseAsBlock(expectedCode, "<expected>").toString)
  }

  test("can expand if expressions") {
    expect("if true { 42 }", "42")
    expect("if true { $? + 12 }", "$? + 12")
  }

  test("can expand dynamic if expressions") {
    expect("if $? { 42 }", "$?.to_bool.if_else({ 42 }, {})")
  }
}
