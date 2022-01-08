package photon.core

import photon.{Arguments, EValue, Location}

abstract class Type extends EValue {
  def method(name: String): Option[Method]
}

trait Method {
  val traits: Set[MethodTrait]

  def typeCheck(args: Arguments[EValue]): Type
  def call(args: Arguments[EValue], location: Option[Location]): EValue
}

sealed abstract class MethodTrait
object MethodTrait {
  object CompileTime extends MethodTrait
  object RunTime     extends MethodTrait
  object SideEffects extends MethodTrait
}

object TypeRoot extends Type {
  override val location = None
  lazy val typ = this
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def evaluate = this

  override def method(name: String) = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
}

abstract class StandardType extends Type {
  val methods: Map[String, Method]

  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def evaluate = this

  override def method(name: String) = methods.get(name)
}