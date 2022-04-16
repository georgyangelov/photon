package photon.core

import photon.core.operations.CallValue
import photon.interpreter.EvalError
import photon.{Arguments, DefaultMethod, EValue, Location, MethodType}
import photon.lib.ScalaExtensions._
import photon.ArgumentExtensions._

object ListType extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "of" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) = {
        val firstType = args.positional.head.evalType.getOrElse(args.positional.head.typ)

        MethodType.of(
          args.positional.zipWithIndex.map { case (_, index) => s"item${index + 1}" -> firstType },
          List
        )
      }

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        if (args.named.nonEmpty) {
          throw EvalError("Cannot call List.of with named arguments", location)
        }

        ListValue(args.positional.map(_.evaluated), location)
      }
    },

    "empty" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(Seq.empty, List)

      override def run(args: Arguments[EValue], location: Option[Location]) =
        ListValue(Seq.empty, location)
    }
  )
}

object List extends StandardType {
  override val typ = ListType
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map(
    "size" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(Seq.empty, Int)

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.selfEval[ListValue]

        IntValue(self.values.length, location)
      }
    },

    "get" -> new DefaultMethod {
      // TODO: Actual type here
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(Seq.empty, Int)

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.selfEval[ListValue]
        val index = args.getEval[IntValue](1, "index").value

        self.values(index)
      }
    }
  )
}

case class ListValue(values: Seq[EValue], location: Option[Location]) extends EValue {
  override val typ = List
  override def unboundNames = values.map(_.unboundNames).unionSets
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def toUValue(core: Core) = CallValue("of", Arguments.positional(List, values), location).toUValue(core)
  override def evaluate = this
  override def finalEval = ListValue(values.map(_.finalEval), location)
}
