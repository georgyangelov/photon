package photon.core
import photon.{Arguments, Location, RealValue}

object TypeRoot extends NativeObject(CoreTypes.Type, Map(

))

object NothingRoot extends NativeObject(CoreTypes.Type, Map.empty)

case class FunctionType(
  argTypes: Seq[RealValue],
  returnType: RealValue
) extends NativeObject(CoreTypes.Type, Map(

))
