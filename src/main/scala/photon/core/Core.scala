package photon.core

import photon.frontend.StaticScope
import photon.interpreter.EvalError
import photon.{EValue, Location, Scope, UOperation, Variable, VariableName}

object CoreType extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override val methods = Map.empty
  override def toUValue(core: Core) = inconvertible
}

class Core extends EValue {
  override val typ = CoreType
  override val location = None
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override protected def evaluate: EValue = this
  override def toUValue(core: Core) = this.referenceTo(this, location)

  lazy val globals = Globals.of(
    "Core" -> this,
    "Type" -> TypeRoot,
    "Bool" -> photon.core.Bool,
    "Int" -> photon.core.Int,
    "Float" -> photon.core.Float,
    "String" -> photon.core.String,
    // "List" -> photon.core.List,
    // "Function" -> photon.core.operations.Function,
    // "Class" -> photon.core.Class
    // "Optional" -> photon.core.Optional
  )

  lazy val rootScope = Scope.newRoot(globals.names)
  lazy val staticRootScope = StaticScope.fromScope(rootScope)

  def referenceTo(value: EValue, location: Option[Location]) =
    globals.referenceTo(value, location)
}

object Globals {
  def of(vars: (String, EValue)*) = {
    new Globals(vars.map { case (name, value) => Variable(new VariableName(name), value) })
  }
}

class Globals(val names: Seq[Variable]) {
  def referenceTo(value: EValue, location: Option[Location]) = {
    val name = names
      .find { case Variable(_, evalue) => evalue == value }
      .getOrElse { throw EvalError(s"Cannot find a name for $value", location) }
      .name

    UOperation.Reference(name, location)
  }
}