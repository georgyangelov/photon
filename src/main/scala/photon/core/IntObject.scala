package photon.core

import photon.{TypeObject, Value}
import photon.core.NativeValue._

object IntParams {
  val FirstParam: Parameter = Parameter(0, "first")
  val SecondParam: Parameter = Parameter(1, "second")
}

import IntParams._

object IntObject extends NativeObject(Map(
  "+" -> ScalaMethod(
    MethodOptions(Seq(FirstParam, SecondParam)),
    { (_, args, l) => Value.Int(args.getInt(FirstParam) + args.getInt(SecondParam), l, Some(TypeObject.Native(IntRoot))) }
  ),

  "-" -> ScalaMethod(
    MethodOptions(Seq(FirstParam, SecondParam)),
    { (_, args, l) => Value.Int(args.getInt(FirstParam) - args.getInt(SecondParam), l, Some(TypeObject.Native(IntRoot))) }
  ),

  "*" -> ScalaMethod(
    MethodOptions(Seq(FirstParam, SecondParam)),
    { (_, args, l) => Value.Int(args.getInt(FirstParam) * args.getInt(SecondParam), l, Some(TypeObject.Native(IntRoot))) }
  ),

  "/" -> ScalaMethod(
    MethodOptions(Seq(FirstParam, SecondParam)),
    { (_, args, l) => Value.Float(args.getDouble(FirstParam) / args.getDouble(SecondParam), l) }
  ),

  "to_bool" -> ScalaMethod(
    MethodOptions(Seq()),
    { (_, _, l) => Value.Boolean(true, l) }
  )
))
