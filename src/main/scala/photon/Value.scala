package photon

import photon.core.NativeValue
import photon.frontend.{Unparser, ValueToAST}
import photon.interpreter.EvalError
import photon.lib.ObjectId

import scala.collection.Map

/* Values and operations */

sealed trait Value {
  override def toString = ValueToAST.transformForInspection(this).toString

  def unboundNames: Set[VariableName]
  val location: Option[Location]

  def realValue: Option[RealValue]
}

// Type groups
sealed trait RealValue extends Value {
  def isFullyKnown: scala.Boolean = isFullyKnown(Set.empty)
  def isFullyKnown(alreadyKnownBoundFunctions: Set[BoundValue.Function]): scala.Boolean = true

  def callsShouldIncludeSelf: Boolean = true
}

sealed trait UnboundValue extends Value {
  def realValue: Option[RealValue]
  def mayHaveSideEffects: Boolean

  def asBlock: Operation.Block = this match {
    case block: Operation.Block => block
    case _ => Operation.Block(Seq(this), realValue, location)
  }
}

// Specific types
sealed trait PureValue extends RealValue with UnboundValue {
  override def isFullyKnown = true
  override def isFullyKnown(_alreadyKnownBoundFunctions: Set[BoundValue.Function]) = true

  def realValue: Some[RealValue] = Some(this)
  override def mayHaveSideEffects = false

  override def unboundNames = Set.empty
}

sealed trait BoundValue extends RealValue {
  val scope: Scope
}

sealed trait Operation extends UnboundValue {
  val realValue: Option[RealValue]
  val unboundNames: Set[VariableName]

  override def mayHaveSideEffects = true
}

object PureValue {
  case class Nothing(location: Option[Location])                         extends Value with PureValue with RealValue with UnboundValue
  case class Boolean(value: scala.Boolean, location: Option[Location])   extends Value with PureValue with RealValue with UnboundValue
  case class Int(value: scala.Int, location: Option[Location])           extends Value with PureValue with RealValue with UnboundValue
  case class Float(value: scala.Double, location: Option[Location])      extends Value with PureValue with RealValue with UnboundValue
  case class String(value: java.lang.String, location: Option[Location]) extends Value with PureValue with RealValue with UnboundValue
  case class Native(native: NativeValue, location: Option[Location])     extends Value with PureValue with RealValue with UnboundValue {
    override def isFullyKnown = native.isFullyEvaluated

    // TODO: Not sure about this. Maybe the `Native` values should be their own category (outside of PureValue)
    override def mayHaveSideEffects = false
  }
}

object BoundValue {
  case class Function(fn: photon.Function, traits: Set[FunctionTrait], scope: Scope, location: Option[Location])
    extends Value with BoundValue with RealValue {

    override def unboundNames = fn.unboundNames
    override def realValue = Some(this)
    override def callsShouldIncludeSelf = false

    override def isFullyKnown(alreadyKnownBoundFunctions: Set[BoundValue.Function]): scala.Boolean = {
      if (alreadyKnownBoundFunctions.contains(this)) {
        return true
      }

      if (!traits.contains(FunctionTrait.CompileTime)) {
        return false
      }

      fn.unboundNames.forall { name =>
        scope.find(name) match {
          case Some(Variable(_, value)) => value.flatMap(_.realValue).exists(_.isFullyKnown(alreadyKnownBoundFunctions + this))
          // TODO: Specify location
          case None => throw EvalError(s"Cannot find name $name during isFullyKnown check", None)
        }
      }
    }
  }

  case class Object(values: Map[String, Value], scope: Scope, location: Option[Location])
    extends Value with BoundValue with RealValue {

    lazy override val unboundNames = values.view.values.flatMap(_.unboundNames).toSet
    override def realValue = Some(this)

    override def isFullyKnown(alreadyKnownBoundFunctions: Set[Function]) =
      values.view.values.map(_.realValue).forall {
        case Some(value: RealValue) => value.isFullyKnown(alreadyKnownBoundFunctions)
        case _ => false
      }
  }
}

object Operation {
  case class Block(values: Seq[UnboundValue], realValue: Option[RealValue], location: Option[Location])
    extends Value with Operation with UnboundValue {

    lazy override val unboundNames = values.view.flatMap(_.unboundNames).toSet
  }

  case class Let(name: VariableName, letValue: UnboundValue, block: Block, realValue: Option[RealValue], location: Option[Location])
    extends Value with Operation with UnboundValue {

    lazy override val unboundNames = (letValue.unboundNames ++ block.unboundNames) - name
  }
  case class Reference(name: VariableName, realValue: Option[RealValue], location: Option[Location])
    extends Value with Operation with UnboundValue {

    override val unboundNames = Set(name)
    override def mayHaveSideEffects = false
  }

