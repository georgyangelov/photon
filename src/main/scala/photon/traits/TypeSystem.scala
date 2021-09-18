package photon.traits

import photon.core.{CallContext, Core}
import photon.interpreter.EvalError
import photon.{Arguments, BoundValue, Function, Operation, Parameter, PureValue, RealValue, Value, VariableName}

import scala.collection.mutable

sealed abstract class NativeType
object NativeType {
  case object Unknown extends NativeType
  case object Boolean extends NativeType
  case object Int extends NativeType
  case object Float extends NativeType
  case object String extends NativeType

  case class Function(arguments: Seq[NativeType], returns: NativeType) extends NativeType
}

class TypeSystem {
  private val valTypes = new mutable.WeakHashMap[Value, NativeType]
  private val varTypes = new mutable.WeakHashMap[VariableName, NativeType]

  def visit(context: CallContext, value: Value): Unit = {
    NativeType.Int.equals()

    valTypes.get(value) match {
      case Some(_) => return
    }

    val valType = value match {
      case PureValue.Nothing(location) => NativeType.Unknown
      case PureValue.Boolean(value, location) => NativeType.Boolean
      case PureValue.Int(value, location) => NativeType.Int
      case PureValue.Float(value, location) => NativeType.Float
      case PureValue.String(value, location) => NativeType.String
      case PureValue.Native(native, location) => NativeType.Unknown
      case BoundValue.Function(fn, traits, scope, location) => NativeType.Unknown

      case BoundValue.Object(values, scope, location) => NativeType.Unknown
      case Operation.Block(values, realValue, location) => NativeType.Unknown
      case Operation.Let(name, letValue, block, realValue, location) => NativeType.Unknown
      case Operation.Reference(name, realValue, location) => NativeType.Unknown
      case Operation.Function(fn, realValue, location) => NativeType.Unknown
      case Operation.Call(target, name, arguments, realValue, location) => NativeType.Unknown
    }

    valTypes.put(value, valType)
  }

  private def functionType(context: CallContext, fn: Function): NativeType.Function = {
    val argTypes = fn.params.map(parameterType(context, _))
    val returnType = fn

//    NativeType.Function()
    ???
  }

  private def parameterType(context: CallContext, param: Parameter) = {
    val typeValue = param.typeValue
      .flatMap(_.realValue)
      // TODO: Use TypeError instead of EvalError
      .getOrElse { throw EvalError("Cannot typecheck parameter with no type", param.location) }

    val nativeType = Core.nativeValueFor(typeValue)
      .callOrThrowError(
        context,
        "$nativeType",
        Arguments.empty[RealValue],
        typeValue.location
      )
      .realValue
      .getOrElse { throw EvalError("Type value is not real", param.location) }

    ???
  }
}
