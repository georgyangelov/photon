package photon.core.operations

import photon.compiler.CompileContext
import photon.core.{Core, StandardType, TypeRoot}
import photon.{EValue, Location, UOperation, Variable, VariableName}

import scala.annotation.tailrec
import scala.collection.mutable

object Let extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
  override def compile(context: CompileContext) = uncompilable
}

case class LetValue(name: VariableName, value: EValue, body: EValue, location: Option[Location]) extends EValue {
  override val typ = Block
  override def unboundNames = value.unboundNames ++ body.unboundNames - name
  override def evalMayHaveSideEffects = value.evalMayHaveSideEffects || body.evalMayHaveSideEffects
  override def evalType = Some(body.evalType.getOrElse(body.typ))

  override protected def evaluate: EValue = {
    val ENABLE_SIDE_EFFECT_CHECK = true

    val evalue = value.evaluated
    val ebody = body.evaluated

    if (ebody.unboundNames.contains(name)) {
      LetValue(name, evalue, ebody, location)
    } else if (!ENABLE_SIDE_EFFECT_CHECK) {
      BlockValue(Seq(evalue, ebody), location)
    } else if (evalue.evalMayHaveSideEffects) {
      BlockValue(Seq(evalue, ebody), location)
    } else {
      ebody.evaluated
    }
  }

  override def toUValue(core: Core) = UOperation.Let(name, value.toUValue(core), body.toUValue(core), location)

  def partialValue: PartialValue = partialValue(Seq.newBuilder)

  @tailrec
  private def partialValue(variables: mutable.Builder[Variable, Seq[Variable]]): PartialValue = {
    variables.addOne(Variable(name, value))

    body match {
      case let: LetValue => let.partialValue(variables)
      case _ => PartialValue(body, variables.result)
    }
  }

  override def compile(context: CompileContext) = {
    val block = context.newInnerBlock.withoutReturn

    val typ = value.evalType.getOrElse(value.typ)
    val cType = block.typeNameOf(typ)
    val cName = s"${name.originalName}$$${name.uniqueId}"
    val cValue = block.valueOf(value, "letValue")

    block.addCode(s"$cType $cName = $cValue;\n");
    block.addStatement()


    s"""
      $typeName $cName = ${context.valueOf(value, "letValue")};

      ${context.addStatement()}
    """.stripMargin.trim

//    val typ = value.evalType.getOrElse(value.typ)
//    val cType = context.requireType(typ)
//
//    val cName = s"${name.originalName}$$${name.uniqueId}"
//
//    context.code.append(cType).append(" ").append(cName).append(";\n")
//
//    value.compile(context.returnInto(cName))
//    context.code.append(";\n")
//
//    context.code.append("{")
//    body.compile(context)
//    context.code.append("}\n")
  }
}

case class PartialValue(value: EValue, variables: Seq[Variable]) {
  def replaceWith(newValue: EValue) = PartialValue(newValue, variables)
  def addOuterVariables(additionalVars: Seq[Variable]) = PartialValue(value, additionalVars ++ variables)
  def addInnerVariables(additionalVars: Seq[Variable]) = PartialValue(value, variables ++ additionalVars)

  def wrapBack: EValue =
    variables.foldRight(value) { case (Variable(name, varValue), innerValue) =>
      if (innerValue.unboundNames.contains(name)) {
        LetValue(name, varValue, innerValue, varValue.location)
      } else {
        innerValue
      }
    }
}