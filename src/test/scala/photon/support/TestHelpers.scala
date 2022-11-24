package photon.support

import org.scalatest.Assertions._
import photon.base._
import photon.frontend.{Lexer, ParseError, Parser, Unparser}
import photon.interpreter._

object TestHelpers {
  def expectCompileTime(macros: String, code: String, expected: String) = ???

  def expectCompileTime(code: String, expected: String) = {
    val interpreter = new Interpreter
    val ast = parse(code)
    val value = interpreter.evaluate(ast, EvalMode.CompileTimeOnly)

    val resultCode = interpreter.toAST(value).toString
    val expectedCode = Unparser.unparse(parse(expected))

    assert(resultCode == expectedCode)
  }

  def expectTypeError(code: String) = {
    val interpreter = new Interpreter
    val ast = parse(code)

    val error = intercept[TypeError] {
      try {
        val result = interpreter.evaluate(ast, EvalMode.Partial)
//        println(interpreter.toAST(result).toString)
      } catch {
        case error: TypeError => throw error
        case error =>
          println(error)
          throw error
      }
    }

    println(error)
  }

  def expectParseError(code: String) = {
    val error = intercept[ParseError] {
      try {
        parse(code)
      } catch {
        case error: TypeError => throw error
        case error =>
          println(error)
          throw error
      }
    }

    println(error)
  }

  def expectPartial(code: String, expected: String) = {
    val interpreter = new Interpreter
    val ast = parse(code)
    val value = interpreter.evaluate(ast, EvalMode.Partial)

    val resultCode = interpreter.toAST(value).toString
    val expectedCode = Unparser.unparse(parse(expected))

    assert(resultCode == expectedCode)
  }

  def expectPhases(code: String, compile: String, run: String) = {
    val interpreter = new Interpreter
    val ast = parse(code)
    val compileValue = interpreter.evaluate(ast, EvalMode.Partial)

    val resultCompileCode = interpreter.toAST(compileValue).toString
    val expectedCompileTimeCode = Unparser.unparse(parse(compile))

    assert(resultCompileCode == expectedCompileTimeCode)

    val runtimeValue = interpreter.evaluate(compileValue, EvalMode.RunTime)
    val resultRunTimeCode = interpreter.toAST(runtimeValue).toString
    val expectedRunTimeCode = Unparser.unparse(parse(run))

    assert(resultRunTimeCode == expectedRunTimeCode)
  }

  private def parse(code: String) = {
    val lexer = new Lexer("<test>", code)
    val parser = new Parser(lexer, Interpreter.macroHandler)

    parser.parseRoot()
  }
}
