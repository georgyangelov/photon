package photon.core

import photon.{EValue, Method}

abstract class Type extends EValue {
  def method(name: String): Option[Method]
}

object TypeRoot extends Type {
  override val location = None
  lazy val typ = this
  override def unboundNames = Set.empty
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def evaluate = this
  override def finalEval = this

  override def method(name: String) = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
}

object StaticType extends Type {
  override val location = None

  lazy val typ = this
  override def unboundNames = Set.empty
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def evaluate = this
  override def finalEval = this

  override def method(name: String) = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
}

abstract class StandardType extends Type {
  val methods: Map[String, Method]

  override def unboundNames = Set.empty
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def evaluate = this
  override def finalEval = this

  override def method(name: String) = methods.get(name)
}