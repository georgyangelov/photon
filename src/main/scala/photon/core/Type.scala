package photon.core

import photon.compiler.{CCode, CompileContext, Compiler}
import photon.lib.ObjectId
import photon.{Arguments, EValue, Location, UFunction}

abstract class Type extends EValue {
  def method(name: String): Option[Method]
}

trait Method extends Equals {
  val traits: Set[MethodTrait]

  def typeCheck(args: Arguments[EValue]): Type
  def call(args: Arguments[EValue], location: Option[Location]): EValue
  def compile(compiler: Compiler): Unit = ???

  val objectId = ObjectId()

  override def canEqual(that: Any): Boolean = that.isInstanceOf[UFunction]
  override def equals(that: Any): Boolean = {
    that match {
      case other: UFunction => this.objectId == other.objectId
      case _ => false
    }
  }
  override def hashCode(): Int = objectId.hashCode
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
  override def unboundNames = Set.empty
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def evaluate = this

  override def method(name: String) = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override def compile(context: CompileContext): CCode = uncompilable
}

abstract class StandardType extends Type {
  val methods: Map[String, Method]

  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def evaluate = this

  override def method(name: String) = methods.get(name)
}