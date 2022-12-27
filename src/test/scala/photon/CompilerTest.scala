package photon

import org.scalatest._
import photon.frontend.{ASTToValue, Lexer, Parser, StaticScope}
import photon.compiler._

class CompilerTest extends FunSuite {
  test("can compile simple values") {
    assertRun("42", 42)
  }

  test("can define functions") {
    assertRun()
  }

  private def assertRun(code: String, expected: Any): Unit = {
    val ast = parse(code)
    val value = ASTToValue.transform(ast, StaticScope.newRoot(Seq.empty))

    val program = new Compiler(value).compile
    val vm = new VM(program)

    val result = vm.executeMain()

    assert(result.obj == expected)
  }

  private def parse(code: String) = {
    val lexer = new Lexer("<test>", code)
    val parser = new Parser(lexer, Parser.BlankMacroHandler)

    parser.parseRoot()
  }
}
