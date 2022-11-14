package photon.interpreter

import photon.base._
import photon.core.$Object
import photon.core.objects._
import photon.frontend.Parser.MacroHandler
import photon.frontend._
import photon.frontend.macros.ClassMacro

object Interpreter {
  def macroHandler(name: String, parser: Parser, location: Location): Option[ASTValue] =
    name match {
      case "class" => Some(ClassMacro.classMacro(parser, location))
      case "interface" => Some(ClassMacro.interfaceMacro(parser, location))
      case "def" => Some(ClassMacro.defMacro(parser, location))
      case _ => None
    }
}

class Interpreter {
  val rootScope = Scope.newRoot(Seq(
    new VarName("Boolean") -> $Boolean,
    new VarName("Int") -> $Int,
    new VarName("String") -> $String,
    new VarName("Core") -> $Object(null, $Core, None),
    new VarName("Class") -> $Object(null, $Class, None),
    new VarName("Interface") -> $Object(null, $Interface, None),
    new VarName("ClassBuilder") -> $ClassBuilder
  ))

  def evaluate(ast: ASTValue, evalMode: EvalMode): Value = {
    val value = ASTToValue.transform(ast, StaticScope.fromRootScope(rootScope))

    evaluate(value, evalMode)
  }

  def evaluate(value: Value, evalMode: EvalMode): Value = {
    val env = Environment(rootScope, evalMode)

    value.evaluate(env)
  }

  def toAST(value: Value): ASTValue = {
    val rootNames = rootScope
      .variables
      .map { case key -> _ => key -> key.originalName }
      .toMap

    value.toAST(rootNames)
  }
}