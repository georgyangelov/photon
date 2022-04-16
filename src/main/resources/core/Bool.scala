package photon.core

import photon.interpreter.EvalError
import photon.{Arguments, DefaultMethod, EValue, Location, MethodType, ULiteral}
import photon.ArgumentExtensions._
import photon.core.operations.{CallValue, FunctionT}

object BoolType extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override def unboundNames = Set.empty
  override val methods = Map.empty

  override def toUValue(core: Core) = inconvertible
}

object Bool extends StandardType {
  override val typ = BoolType
  override val location = None
  override def unboundNames = Set.empty

  override def toUValue(core: Core) = core.referenceTo(Bool, location)

  override val methods = Map(
    // TODO: Short-circuiting
    "and" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType(
          Seq("other" -> Bool),
          Bool
        )

      override def run(args: Arguments[EValue], location: Option[Location]): EValue = {
        val self = args.selfEval[BoolValue]
        val other = args.getEval[BoolValue](1, "other")

        BoolValue(self.value && other.value, location)
      }
     },

    // TODO: Short-circuiting
    "or" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType(
          Seq("other" -> Bool),
          Bool
        )

      override def run(args: Arguments[EValue], location: Option[Location]): EValue = {
        val self = args.selfEval[BoolValue]
        val other = args.getEval[BoolValue](1, "other")

        BoolValue(self.value || other.value, location)
      }
    },

    "ifElse" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) = {
        // TODO: Adequate error messages for evalAssert
        val thenFn = args.positional.head
        val thenFnType = thenFn.evalType.getOrElse(thenFn.typ).assertSpecificType[FunctionT]
        val thenReturnType = thenFnType.returnType.assertType

        val elseFn = args.positional.head
        val elseFnType = elseFn.evalType.getOrElse(elseFn.typ).assertSpecificType[FunctionT]
        val elseReturnType = elseFnType.returnType.assertType

        // TODO: Union interface?
        if (thenReturnType != elseReturnType) {
          throw EvalError("Cannot have different types returned from if", location)
        }

        MethodType(
          // TODO: Verify no arguments
          Seq(
            "then" -> thenFn,
            "else" -> elseFn
          ),
          thenReturnType
        )
      }

      // TODO: Extract a common type, or check to see if the two types are equal
//      override def typeCheck(arguments: Arguments[EValue]) = {
//        val first = arguments.positional.head
//
//        first.evalType.getOrElse(first.typ)
//      }

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val condition = args.selfEval[BoolValue].value

        val fnToCall = if (condition) {
          args.positional.head
        } else {
          args.positional(1)
        }

        CallValue("call", Arguments.empty(fnToCall), location).evaluated

//        fnToCall.evalType.getOrElse(fnToCall.typ)
//          .method("call")
//          .getOrElse { throw EvalError("Functions given to ifElse must be callable", location) }
//          .call(Arguments.empty(fnToCall), location)
      }
    }
  )
}

case class BoolValue(value: scala.Boolean, location: Option[Location]) extends EValue {
  override val typ = Bool
  override def unboundNames = Set.empty
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def evaluate = this
  override def finalEval = this
  override def toUValue(core: Core) = ULiteral.Boolean(value, location)
}
