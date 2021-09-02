package photon.core

import photon.{Arguments, Location, PureValue, RealValue, Value}
import photon.core.Conversions._

private object BoolObjectArgs {
  val FirstParam = Parameter(0, "first")
  val SecondParam = Parameter(1, "second")

  val IfCondition = Parameter(0, "condition")
  val IfTrueBranch = Parameter(1, "ifTrue")
  val IfFalseBranch = Parameter(2, "ifFalse")
}

import BoolObjectArgs._

object BoolObject extends NativeObject(Map(
  "!" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Boolean(!args.getBool(FirstParam), location)
  },

  "not" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Boolean(!args.getBool(FirstParam), location)
  },

  // TODO: Short-circuiting
  "and" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Boolean(args.getBool(FirstParam) && args.getBool(SecondParam), location)
  },

  // TODO: Short-circuiting
  "or" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Boolean(args.getBool(FirstParam) || args.getBool(SecondParam), location)
  },

  "toBool" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Boolean(args.getBool(FirstParam), location)
  },

  "ifElse" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
      val lambda = if (args.getBool(IfCondition)) {
        args.getFunction(IfTrueBranch)
      } else {
        args.getFunction(IfFalseBranch)
      }

      Core.nativeValueFor(lambda)
        .callOrThrowError(context, "call", Arguments(Seq.empty, Map.empty), location)
    }
  }
))
