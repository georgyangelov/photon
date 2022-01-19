package photon.core

import photon.compiler.CompilerContext
import photon.interpreter.EvalError
import photon.{EValue, Location}

object Unknown extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
  override def compile(output: CompilerContext): Unit = uncompilable
}

case class UnknownValue(etype: Type, location: Option[Location]) extends EValue {
  override def typ = Unknown
  override def unboundNames = Set.empty
  override def evalMayHaveSideEffects = false
  override def evalType = Some(etype)
  override def toUValue(core: Core) = inconvertible
  override def evaluate = throw EvalError("Cannot evaluate Unknown", location)
  override def compile(output: CompilerContext): Unit = uncompilable
}
