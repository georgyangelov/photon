package photon.core

import photon.lib.ObjectId
import photon.{Arguments, EValue, Location, UFunction}

abstract class Type extends EValue {
  def method(name: String): Option[Method]
}

trait Method extends Equals {
//  val runMode: MethodRunMode
//  val inlinePreference: InlinePreference = InlinePreference.Default

  def typeCheck(args: Arguments[EValue]): Type
  def call(args: Arguments[EValue], location: Option[Location]): EValue

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

sealed abstract class MethodRunMode
object MethodRunMode {
  object CompileTimeOnly extends MethodRunMode
  object Default         extends MethodRunMode
  object PreferRunTime   extends MethodRunMode
  object RunTimeOnly     extends MethodRunMode
}

sealed abstract class InlinePreference
object InlinePreference {
  object ForceInline extends InlinePreference
  object Default     extends InlinePreference
  object NoInline    extends InlinePreference
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