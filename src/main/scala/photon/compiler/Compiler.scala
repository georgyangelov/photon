package photon.compiler

import photon.{EValue, Location, PhotonError, VariableName}
import photon.core.Method

import scala.collection.mutable

sealed trait CCode
object CCode {
  case object Nothing extends CCode
  case class Block(statements: Seq[String]) extends CCode
  case class Statement(code: String) extends CCode
  case class Expression(code: String) extends CCode

  case class BlockBuilder(context: CompileContext) {
    private[this] val statements = Seq.newBuilder[String]

    def addStatement(code: String): Unit = statements.addOne(code)
    def addStatement(value: EValue): Unit = {
      val result = value.compile(context.withoutReturn)

      result match {
        case Nothing =>
        // TODO: Can this not be a sub-block (i.e. `{ <code> }`?)
        case Block(subStatements) => statements.addOne(s"{\n ${subStatements.mkString(";\n")} }\n")
        case Statement(code) => addStatement(code)
        case Expression(code) => addStatement(code)
      }
    }

    def addExpression(value: EValue): String = {
      val varName = s"expr__${new VariableName("expr").uniqueId}"
      val
      val expressionCode = value.compile(context.returnsIn(Some()))

      expressionCode match {
        case Nothing => ???
        case Block(statements) => ???
        case Statement(code) => ???
        case Expression(code) => ???
      }
    }

    def addReturn(code: String): Unit = context.returnName match {
      case Some(returnName) => statements.addOne(s"$returnName = ($code)")
      case None => statements.addOne(code)
    }
    def addReturn(value: EValue): Unit = value.compile(context)

    def build = CCode.Block(statements.result)
  }
}

case class CompileError(message: String, override val location: Option[Location] = None)
  extends PhotonError(message, location) {}

class Compiler {
  val definitions = Seq.newBuilder[String]
  val typeNames = new mutable.HashMap[EValue, String]
  val functionNames = new mutable.HashMap[Method, String]

  private val rootContext = CompileContext(this, new StringBuilder, None)

  def defineType(value: EValue, name: String, code: String): Unit = {
    definitions.addOne(code)
    typeNames.addOne(value -> name)
  }

  def defineFunction(method: Method, name: String, code: String): Unit = {
    definitions.addOne(code)
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
  private val blockCode: StringBuilder,
  returnName: Option[String]
) {


  def toStatement = CCode.Statement(blockCode.result)

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

  def returnsIn(returnName: Option[String]) = CompileContext(compiler, blockCode, returnName)
  def withoutReturn = CompileContext(compiler, blockCode, None)
}