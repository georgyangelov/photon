package photon.interpreter

import photon.base._
import photon.core._
import photon.core.objects._
import photon.frontend._
import photon.frontend.macros.ClassMacro

object Interpreter {
  def macroHandler(name: String, parser: Parser, location: Location): Option[ASTValue] =
    name match {
      case "class" => Some(ClassMacro.classMacro(parser, location))
      case "object" => Some(ClassMacro.objectMacro(parser, location))
      case "interface" => Some(ClassMacro.interfaceMacro(parser, location))
      case "def" => Some(ClassMacro.defMacro(parser, location))
      case _ => None
    }
}

class Interpreter {
  val rootScope = Scope.newRoot(Seq(
    new VarName("Type") -> $Type,
    new VarName("Boolean") -> $Boolean,
    new VarName("Int") -> $Int,
    new VarName("String") -> $String,
    new VarName("Core") -> $Object(null, $Core, None),
    new VarName("Class") -> $Object(null, $Class, None),
    new VarName("Object") -> $Object(null, $ObjectSingleton, None),
    new VarName("Interface") -> $Object(null, $Interface, None),
    new VarName("ClassBuilder") -> $ClassBuilder,
    new VarName("NativeHandle") -> $NativeHandle,
    new VarName("Internal") -> $Object(null, $Internal, None),
  ))

  def evaluate(ast: ASTValue, evalMode: EvalMode): Value = {
    val value = ASTToValue.transform(ast, StaticScope.fromRootScope(rootScope))

    evaluate(value, evalMode)
  }

  def evaluate(value: Value, evalMode: EvalMode): Value = {
    val env = Environment(rootScope, evalMode)
    val result = value.evaluate(env)

    result.partiallyEvaluateInnerClosures(env)

    result.value
  }

  def toAST(value: Value): ASTValue = {
    val rootNames = rootScope
      .variables
      .map { case key -> _ => key -> key.originalName }
      .toMap

    value.toAST(rootNames)
  }
}