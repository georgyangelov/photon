package photon.core
import photon.{FunctionTrait, PureValue, RealValue}

object TypeRoot extends NativeObject(CoreTypes.Type, Map(
  "$methodTypes" -> TypeHelpers.methodTypes(Seq(
    FunctionType("$methodTypes", Seq.empty, CoreTypes.List)
  ))
))

object NothingRoot extends NativeObject(CoreTypes.Type, Map.empty)

case class FunctionType(
  name: String,
  argTypes: Seq[RealValue],
  returnType: RealValue
) extends NativeObject(CoreTypes.Type, Map(
  "$methodTypes" -> TypeHelpers.methodTypes(Seq(
    FunctionType("call", argTypes, returnType)
  ))
))

object TypeHelpers {
  def methodTypes(methods: Seq[FunctionType]) =
    GetterMethod(
      PureValue.Native(List(
        methods.map(PureValue.Native(_, None))
      ), None),
      Set(FunctionTrait.CompileTime, FunctionTrait.Partial)
    )
}
