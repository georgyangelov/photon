package photon

import com.typesafe.scalalogging.Logger
import org.scalatest.Matchers.intercept
import org.scalatest.Assertions._
import photon.frontend.{ASTBlock, Lexer, Parser, Unparser, ValueToAST}
import photon.interpreter.{EvalError, Interpreter}

object TestHelpers {
  def parseCode(code: String, macroHandler: Parser.MacroHandler = Parser.BlankMacroHandler): ASTBlock = {
    val parser = new Parser(new Lexer("<testing>", code), macroHandler)
    val values = parser.parseAll()

    ASTBlock(values)
  }

  def evalCompileTime(prelude: Option[String], code: String): Value = {
    val interpreter = new Interpreter(/* RunMode.CompileTime */)

    prelude match {
      case Some(prelude) =>
        val preludeAST = parseCode(prelude, interpreter.macroHandler)

        interpreter.evaluate(preludeAST)
      case None =>
    }

    val value = parseCode(code, interpreter.macroHandler)

    interpreter.evaluate(value)
  }

  def evalRunTime(code: String): Value = {
    val interpreter = new Interpreter(/* RunMode.Runtime */)
    val value = parseCode(code, Parser.BlankMacroHandler)

    interpreter.evaluate(value)
  }

  def eval(prelude: String, code: String): Value = {
    val logger = Logger("TestHelpers")

    val compiledValue = evalCompileTime(Some(prelude), code)
    val compileTimeCode = Unparser.unparse(ValueToAST.transformAsBlock(compiledValue))

    logger.debug(s"Compile-time evaluated to $compileTimeCode")

    val runtimeResult = evalRunTime(compileTimeCode)

    runtimeResult
  }

  def expectEvalCompileTime(actualCode: String, expectedCode: String): Unit = {
    assert(evalCompileTime(None, actualCode).toString == parseCode(expectedCode).toString)
  }

  def expectEvalCompileTime(prelude: String, actualCode: String, expectedCode: String): Unit = {
    assert(evalCompileTime(Some(prelude), actualCode).toString == parseCode(expectedCode).toString)
  }

  def expectFailCompileTime(actualCode: String, message: String): Unit = {
    val evalError = intercept[EvalError] { evalCompileTime(None, actualCode) }

    assert(evalError.message.contains(message))
  }

  def expectEval(prelude: String, actualCode: String, expectedResult: String): Unit = {
    val result = eval(prelude, actualCode)

    assert(result.toString == parseCode(expectedResult).toString)
  }

  def expectEval(actualCode: String, expectedResult: String): Unit = {
    expectEval("", actualCode, expectedResult)
  }

  def expectEvalRuntime(actualCode: String, expectedResult: String): Unit = {
    val result = evalRunTime(actualCode)

    assert(result.toString == parseCode(expectedResult).toString)
  }

  def expectPhases(actualCode: String, expectedCompileTimeCode: String, expectedResult: String): Unit = {
    val compiledValue = evalCompileTime(None, actualCode)

    assert(compiledValue.toString == parseCode(expectedCompileTimeCode).toString)

    val result = evalRunTime(Unparser.unparse(ValueToAST.transformAsBlock(compiledValue)))

    assert(result.toString == parseCode(expectedResult).toString)
  }

  def expectRuntimeFail(actualCode: String, message: String): Unit = {
    val compiledValue = evalCompileTime(None, actualCode)
    val runtimeCode = Unparser.unparse(ValueToAST.transformAsBlock(compiledValue))

    Logger("InterpreterTest").debug(s"Compiled code result: $runtimeCode")

    val evalError = intercept[EvalError] { evalRunTime(runtimeCode) }

    assert(evalError.message.contains(message))
  }
}
