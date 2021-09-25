package photon.core
import photon.interpreter.EvalError
import photon.{Arguments, Location, New, PureValue, RealValue, Value}
import photon.core.Conversions._

object ListType extends New.TypeObject {
  val methods = Map(
    "of" -> new New.StandardMethod {
      override val name = "of"
      // TODO: Varargs
      override val arguments = Seq.empty
      override val returns = ListType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        if (args.named.nonEmpty) {
          throw EvalError("Cannot call List.of with named arguments", location)
        }

        PureValue.Native(List(args.positional), location)
      }
    },

    "empty" -> new New.StandardMethod {
      override val name = "empty"
      override val arguments = Seq.empty
      override val returns = ListType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
        PureValue.Native(List(Seq.empty), location)
    }
  )

  val instanceMethods = Map(
    "size" -> new New.StandardMethod {
      override val name = "size"
      override val arguments = Seq.empty
      override val returns = IntType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
        PureValue.Int(args.getNativeSelf[List].values.length, location)
    },

//    TODO: Implement these
//    "get" -> ???,
//    "map" -> ???,
//    "reduce" -> ???
  )
}

case class List(values: Seq[Value]) extends New.NativeObject(ListType)


//object ListRoot extends NativeObject(CoreTypes.Type, Map(
//  "of" -> new {} with PureMethod {
//    // TODO: Support partial evaluation
//    // TODO: This needs to be partial only? So that any values that are references are kept as
//    //       references if needed to be converted to ASTValue
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
//      if (args.named.nonEmpty) {
//        throw EvalError("Cannot call List.of with named arguments", location)
//      }
//
//      PureValue.Native(List(args.positional), location)
//    }
//  },
//
//  "empty" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.Native(List(Seq.empty), location)
//  }
//)) with Type {
//  override def methodTypes = Seq.empty
//}
//
//// TODO: Make this a persistent structure
//case class List(values: Seq[Value]) extends NativeObject(CoreTypes.List, Map(
//  "size" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.Int(values.length, location)
//  },
//
//  "get" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      values(args.getInt(Parameter(1, "index")))
//  },
//
//  // TODO: Make it work in partial context, executing only on some of the elements
//  "map" -> new {} with PureMethod {
//    // This will only get called when all of the values inside are fully known
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
//      val fn = args.getFunction(Parameter(1, "fn"))
//      val fnCall = Core.nativeValueFor(fn)
//        .method("call", location)
//        .getOrElse { throw EvalError("Cannot invoke 'call' on the fn passed", location) }
//
//      val list = List(values.map { value => {
//        // Using this for the identity fn
//        val realValue = value.realValue
//          .getOrElse { throw EvalError("List#map called on non-fully known list", location) }
//
//        fnCall.call(
//          context,
//          Arguments.positional(Seq(realValue)),
//          location
//        )
//      }})
//
//      PureValue.Native(list, location)
//    }
//  },
//
//  "reduce" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
//      val initialValue = args.get(Parameter(1, "initialValue"))
//      val fn = args.getFunction(Parameter(2, "fn"))
//
//      val fnCall = Core.nativeValueFor(fn)
//        .method("call", location)
//        .getOrElse { throw EvalError("Cannot invoke 'call' on the fn passed", location) }
//
//      val initial = initialValue.realValue
//        .getOrElse { throw EvalError("List#reduce called with unknown initial value", location) }
//
//      val result = values.foldLeft[Value](initial) { (accumulator, value) =>
//        // TODO: Support partial evaluation and remove these
//        val realAccumulator = accumulator.realValue
//          .getOrElse { throw EvalError("Accumulator is not real", location) }
//
//        val realValue = value.realValue
//          .getOrElse { throw EvalError("List#reduce called on non-fully known list", location) }
//
//        fnCall.call(
//          context,
//          Arguments.positional(Seq(realAccumulator, realValue)),
//          location
//        )
//      }
//
//      result
//    }
//  }
//))
