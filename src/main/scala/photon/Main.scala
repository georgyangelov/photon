package photon

import photon.base.EvalMode
import photon.frontend.{Lexer, Parser}
import photon.interpreter.Interpreter

import java.io.FileInputStream

object Main {
  def main(args: Array[String]): Unit = {
    val filePath = args.head
    val fileStream = new FileInputStream(filePath)

    val lexer = new Lexer(filePath, fileStream)
    val parser = new Parser(lexer, Interpreter.macroHandler)

    val ast = parser.parseRoot()

    val interpreter = new Interpreter
    val compileValue = interpreter.evaluate(ast, EvalMode.Partial)
    val runtimeValue = interpreter.evaluate(compileValue, EvalMode.RunTime)

    println(interpreter.toAST(runtimeValue).toString)
  }
}
