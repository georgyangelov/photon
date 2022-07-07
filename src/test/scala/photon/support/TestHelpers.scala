package photon.support

import org.scalatest.Assertions._

import photon.base.{EValue, EvalMode}
import photon.frontend.{Lexer, Parser, Unparser}

object TestHelpers {
  def expectCompileTime(macros: String, code: String, expected: String) = ???

  def expectCompileTime(code: String, expected: String) = {
    val interpreter = new Interpreter
    val ast = parse(code)
    val value = interpreter.withContext(EvalMode.CompileTimeOnly) {
      interpreter.toEValueInRootScope(ast).evaluated
    }

    val resultCode = value.toUValue(interpreter.core).toString
    val expectedCode = Unparser.unparse(parse(expected))

    assert(resultCode == expectedCode)
  }

  def expectPhases(code: String, compile: String, run: String) = ???
  def toEValue(code: String): EValue = ???

  private def parse(code: String) = {
    val lexer = new Lexer("<test>", code)
    val parser = new Parser(lexer, Parser.BlankMacroHandler)

    parser.parseRoot()
  }
}
