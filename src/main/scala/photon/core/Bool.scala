package photon.core

import photon.{AnyType, ArgumentType, Arguments, Location, New, PureValue, RealValue, TypeType}
import photon.core.Conversions._
import photon.interpreter.{CallContext, EvalError}

private object BoolObjectArgs {
  val Self = Parameter(0, "self")
  val Other = Parameter(1, "other")

  val IfCondition = Parameter(0, "condition")
  val IfTrueBranch = Parameter(1, "ifTrue")
  val IfFalseBranch = Parameter(2, "ifFalse")
}

import BoolObjectArgs._

//object T {
//  val IfTrue = new TypeParam.TypeVar
//  val IfFalse = new TypeParam.TypeVar
//}

object BoolTypeType extends New.TypeObject {
  override val typeObject = TypeType
  override val instanceMethods = Map.empty
}

object BoolType extends New.TypeObject {
  override val typeObject = BoolTypeType

  override val instanceMethods = Map(
    "!" -> new New.StandardMethod {
      override val name = "!"
      override val arguments = Seq.empty
      override val returns = BoolType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
        PureValue.Boolean(!args.getBool(Self), location)
    },

    "toBool" -> new New.StandardMethod {
      override val name = "toBool"
      override val arguments = Seq.empty
      override val returns = BoolType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
        PureValue.Boolean(args.getBool(Self), location)
    },

    /**
     * def ifElse(ifTrue: Fn(let T1), ifFalse: Fn(let T2)): Union(T1, T2)
     */
    "ifElse" -> new New.StandardMethod {
      override val name = "ifElse"
      override val arguments = Seq(
        ArgumentType("ifTrue", AnyType),
        ArgumentType("ifFalse", AnyType)
      )
      override val returns = AnyType
//      override val arguments = Seq(
//        ArgumentType("ifTrue", FnType(Seq.empty, T.IfTrue).toTypeParam),
//        ArgumentType("ifFalse", FnType(Seq.empty, T.IfFalse).toTypeParam)
//      )
//      override val returns = TypeParam.Union(Seq(T.IfTrue, T.IfFalse))

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val lambda = if (args.getBool(IfCondition)) {
          args.getFunction(IfTrueBranch)
        } else {
          args.getFunction(IfFalseBranch)
        }

        // TODO: Make this easier to do
        lambda.typeObject.getOrElse {
          throw EvalError("Could not call lambda which does not have a type", location)
        }.instanceMethod("call").getOrElse {
          throw EvalError("Lambda does not have a call method", location)
        }.call(context, Arguments(None, Seq.empty, Map.empty), location)
      }
    }
  )
}

//object BoolObject extends NativeObject(Map(
//  "!" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.Boolean(!args.getBool(FirstParam), location)
//  },
//
//  "not" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.Boolean(!args.getBool(FirstParam), location)
//  },
//
//  // TODO: Short-circuiting
//  "and" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.Boolean(args.getBool(FirstParam) && args.getBool(SecondParam), location)
//  },
//
//  // TODO: Short-circuiting
//  "or" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.Boolean(args.getBool(FirstParam) || args.getBool(SecondParam), location)
//  },
//
//  "toBool" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.Boolean(args.getBool(FirstParam), location)
//  },
//
//  "ifElse" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
//      val lambda = if (args.getBool(IfCondition)) {
//        args.getFunction(IfTrueBranch)
//      } else {
//        args.getFunction(IfFalseBranch)
//      }
//
//      Core.nativeValueFor(lambda)
//        .callOrThrowError(context, "call", Arguments(None, Seq.empty, Map.empty), location)
//    }
//  }
//))
