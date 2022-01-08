package photon.core

import photon.interpreter.EvalError
import photon.{Arguments, EValue, Location, ULiteral}

object BoolType extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override val methods = Map.empty

  override def toUValue(core: Core) = inconvertible
}

object Bool extends StandardType {
  override val typ = BoolType
  override val location = None

  override def toUValue(core: Core) = core.referenceTo(Bool, location)

  override val methods = Map(
    // TODO: Short-circuiting
    "and" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(arguments: Arguments[EValue]) = Bool

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[BoolValue]
        val other = args.get(1, "other").evalAssert[BoolValue]

        BoolValue(self.value && other.value, location)
      }
    },

    "or" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(arguments: Arguments[EValue]) = Bool

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[BoolValue]
        val other = args.get(1, "other").evalAssert[BoolValue]

        BoolValue(self.value || other.value, location)
      }
    },

    "ifElse" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Extract a common type, or check to see if the two types are equal
      override def typeCheck(arguments: Arguments[EValue]) = {
        val first = arguments.positional.head

        first.evalType.getOrElse(first.typ)
      }

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        // TODO: Make this a normal assert so that it can be partially evaluated
        val condition = args.self.evalAssert[BoolValue].value

        val fnToCall = if (condition) {
          args.positional.head
        } else {
          args.positional(1)
        }

//        CallValue("call", Arguments.empty(fnToCall), location)

        // TODO: Can this be `evalType`?
        fnToCall.evaluated.typ
          .method("call")
          .getOrElse { throw EvalError("Functions given to ifElse must be callable", location) }
          .call(Arguments.empty(fnToCall), location)
      }
    }
  )
}

case class BoolValue(value: scala.Boolean, location: Option[Location]) extends EValue {
  override val typ = Bool
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def evaluate = this
  override def toUValue(core: Core) = ULiteral.Boolean(value, location)
}
