package photon.core

import photon.{Arguments, PureValue}
import photon.core.Conversions._

private object BoolObjectArgs {
  val FirstParam: Parameter = Parameter(0, "first")
  val SecondParam: Parameter = Parameter(1, "second")

  val IfCondition: Parameter = Parameter(0, "condition")
  val IfTrueBranch: Parameter = Parameter(1, "ifTrue")
  val IfFalseBranch: Parameter = Parameter(2, "ifFalse")
}

import BoolObjectArgs._

object BoolObject extends NativeObject(Map(
  "!" -> ScalaMethod(
    MethodOptions(Seq(FirstParam)),
    { (_, args, l) => PureValue.Boolean(!args.getBool(FirstParam), l) }
  ),

  "not" -> ScalaMethod(
    MethodOptions(Seq(FirstParam)),
    { (_, args, l) => PureValue.Boolean(!args.getBool(FirstParam), l) }
  ),

  // TODO: Short-circuiting
  "and" -> ScalaMethod(
    MethodOptions(Seq(FirstParam, SecondParam)),
    { (_, args, l) => PureValue.Boolean(args.getBool(FirstParam) && args.getBool(SecondParam), l) }
  ),

  "or" -> ScalaMethod(
    MethodOptions(Seq(FirstParam, SecondParam)),
    { (_, args, l) => PureValue.Boolean(args.getBool(FirstParam) || args.getBool(SecondParam), l) }
  ),

  "to_bool" -> ScalaMethod(
    MethodOptions(Seq(FirstParam)),
    { (_, args, l) => PureValue.Boolean(args.getBool(FirstParam), l) }
  ),

  "if_else" -> ScalaMethod(
    MethodOptions(Seq(IfCondition, IfTrueBranch, IfFalseBranch)),
    { (c, args, l) =>
      val lambda = if (args.getBool(IfCondition)) {
        args.getFunction(IfTrueBranch)
      } else {
        args.getFunction(IfFalseBranch)
      }

//      Logger("BoolObject").debug(s"Running $lambda, scope: ${lambda.asLambda.scope}")

      Core.nativeValueFor(lambda).callOrThrowError(c, "call", Arguments(Seq(lambda), Map.empty), l)
    }
  )
))
