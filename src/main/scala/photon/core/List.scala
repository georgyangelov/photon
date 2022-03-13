package photon.core

import photon.core.operations.CallValue
import photon.interpreter.EvalError
import photon.{Arguments, EValue, Location}
import photon.lib.ScalaExtensions._

object ListType extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "of" -> new Method {
      override val runMode = MethodRunMode.Default
      override def typeCheck(args: Arguments[EValue]) = List
      override def call(args: Arguments[EValue], location: Option[Location]) = {
        if (args.named.nonEmpty) {
          throw EvalError("Cannot call List.of with named arguments", location)
        }

        ListValue(args.positional, location)
      }
    },

    "empty" -> new Method {
      override val runMode = MethodRunMode.Default
      override def typeCheck(args: Arguments[EValue]) = List
      override def call(args: Arguments[EValue], location: Option[Location]) =
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
    "size" -> new Method {
      override val runMode = MethodRunMode.Default

      // TODO: Actually type check arguments
      override def typeCheck(args: Arguments[EValue]) = Int

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[ListValue]

        IntValue(self.values.length, location)
      }
    },

    "get" -> new Method {
      override val runMode = MethodRunMode.Default

      // TODO: Actual type here
      override def typeCheck(args: Arguments[EValue]) = Int

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[ListValue]
        val index = args.positional.head.evalAssert[IntValue].value

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
