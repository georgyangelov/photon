package photon.support

import org.scalatest.Assertions._
import photon.base._
import photon.frontend.{Lexer, Parser, Unparser}
import photon.interpreter._

object TestHelpers {
  def expectCompileTime(macros: String, code: String, expected: String) = ???

  def expectCompileTime(code: String, expected: String) = {
    val interpreter = new Interpreter
    val ast = parse(code)
    val value = interpreter.evaluate(ast, EvalMode.CompileTimeOnly)

    val resultCode = value.toAST(Map.empty).toString
    val expectedCode = Unparser.unparse(parse(expected))

    assert(resultCode == expectedCode)
  }

  def expectPartial(code: String, expected: String) = {
    val interpreter = new Interpreter
    val ast = parse(code)
    val value = interpreter.evaluate(ast, EvalMode.Partial)

    val resultCode = value.toAST(Map.empty).toString
    val expectedCode = Unparser.unparse(parse(expected))

    assert(resultCode == expectedCode)
  }

  def expectPhases(code: String, compile: String, run: String) = ???

  private def parse(code: String) = {
    val lexer = new Lexer("<test>", code)
    val parser = new Parser(lexer, Parser.BlankMacroHandler)

    parser.parseRoot()
  }
}
