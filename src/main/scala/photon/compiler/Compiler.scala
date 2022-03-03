package photon.compiler

import photon.{EValue, Location, PhotonError, VariableName}
import photon.core.Method
import photon.interpreter.EvalError

import scala.collection.mutable

sealed trait CCode
object CCode {
  val EmptyBlock = Block(Seq.empty)
  case class Block(statements: Seq[String]) extends CCode
  case class Expression(code: String) extends CCode

  case class BlockBuilder(context: CompileContext) {
    private[this] val statements = Seq.newBuilder[String]

    def addStatement(code: String): Unit = statements.addOne(code)
    def addStatement(value: EValue): Unit = {
      val result = value.compile(context.noReturn)

      result match {
        // TODO: Can this not be a sub-block (i.e. `{ <code> }`?)
        case Block(subStatements) =>
          if (subStatements.size == 1)
            statements.addOne(s"{\n ${subStatements.mkString(";\n")} }\n")
          else if (subStatements.nonEmpty)
            statements.addOne(subStatements.head)

        case Expression(code) => addStatement(code)
      }
    }

    def let(name: VariableName, value: EValue): String = {
      val cName = context.cNameFor(name)
      val expressionCode = value.compile(context.returnsIn(Some(cName)))

      val cType = context.typeNameOf(value.evalType.getOrElse(value.typ))
      statements.addOne(s"$cType $cName")

      expressionCode match {
        case Expression(code) =>
          statements.addOne(s"$cName = ($code)")

        case Block(subStatements) =>
          if (subStatements.isEmpty) {
            throw EvalError("Compiler#let value returned empty block", None)
          }

          if (subStatements.size == 1)
            statements.addOne(s"{\n ${subStatements.mkString(";\n")} }\n")
          else
            statements.addOne(subStatements.head)
      }

      cName
    }

    def resultOf(value: EValue): String = {
      val cName = context.cNameFor(new VariableName("resultOf"))
      val expressionCode = value.compile(context.returnsIn(Some(cName)))

      expressionCode match {
        case Expression(code) => code
        case Block(subStatements) =>
          if (subStatements.isEmpty) {
            throw EvalError(s"Compiler#resultOf value returned empty block (when compiling ${value})", None)
          }

          val cType = context.typeNameOf(value.evalType.getOrElse(value.typ))
          statements.addOne(s"$cType $cName")

          if (subStatements.size == 1)
            statements.addOne(s"{\n ${subStatements.mkString(";\n")} }\n")
          else
            statements.addOne(subStatements.head)

          cName
      }
    }

    def addReturn(code: String): Unit = context.returnName match {
      case Some(returnName) => statements.addOne(s"$returnName = ($code)")
      case None => statements.addOne(code)
    }

    def addReturn(value: EValue): Unit = value.compile(context) match {
      case Expression(code) => addReturn(code)
      case Block(subStatements) =>
        if (subStatements.size == 1)
          statements.addOne(s"{\n ${subStatements.mkString(";\n")} }\n")
        else
          statements.addOne(subStatements.head)
    }

    def build = CCode.Block(statements.result)
  }
}

case class CompileError(message: String, override val location: Option[Location] = None)
  extends PhotonError(message, location) {}

class Compiler {
  val definitions = Seq.newBuilder[String]
  val typeNames = new mutable.HashMap[EValue, String]
  val functionNames = new mutable.HashMap[Method, String]

  private val rootContext = CompileContext(this, None)

  def compileProgram(values: Seq[EValue]): String = {
    definitions.addOne("#include <stdio.h>\n")

    val block = rootContext.newBlock

    values.foreach(block.addStatement)

    val mainFnCode = block.build.statements.mkString(";\n")

    definitions.addOne(
      s"""
         int main() {
           $mainFnCode;

           return 0;
         }
      """
    )

    definitions.result.mkString("\n\n")
  }

  def defineType(value: EValue, name: String, code: String): Unit = {
    definitions.addOne(s"${code};\n")
    typeNames.addOne(value -> name)
  }

  def defineFunction(method: Method, name: String, code: String): Unit = {
    definitions.addOne(s"${code}\n")
    functionNames.addOne(method -> name)
  }

  def typeNameOf(value: EValue): String = {
    typeNames.getOrElse(value, {
      value.compile(rootContext)

      typeNames.getOrElse(value, throw CompileError(s"Expected $value to define a type"))
    })
  }

  def functionNameOf(method: Method): String = {
    functionNames.getOrElse(method, {
      method.compile(this)

      functionNames.getOrElse(method, throw CompileError(s"Expected $method to define a function"))
    })
  }
}

case class CompileContext(
  compiler: Compiler,
  returnName: Option[String]
) {
//  def toStatement = CCode.Statement(blockCode.result)

  def cNameFor(variableName: VariableName) = s"${variableName}__${variableName.uniqueId}"
  def typeNameOf(value: EValue) = compiler.typeNameOf(value)
  def functionNameOf(method: Method) = compiler.functionNameOf(method)

//  def valueOf(value: EValue, nameHint: String): CCode.Expression = {
//    val returnName = s"${nameHint}__${new VariableName(nameHint).uniqueId}_temp"
//    val context = CompileContext(compiler, blockCode, Some(returnName))
//
//    value.compile(context) match {
//      case CCode.Nothing => throw CompileError(s"Expected $value to compile to something")
//      case CCode.Statement(code) =>
//        // TODO: This won't work correctly
//        blockCode.append(s"${typeNameOf(value)} $returnName;\n")
//        blockCode.append(code).append(";\n")
//
//        CCode.Expression(returnName)
//
//      case cValue: CCode.Expression => cValue
//    }
//  }

  def newBlock = CCode.BlockBuilder(this)

//  def newInnerBlock = CompileContext(compiler, new StringBuilder(), returnName)

  def returnsIn(returnName: Option[String]) = CompileContext(compiler, returnName)
  def noReturn = CompileContext(compiler, None)
}