  case class Function(fn: photon.Function, realValue: Option[RealValue], location: Option[Location])
    extends Value with Operation with UnboundValue {

    override val unboundNames = fn.unboundNames
    override def mayHaveSideEffects = false
  }
  case class Call(target: UnboundValue, name: String, arguments: Arguments[UnboundValue], realValue: Option[RealValue], location: Option[Location])
    extends Value with Operation with UnboundValue {

    lazy override val unboundNames =
      target.unboundNames ++
        arguments.positional.view.flatMap(_.unboundNames).toSet ++
        arguments.named.view.values.flatMap(_.unboundNames).toSet
  }

//  case class Object(values: Map[String, UnboundValue], realValue: Option[RealValue], location: Option[Location])
//    extends Value with Operation with UnboundValue {
//
//    lazy override val unboundNames = values.view.flatMap(unboundNames).toSet
//    override def mayHaveSideEffects = false
//  }
}



/* Variables and scopes */

class VariableName(val originalName: String) extends Equals {
  private val objectId: Long = ObjectId().id

  def uniqueId = objectId

  override def canEqual(that: Any): Boolean = that.isInstanceOf[VariableName]
  override def equals(that: Any): Boolean = {
    that match {
      case other: VariableName => this.objectId == other.objectId
      case _ => false
    }
  }
  override def hashCode(): Int = objectId.hashCode
}

class Variable(val name: VariableName, private var _value: Option[Value]) {
  def value: Option[Value] = _value

  def dangerouslySetValue(newValue: Value): Unit = _value = Some(newValue)
}

object Variable {
  def unapply(variable: Variable) = Some(variable.name, variable.value)
}

object Scope {
  def newRoot(variables: Seq[Variable]): Scope = {
    Scope(
      None,
      variables.map { variable => variable.name -> variable }.toMap
    )
  }
}

case class Scope(parent: Option[Scope], variables: Map[VariableName, Variable]) {
  def newChild(variables: Seq[Variable]): Scope = {
    Scope(
      Some(this),
      variables.map { variable => variable.name -> variable }.toMap
    )
  }

  override def toString: String = {
    val values = variables.map { case name -> variable => name.originalName -> variable.value.toString }

    if (parent.isDefined) {
      s"$values -> ${parent.get.toString}"
    } else {
      values.toString
    }
  }

  def find(name: VariableName): Option[Variable] = {
    variables.get(name) orElse { parent.flatMap(_.find(name)) }
  }
}



/* Functions */

sealed abstract class FunctionTrait

object FunctionTrait {
  case object Partial extends FunctionTrait
  case object CompileTime extends FunctionTrait
  case object Runtime extends FunctionTrait
  case object Pure extends FunctionTrait
}

class Function(
  val selfName: VariableName,
  val params: Seq[Parameter],
  val body: Operation.Block,
  val returnType: Option[UnboundValue]
) extends Equals {
  val objectId = ObjectId()

  val unboundNames = body.unboundNames -- params.map(_.name) - selfName

  override def canEqual(that: Any): Boolean = that.isInstanceOf[Function]
  override def equals(that: Any): Boolean = {
    that match {
      case other: Function => this.objectId == other.objectId
      case _ => false
    }
  }
  override def hashCode(): Int = objectId.hashCode
}

case class Parameter(name: VariableName, typeValue: Option[Value], location: Option[Location])



/* Arguments */

case class Arguments[T <: Value](
  self: Option[T],
  positional: Seq[T],
  named: Map[String, T]
) {
  def withoutSelf = Arguments(None, positional, named)
  def withSelf(value: T) = Arguments(Some(value), positional, named)

  def map[R <: Value](f: T => R) = Arguments(
    self.map(f),
    positional.map(f),
    named.view.mapValues(f).toMap
  )

  def forall(f: T => Boolean) = self.forall(f) && positional.forall(f) && named.view.values.forall(f)

  override def toString = Unparser.unparse(
    ValueToAST.transformForInspection(
      this.asInstanceOf[Arguments[Value]]
    )
  )
}

object Arguments {
  def empty[T <: Value]: Arguments[T] = Arguments(None, Seq.empty, Map.empty)

  def positional[T <: Value](values: Seq[T]) = Arguments[T](
    self = None,
    positional = values,
    named = Map.empty
  )
}
