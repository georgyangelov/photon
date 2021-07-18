package photon

import photon.core.NativeValue
import photon.frontend.{Unparser, ValueToAST}

import java.util.concurrent.atomic.AtomicLong
import scala.collection.Map

sealed abstract class Value {
  def location: Option[Location]
  def typeObject: Option[TypeObject] = None

  override def toString: String = ValueToAST.transformForInspection(this).toString

  val unboundNames: Set[VariableName]
  def isFullyKnown: Boolean = isFullyKnown(Set.empty)

  protected def isFullyKnown(alreadyKnownBoundFunctions: Set[photon.BoundFunction]): Boolean

  def isOperation: Boolean = {
    this match {
      case Value.Operation(_, _) => true
      case _ => false
    }
  }

  def isUnknown: Boolean = {
    this match {
      case Value.Unknown(_) => true
      case _ => false
    }
  }

  def isNothing: Boolean = {
    this match {
      case Value.Nothing(_) => true
      case _ => false
    }
  }
}

object Value {
  case class Unknown(location: Option[Location]) extends Value {
    override val unboundNames = Set.empty
    override def isFullyKnown(alreadyKnownBoundFunctions: Set[photon.BoundFunction]) = false
  }

  case class Nothing(location: Option[Location]) extends Value {
    override val unboundNames = Set.empty
    override def isFullyKnown(alreadyKnownBoundFunctions: Set[photon.BoundFunction]) = true
  }

  case class Boolean(value: scala.Boolean, location: Option[Location]) extends Value {
    override val unboundNames = Set.empty
    override def isFullyKnown(alreadyKnownBoundFunctions: Set[photon.BoundFunction]) = true
  }

  case class Int(value: scala.Int, location: Option[Location], override val typeObject: Option[TypeObject]) extends Value {
    override val unboundNames = Set.empty
    override def isFullyKnown(alreadyKnownBoundFunctions: Set[photon.BoundFunction]) = true
  }

  case class Float(value: scala.Double, location: Option[Location]) extends Value {
    override val unboundNames = Set.empty
    override def isFullyKnown(alreadyKnownBoundFunctions: Set[photon.BoundFunction]) = true
  }

  case class String(value: java.lang.String, location: Option[Location]) extends Value {
    override val unboundNames = Set.empty
    override def isFullyKnown(alreadyKnownBoundFunctions: Set[photon.BoundFunction]) = true
  }

  case class Native(native: NativeValue, location: Option[Location]) extends Value {
    override val unboundNames = Set.empty
    override def isFullyKnown(alreadyKnownBoundFunctions: Set[photon.BoundFunction]) = native.isFullyEvaluated
  }

  case class Struct(struct: photon.Struct, location: Option[Location]) extends Value {
    lazy override val unboundNames = struct.props.view.values.map(_.unboundNames).fold(Set.empty) { case (a, b) => a ++ b }

    override def isFullyKnown(alreadyKnownBoundFunctions: Set[photon.BoundFunction]) = {
      struct.props.view.values.forall(_.isFullyKnown(alreadyKnownBoundFunctions))
    }
  }

  case class BoundFunction(boundFn: photon.BoundFunction, location: Option[Location]) extends Value {
    override val unboundNames = boundFn.fn.unboundNames

    override def isFullyKnown(alreadyKnownBoundFunctions: Set[photon.BoundFunction]): scala.Boolean = {
      if (alreadyKnownBoundFunctions.contains(boundFn)) {
        return true
      }

      // TODO: This is not exactly correct, because we can fully evaluate a function call even if one
      //       of its parameters cannot be called at this time:
      //
      //           unknown = () { ... }.runTimeOnly
      //           identity = (a) { a }
      //           identity(unknown)()
      //
      //           # Should result in
      //           unknown()
      if (!boundFn.traits.contains(FunctionTrait.CompileTime)) {
        return false
      }

      unboundNames.forall { name =>
        boundFn.scope.find(name) match {
          case Some(Variable(_, value)) => value.isFullyKnown(alreadyKnownBoundFunctions + boundFn)
          case None => throw EvalError(s"Cannot find name $name during isFullyKnown check", location)
        }
      }
    }
  }

  case class Operation(operation: photon.Operation, location: Option[Location]) extends Value {
    lazy override val unboundNames = operation.unboundNames
    override def isFullyKnown(alreadyKnownBoundFunctions: Set[photon.BoundFunction]) = false
  }
}

sealed abstract class TypeObject

object TypeObject {
  case class Native(native: NativeValue) extends TypeObject
  case class Struct(struct: photon.Struct) extends TypeObject
}

sealed abstract class FunctionTrait

object FunctionTrait {
  case object Partial extends FunctionTrait
  case object CompileTime extends FunctionTrait
  case object Runtime extends FunctionTrait
  case object Pure extends FunctionTrait
}

case class ObjectId(id: Long) extends AnyVal

object ObjectId {
  val idCounter = new AtomicLong(1)

  def apply(): ObjectId = new ObjectId(idCounter.getAndIncrement())
}

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

// TODO: Do we need this at all?
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
      variables.map { variable => (variable.name, variable) }.toMap
    )
  }
}

case class Scope(parent: Option[Scope], variables: Map[VariableName, Variable]) {
  def newChild(variables: Seq[Variable]): Scope = {
    Scope(
      Some(this),
      variables.map { variable => (variable.name, variable) }.toMap
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

case class Struct(props: Map[String, Value])

case class BoundFunction(
  fn: Function,
  scope: Scope,

  traits: Set[FunctionTrait]
)

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

sealed abstract class Operation {
  val unboundNames: Set[VariableName]
}

object Operation {
  case class Block(values: Seq[Value]) extends Operation {
    lazy override val unboundNames = values.map(_.unboundNames).fold(Set.empty) { case (a, b) => a ++ b }
  }

  case class Let(name: VariableName, value: Value, block: Block) extends Operation {
    lazy override val unboundNames = (value.unboundNames ++ block.unboundNames) - name
  }
  case class Reference(name: VariableName) extends Operation {
    override val unboundNames = Set(name)
  }

  case class Function(fn: photon.Function) extends Operation {
    override val unboundNames = fn.unboundNames
  }
  case class Call(target: Value, name: String, arguments: Arguments) extends Operation {
    lazy override val unboundNames =
      target.unboundNames ++
        arguments.positional.map(_.unboundNames).fold(Set.empty) { case (a, b) => a ++ b } ++
        arguments.named.view.values.map(_.unboundNames).fold(Set.empty) { case (a, b) => a ++ b }
  }
}

case class Parameter(name: VariableName, typeValue: Option[Value])

case class Arguments(positional: Seq[Value], named: Map[String, Value]) {
  def withoutSelf = Arguments(positional.drop(1), named)

  def map(f: Value => Value) = Arguments(
    positional.map(f),
    named.view.mapValues(f).toMap
  )

  override def toString = Unparser.unparse(ValueToAST.transformForInspection(this))
}

object Arguments {
  val empty: Arguments = Arguments(Seq.empty, Map.empty)
}

case class CallStackEntry(methodId: ObjectId, name: String, location: Option[Location])
