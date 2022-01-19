package photon.core

import photon.compiler.CompilerContext
import photon.{EValue, Location}

object Lazy extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
  override def compile(output: CompilerContext): Unit = uncompilable
}

case class LazyValue(lazyValue: photon.lib.Lazy[EValue], location: Option[Location]) extends EValue {
  override def typ = lazyValue.resolve.typ
  override def unboundNames = lazyValue.resolve.unboundNames
  override def evalMayHaveSideEffects = lazyValue.resolve.evalMayHaveSideEffects
  override def evalType = lazyValue.resolve.evalType
  override def toUValue(core: Core) = inconvertible
  override def evaluate = lazyValue.resolve.evaluated
  override def compile(output: CompilerContext): Unit = lazyValue.resolve.compile(output)
}
