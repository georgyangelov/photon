package photon.base

import scala.reflect.ClassTag

case class ArgumentsWithoutSelf[+T](positional: Seq[T], named: Map[String, T]) {
  def argValues = positional ++ named.values
}
object ArgumentsWithoutSelf {
  def empty[T]: ArgumentsWithoutSelf[T] = ArgumentsWithoutSelf(Seq.empty, Map.empty)

  def positional[T](values: Seq[T]) =
    ArgumentsWithoutSelf[T](
      positional = values,
      named = Map.empty
    )

  def named[T](values: Map[String, T]) =
    ArgumentsWithoutSelf[T](
      positional = Seq.empty,
      named = values
    )
}

case class Arguments[+T](
  self: T,
  positional: Seq[T],
  named: Map[String, T]
) {
  def values = positional ++ named.values ++ Seq(self)
  def argValues = positional ++ named.values

  def count = positional.size + named.size

  def changeSelf[R >: T](value: R) = Arguments[R](value, positional, named)

  def map[R](f: T => R) = Arguments(
    f(self),
    positional.map(f),
    named.view.mapValues(f).toMap
  )

  def mapWithNames[R >: T](names: Seq[String])(f: (String, T) => R) = {
    val positionalNames = names.filterNot(named.contains)

    Arguments(
      self,
      positionalNames.zip(positional).map { case (name, value) => f(name, value) },
      named.map { case (name, value) => name -> f(name, value) }
    )
  }

  def forall(f: T => Boolean) = f(self) && positional.forall(f) && named.view.values.forall(f)

  def get(index: Int, name: String): T = {
    if (index == 0) {
      self
    } else if (index - 1 < positional.size) {
      positional(index - 1)
    } else {
      named.get(name) match {
        case Some(value) => value
        case None => throw EvalError(s"Missing argument $name (at index $index)", None)
      }
    }
  }

//  def toPositional(params: Seq[EParameter]) = {
//    // TODO: Optional parameters?
//    val namedParamsInOrder = params.drop(positional.length)
//      .map { param => named.get(param.name) }
//
//    Arguments.positional(
//      self,
//      positional ++ namedParamsInOrder
//    )
//  }

  def matchWithNamesUnordered(paramNames: Seq[String]): Seq[(String, T)] = {
    val positionalNames = paramNames.filterNot(named.contains)

    val positionalParams = positionalNames.zip(positional)
    val namedParams = named.toSeq

    positionalParams ++ namedParams
  }

  def matchWith[T1](params: Seq[(String, T1)]): Seq[(String, (T, T1))] = {
    val (positionalParams, namedParams) = params.partition { case (name, _) => named.contains(name) }

    val positionalWithT1 = positionalParams
      .zip(positional)
      .map { case ((name, t1), value) => (name, (value, t1)) }

    val namedWithT1 = namedParams
      .map { case (name, t1) => (name, (named(name), t1)) }

    positionalWithT1 ++ namedWithT1
  }

//  def matchInOrder(paramNames: Seq[String]): Seq[(String, EValue)] = {
//
//
//    paramNames.map { name =>  }
//
//
//    val positionalNames = paramNames.filterNot(named.contains)
//
//
//    val positionalParams = positionalNames.zip(positional)
//    val namedParams = paramNames named.toSeq
//
//  }

  //  override def toString = Unparser.unparse(
  //    ValueToAST.transformForInspection(
  //      this.asInstanceOf[Arguments[Value]]
  //    )
  //  )
}

//object ArgumentExtensions {
//  implicit class GetEval(self: Arguments[EValue]) {
//    def selfEval[T <: EValue](implicit tag: ClassTag[T]) = getEval[T](0, "self")(tag)
//
//    def getEval[T <: EValue](index: Int, name: String)(implicit tag: ClassTag[T]) =
//      self.get(index, name).evaluated match {
//        case value: T => value
//        case UnknownValue(_, _) => throw DelayCall
//        case value =>
//          if (value.isOperation) {
//            // TODO: We probably don't need this if all values respect the EvalMode
//            EValue.context.evalMode match {
//              case EvalMode.RunTime => throw EvalError(s"Cannot evaluate $self even runtime", None)
//              case EvalMode.CompileTimeOnly => throw EvalError(s"Cannot evaluate $self compile-time", None)
//              case EvalMode.Partial |
//                   EvalMode.PartialRunTimeOnly |
//                   EvalMode.PartialPreferRunTime => throw DelayCall
//            }
//          } else {
//            // TODO: Do I need this? Won't the typechecks handle this already?
//            throw EvalError(s"Invalid value type $value, expected a $tag value", None)
//          }
//      }
//
//    def selfEvalInlined[T <: EValue](implicit tag: ClassTag[T]) = getEvalInlined[T](0, "self")(tag)
//
//    def getEvalInlined[T <: EValue](index: Int, name: String)(implicit tag: ClassTag[T]) =
//      self.get(index, name).evaluated.inlinedValue match {
//        case value: T => value
//        case UnknownValue(_, _) => throw DelayCall
//        case value =>
//          if (value.isOperation) {
//            EValue.context.evalMode match {
//              case EvalMode.RunTime => throw EvalError(s"Cannot evaluate $self even runtime", None)
//              case EvalMode.CompileTimeOnly => throw EvalError(s"Cannot evaluate $self compile-time", None)
//              case EvalMode.Partial |
//                   EvalMode.PartialRunTimeOnly |
//                   EvalMode.PartialPreferRunTime => throw DelayCall
//            }
//          } else {
//            // TODO: Do I need this? Won't the typechecks handle this already?
//            throw EvalError(s"Invalid value type $value, expected a $tag value", None)
//          }
//      }
//  }
//}

object Arguments {
  def empty[T](self: T): Arguments[T] = Arguments(self, Seq.empty, Map.empty)

  def positional[T](self: T, values: Seq[T]) = Arguments[T](
    self = self,
    positional = values,
    named = Map.empty
  )

  def named[T](self: T, values: Map[String, T]) = Arguments[T](
    self = self,
    positional = Seq.empty,
    named = values
  )
}