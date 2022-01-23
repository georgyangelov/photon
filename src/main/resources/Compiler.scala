package photon.compiler

import photon.EValue
import photon.core.{Core, Method}
import photon.core.operations.{BlockValue, FunctionValue}

import scala.collection.mutable

case class CompilerContext(
  compiler: Compiler,
  definitions: StringBuilder,
  code: StringBuilder,
  returnName: Option[String]
) {
  def newFunction(returnName: String): CompilerContext = {
    val fnBuilder = new StringBuilder

    CompilerContext(compiler, definitions, fnBuilder, Some(returnName))
  }

  def withoutReturn = CompilerContext(compiler, definitions, code, None)
  def returnInto(returnName: String) =
    CompilerContext(compiler, definitions, code, Some(returnName))

  def endFunction(): Unit = {
    definitions.append("\n\n")
    definitions.append(code.result)
  }

  def appendValue(string: String) = {
    if (returnName.isDefined) {
      code.append(returnName.get)
      code.append(" = ")
      code.append(string)
    } else {
      code.append(string)
    }
  }

  def requireType(value: EValue): String =
    compiler.typedefs.get(value) match {
      case Some(name) => name
      case None =>
        value.compile(this)

        compiler.typedefs(value)
    }

  def requireFunction(method: Method): String =
    compiler.functions.get(method) match {
      case Some(name) => name
      case None =>
        method.compile(this)

        compiler.functions(method)
    }
}

class Compiler(core: Core) {
  val typedefs = new mutable.HashMap[EValue, String]
  val functions = new mutable.HashMap[Method, String]

  def compileProgram(evalues: Seq[EValue]): String = {
    typedefs.addOne(photon.core.Int -> "int")

    val context = CompilerContext(
      this,
      new StringBuilder(),
      new StringBuilder(),
      None
    )

    context.definitions.append("#include <stdio.h>\n")

    BlockValue(evalues, None).compile(context)

    context.definitions.append("int main() {\n")
    context.definitions.append(context.code)
    context.definitions.append("  return 0;\n")
    context.definitions.append("}")

    context.definitions.result
  }
}
