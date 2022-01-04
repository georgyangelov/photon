package photon.core.operations

import photon.core.{Core, StandardType, TypeRoot}
import photon.interpreter.Interpreter
import photon.{EValue, Location, Scope, UFunction, UOperation, UParameter, UValue, VariableName}

object FunctionDef extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

case class FunctionDefValue(fn: photon.UFunction, scope: Scope, location: Option[Location]) extends EValue {
  override val typ = FunctionDef

  override def evalMayHaveSideEffects = false
  override def evalType = Some(evaluate.typ)

  // TODO: Should this indirection be here at all?
  //       Maybe when type inference for parameters is implemented?
  override protected def evaluate: EValue = {
    val interpreter = new Interpreter()
    val eParams = fn.params.map { param =>
      val argType = interpreter.toEValue(param.typ, scope)

      EParameter(param.name, argType, location)
    }

//    val innerScope = scope.newChild()

//    val ebody = interpreter.toEValue(fn.body, )

    val eReturnType = interpreter.toEValue(fn.returnType, scope)

//    val eReturnType = fn.returnType
//      .map(interpreter.toEValue(_, scope))
//      .orElse(ebody.evalType)
//      .getOrElse(ebody.typ)

    val functionType = FunctionT(eParams, eReturnType)

    FunctionValue(functionType, fn.body, scope, location)
  }

  override def toUValue(core: Core) = UOperation.Function(fn, location)
}

case class FunctionT(params: Seq[EParameter], returnType: EValue) extends StandardType {
  override def typ = TypeRoot
  override val location = None

  // TODO: Add `call` method here
  override val methods = Map.empty

  // TODO: This needs to become convertible to a function call building the type
  override def toUValue(core: Core) = inconvertible
}

case class EParameter(name: VariableName, typ: EValue, location: Option[Location]) {
  def toUParameter(core: Core) = UParameter(name, typ.toUValue(core), location)
}

case class FunctionValue(typ: FunctionT, body: UValue, scope: Scope, location: Option[Location]) extends EValue {
  override def evalType = None
  override def evalMayHaveSideEffects = false
  override protected def evaluate: EValue = this

  override def toUValue(core: Core) = UOperation.Function(
    new UFunction(
      typ.params.map(_.toUParameter(core)),
      body,
      typ.returnType.toUValue(core)
    ),
    location
  )
}
