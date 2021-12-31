package photon.core2

import photon.interpreter.CallContext
import photon.{Arguments, EValue, Location}

abstract class Type extends EValue {
  def method(name: String): Option[Method]
}

trait Method {
  val traits: Set[MethodTrait]

  def typeCheck(argumentTypes: Arguments[Type]): Type
  def call(context: CallContext, args: Arguments[EValue], location: Option[Location]): EValue
}

sealed abstract class MethodTrait
object MethodTrait {
  object CompileTime extends MethodTrait
  object RunTime     extends MethodTrait
}

object TypeRoot extends Type {
  override val location = None
  lazy val typ = this

  override def method(name: String) = None
}

abstract class StandardType extends Type {
  val methods: Map[String, Method]

  override def method(name: String) = methods.get(name)
}