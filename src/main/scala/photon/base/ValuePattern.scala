package photon.base

import photon.core.$Object
import photon.core.objects.$MatchResult
import photon.core.operations.$Call
import photon.frontend._

case class PatternNames(defined: Set[VarName], unbound: Set[VarName])

case class MatchResult(bindings: Map[VarName, Value])

sealed trait ValuePattern {
  def names: PatternNames

  def applyTo(value: Value, env: Environment): Option[MatchResult]
  def toASTWithPreBoundNames(names: Map[VarName, String]): Pattern
}
object ValuePattern {
  def namesOfSequenceOfPatterns(patterns: Seq[ValuePattern]): PatternNames =
    patterns
      .foldLeft(PatternNames(defined = Set.empty, unbound = Set.empty)) {
        case names -> pattern =>
          val PatternNames(defined, unbound) = pattern.names

          PatternNames(
            defined = names.defined ++ defined,
            unbound = names.unbound ++ (unbound -- names.defined)
          )
      }

  case class Expected(expectedValue: Value, location: Option[Location]) extends ValuePattern {
    override def names = PatternNames(
      defined = Set.empty,
      unbound = expectedValue.unboundNames
    )

    override def applyTo(value: Value, env: Environment) =
    // TODO: Need `equals` between values here
    // TODO: DelayCall if this needs to be done runtime
      if (value == expectedValue.evaluate(env)) {
        Some(MatchResult(Map.empty))
      } else None

    override def toASTWithPreBoundNames(names: Map[VarName, String]): Pattern =
      Pattern.SpecificValue(expectedValue.toAST(names))
  }

  case class Binding(name: VarName, location: Option[Location]) extends ValuePattern {
    override def names = PatternNames(
      defined = Set(name),
      unbound = Set.empty
    )

    override def applyTo(value: Value, env: Environment) =
      Some(MatchResult(Map(name -> value)))

    override def toASTWithPreBoundNames(names: Map[VarName, String]): Pattern =
      Pattern.Binding(
        names.getOrElse(name, throw EvalError(s"Could not find string name for $name", location)),
        location
      )
  }

  case class Call(
    target: Value,
    name: String,
    args: ArgumentsWithoutSelf[ValuePattern],
    location: Option[Location]
  ) extends ValuePattern {
    override def names =
      args.argValues
        .foldLeft(PatternNames(defined = Set.empty, unbound = target.unboundNames)) {
          case names -> pattern =>
            val PatternNames(defined, unbound) = pattern.names

            PatternNames(
              defined = names.defined ++ defined,
              unbound = names.unbound ++ (unbound -- names.defined)
            )
        }

    override def applyTo(value: Value, env: Environment): Option[MatchResult] = {
      // TODO: Handle this run-time
      if (env.evalMode != EvalMode.CompileTimeOnly) {
        throw EvalError("Cannot evaluate call pattern unless it's compile-time (yet)", location)
      }

      // TODO: DelayCall if this needs to be done runtime
      val resultArgs = $Call(
        s"$name$$match",
        Arguments.positional(target, Seq(value)),
        location
      ).evaluate(env) match {
        // TODO: What if the stuff conforms to this type but is wrapped?
        // TODO: The [Value] part is not checked, do something with the warning
        case EvalResult($Object(args: ArgumentsWithoutSelf[Value], typ, _), _) if typ == $MatchResult => args
        case _ => ???
      }

      // TODO: What if args & resultArgs have fewer or more elements
      val (result, _) = args.positional
        .zip(resultArgs.positional)
        .foldLeft(Map.empty[VarName, Value] -> env.scope) {
          case (bindings -> scope) -> (pattern -> value) =>
            val argBindings = pattern.applyTo(value, Environment(scope, env.evalMode)) match {
              case Some(value) => value.bindings
              case None => return None
            }

            (bindings ++ argBindings) -> scope.newChild(argBindings.toSeq)
        }

      Some(MatchResult(result))
    }

    override def toASTWithPreBoundNames(names: Map[VarName, String]): Pattern =
      Pattern.Call(
        target.toAST(names),
        name,
        arguments = args.map(_.toASTWithPreBoundNames(names)),
        mayBeVarCall = false,
        location
      )
  }
}
