package photon.base

trait EValue
trait Type

object MethodType {
  def of(args: Seq[(String, Pattern)], returnType: EValue) = MethodType(args, returnType)
}

case class MethodType(
  argTypes: Seq[(String, Pattern)],
  returnType: EValue
) {
  def specialize(args: Arguments[EValue]): SpecializedMethodType = ???
}

case class SpecializedMethodType(
  bindings: Seq[(String, EValue)],
  argTypes: Seq[(String, Type)],
  returnType: Type
)