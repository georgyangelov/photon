package photon

import photon.core.NativeValue
import photon.frontend.{Unparser, ValueToAST}
import photon.interpreter.EvalError
import photon.lib.ObjectId

import scala.collection.Map



/* Values and operations */

sealed abstract class RealValue {
  def isFullyKnown: scala.Boolean = isFullyKnown(Set.empty)
  def isFullyKnown(alreadyKnownBoundFunctions: Set[BoundFunction]): scala.Boolean = true

  def asValue(location: Option[Location]) = Value.Real(this, location)

  override def toString = ValueToAST.transformForInspection(this).toString
}
object RealValue {
  case object Nothing extends RealValue
  case class Boolean(value: scala.Boolean) extends RealValue
  case class Int(value: scala.Int) extends RealValue
  case class Float(value: scala.Double) extends RealValue
  case class String(value: java.lang.String) extends RealValue
  case class Native(native: NativeValue) extends RealValue {
    override def isFullyKnown(_alreadyKnownBoundFunctions: Set[BoundFunction])
      = native.isFullyEvaluated
  }
  case class BoundFn(boundFn: BoundFunction) extends RealValue {
    override def isFullyKnown(alreadyKnownBoundFunctions: Set[BoundFunction]): scala.Boolean = {
      if (alreadyKnownBoundFunctions.contains(boundFn)) {
        return true
      }

      if (!boundFn.traits.contains(FunctionTrait.CompileTime)) {
        return false
      }

      boundFn.fn.unboundNames.forall { name =>
        boundFn.scope.find(name) match {
          case Some(Variable(_, value)) => value.isFullyKnown(alreadyKnownBoundFunctions + boundFn)
          // TODO: Specify location
          case None => throw EvalError(s"Cannot find name $name during isFullyKnown check", None)
        }
      }
    }
  }
}

sealed abstract class Operation {
  val realValue: Option[RealValue]
  val unboundNames: Set[VariableName]

  override def toString = ValueToAST.transformForInspection(this).toString
}
object Operation {
  case class Block(values: Seq[Value], realValue: Option[RealValue]) extends Operation {
    lazy override val unboundNames = values.view.flatMap(_.unboundNames).toSet
  }

  case class Let(name: VariableName, letValue: Value, block: Block, realValue: Option[RealValue])
    extends Operation {
    lazy override val unboundNames = (letValue.unboundNames ++ block.unboundNames) - name
  }
  case class Reference(name: VariableName, realValue: Option[RealValue]) extends Operation {
    override val unboundNames = Set(name)
  }

  case class Function(fn: photon.Function, realValue: Option[RealValue]) extends Operation {
    override val unboundNames = fn.unboundNames
  }
  case class Call(target: Value, name: String, arguments: Arguments, realValue: Option[RealValue])
    extends Operation {
    lazy override val unboundNames =
      target.unboundNames ++
        arguments.positional.view.flatMap(_.unboundNames).toSet ++
        arguments.named.view.values.flatMap(_.unboundNames).toSet
  }
}

sealed abstract class Value {
  def realValue: Option[RealValue]
  val location: Option[Location]

  def isReal: Boolean
  def isOperation: Boolean

  def realValueAsValue: Option[Value] = realValue.map(Value.Real(_, location))
  def realValueOrSelf = realValueAsValue.getOrElse(this)

  def unboundNames: Set[VariableName]

  override def toString = ValueToAST.transformForInspection(this).toString

  def asBlock: Operation.Block =
    this match {
      case Value.Operation(block @ Operation.Block(_, _), _) => block
      case _ => Operation.Block(Seq(this), realValue)
    }

  def isFullyKnown: Boolean = isFullyKnown(Set.empty)
  def isFullyKnown(alreadyKnownBoundFunctions: Set[BoundFunction]): Boolean =
    this match {
      case Value.Real(realValue, _) => realValue.isFullyKnown(alreadyKnownBoundFunctions)
      case Value.Operation(_, _) => false
    }
}
object Value {
  case class Real(value: RealValue, location: Option[Location]) extends Value {
    override def realValue = Some(value)
    override def isReal = true
    override def isOperation = false
    override def unboundNames = Set.empty
  }

  case class Operation(operation: photon.Operation, location: Option[Location]) extends Value {
    override def realValue = operation.realValue
    override def isReal = false
    override def isOperation = true
    override def unboundNames = operation.unboundNames
  }
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

class Variable(val name: VariableName, private var _value: Value) {
  def value: Value = _value

  def dangerouslySetValue(newValue: Value): Unit = _value = newValue
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

case class BoundFunction(
  fn: Function,
  scope: Scope,

  traits: Set[FunctionTrait]
)

sealed abstract class FunctionTrait

object FunctionTrait {
  case object Partial extends FunctionTrait
  case object CompileTime extends FunctionTrait
  case object Runtime extends FunctionTrait
  case object Pure extends FunctionTrait
}

class Function(
  val params: Seq[Parameter],
  val body: Operation.Block
) extends Equals {
  val objectId = ObjectId()

  val unboundNames = body.unboundNames -- params.map(_.name)

  override def canEqual(that: Any): Boolean = that.isInstanceOf[Function]
  override def equals(that: Any): Boolean = {
    that match {
      case other: Function => this.objectId == other.objectId
      case _ => false
    }
  }
  override def hashCode(): Int = objectId.hashCode
}

case class Parameter(name: VariableName, typeValue: Option[Value])



/* Arguments */

case class Arguments(positional: Seq[Value], named: Map[String, Value]) {
  def withoutSelf = Arguments(positional.drop(1), named)
  def withSelf(value: Value) = Arguments(value +: positional, named)

  def map(f: Value => Value) = Arguments(
    positional.map(f),
    named.view.mapValues(f).toMap
  )

  def forall(f: Value => Boolean) = positional.forall(f) && named.view.values.forall(f)

  override def toString = Unparser.unparse(ValueToAST.transformForInspection(this))
}

object Arguments {
  val empty: Arguments = Arguments(Seq.empty, Map.empty)
}
