package photon.core

import photon.{PureValue, RealValue, Value}
import photon.core.NativeValue._

object IntParams {
  val FirstParam: Parameter = Parameter(0, "first")
  val SecondParam: Parameter = Parameter(1, "second")
}

import IntParams._

object IntObject extends NativeObject(Map(
  "+" -> ScalaMethod(
    MethodOptions(Seq(FirstParam, SecondParam)),
    { (_, args, l) => PureValue.Int(args.getInt(FirstParam) + args.getInt(SecondParam), l) }
  ),

  "-" -> ScalaMethod(
    MethodOptions(Seq(FirstParam, SecondParam)),
    { (_, args, l) => PureValue.Int(args.getInt(FirstParam) - args.getInt(SecondParam), l) }
  ),

  "*" -> ScalaMethod(
    MethodOptions(Seq(FirstParam, SecondParam)),
    { (_, args, l) => PureValue.Int(args.getInt(FirstParam) * args.getInt(SecondParam), l) }
  ),

  "/" -> ScalaMethod(
    MethodOptions(Seq(FirstParam, SecondParam)),
    { (_, args, l) => PureValue.Float(args.getDouble(FirstParam) / args.getDouble(SecondParam), l) }
  ),

  "==" -> ScalaMethod(
    MethodOptions(Seq(FirstParam, SecondParam)),
    { (_, args, l) => PureValue.Boolean(args.getInt(FirstParam) == args.getInt(SecondParam), l) }
  ),

  "to_bool" -> ScalaMethod(
    MethodOptions(Seq()),
    { (_, _, l) => PureValue.Boolean(true, l) }
  )
))
