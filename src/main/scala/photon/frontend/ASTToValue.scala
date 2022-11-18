package photon.frontend

import photon.base._
import photon.core._
import photon.core.objects._
import photon.core.operations._
import photon.frontend.ASTValue.Pattern
import photon.lib.ScalaExtensions._

object ASTToValue {
  def transform(ast: ASTValue, scope: StaticScope): Value = ast match {
    case ASTValue.Boolean(value, location) => $Object(value, $Boolean, location)
    case ASTValue.Int(value, location) => $Object(value, $Int, location)
    case ASTValue.Float(value, location) => ???
    case ASTValue.String(value, location) => $Object(value, $String, location)
    case ASTValue.Block(values, location) => $Block(values.map(transform(_, scope)), location)

    case ASTValue.Function(params, body, returnType, location) =>
      val (paramScope, fnParamsIterable) = params.mapWithRollingContext(scope) {
        case scope -> astParam =>
          val (param, newScope) = transform(astParam, scope)

          newScope -> param
      }
      val fnParams = fnParamsIterable.toSeq

      // TODO: Switch this to `paramScope` if the fn body needs to be able to access the pattern names
      val innerScope = scope.newChild(
        fnParams
          .map { param => param.inName.originalName -> param.inName }
          .toMap
      )
      val fnBody = transform(body, innerScope)
      val fnReturnType = returnType.map(transform(_, paramScope))

      $FunctionDef(fnParams, fnBody, fnReturnType, location)

    case ASTValue.FunctionType(params, returnType, location) =>
      val (paramTypeScope, typeParams) = params.mapWithRollingContext(scope) {
        case scope -> pattern =>
          val (typeParam, paramTypeScope) = transform(pattern, scope)

          paramTypeScope -> typeParam
      }
      val returns = transform(returnType, paramTypeScope)

      $FunctionInterfaceDef(typeParams.toSeq, returns, location)

    case ASTValue.Call(target, name, arguments, mayBeVarCall, location) =>
      val positionalArgs = arguments.positional.map(transform(_, scope))
      val namedArgs = arguments.named.map { case name -> astValue => (name, transform(astValue, scope)) }

      if (mayBeVarCall) {
        scope.find(name) match {
          case Some(value) =>
            return $Call(
              "call",
              Arguments(
                self = $Reference(value, location),
                positionalArgs,
                namedArgs
              ),
              location
            )

          case _ =>
        }
      }

      $Call(
        name,
        Arguments(
          self = transform(target, scope),
          positionalArgs,
          namedArgs
        ),
        location
      )

    case ASTValue.NameReference(name, location) =>
      scope.find(name) match {
        case Some(varName) => $Reference(varName, location)
        case None =>
          val self = scope.find("self").getOrElse {
            throw EvalError(s"Could not find name $name in $scope", location)
          }
          val referenceToSelf = $Reference(self, location)

          $Call(
            name,
            args = Arguments.empty(referenceToSelf),
            location
          )
      }

    case ASTValue.Let(name, value, block, location) =>
      val varName = new VarName(name)
      val innerScope = scope.newChild(Map(name -> varName))

      $Let(
        varName,
        transform(value, innerScope),
        transform(block, innerScope),
        location
      )

    // TODO: This shouldn't be possible, right?
    //       Maybe I don't need Pattern to be instance of ASTValue
    case pattern: ASTValue.Pattern => ???
  }

  private def transform(param: ASTParameter, scope: StaticScope): (Parameter, StaticScope) = {
    val varName = new VarName(param.inName)
    val typePattern = param.typePattern
      .getOrElse { throw EvalError("Params must have explicit types for now", param.location) }

    val (typ, newScope) = transform(typePattern, scope)

    Parameter(param.outName, varName, typ, param.location) -> newScope
  }

  private def transform(param: ASTTypeParameter, scope: StaticScope): (TypeParameter, StaticScope) = {
    val (typ, newScope) = transform(param.typePattern, scope)

    TypeParameter(param.name, typ, param.location) -> newScope
  }

  private def transform(pattern: Pattern, scope: StaticScope): (ValuePattern, StaticScope) = {
    pattern match {
      case Pattern.SpecificValue(value) =>
        (
          ValuePattern.Expected(transform(value, scope), value.location),
          scope
        )

      case Pattern.Binding(name, location) =>
        val varName = new VarName(name)
        val newScope = scope.newChild(Map(name -> varName))

        (ValuePattern.Binding(varName, location), newScope)

      case Pattern.Call(target, name, args, mayBeVarCall, location) =>
        var argScope = scope
        val positional = args.positional.map { pattern =>
          val (valuePattern, newScope) = transform(pattern, argScope)

          argScope = newScope

          valuePattern
        }
        val named = args.named.view.mapValues { pattern =>
          val (valuePattern, newScope) = transform(pattern, argScope)

          argScope = newScope

          valuePattern
        }.toMap

        val valueArgs = ArgumentsWithoutSelf[ValuePattern](
          // TODO: The order of parameters matters here unfortunately, need to preserve it across positional and named,
          //       unless I want to make it so named parameters are strictly after positional ones
          positional,
          named
        )

        // TODO: Duplication with building $Call
        val (realTarget, realName) =
          if (mayBeVarCall) {
            scope.find(name) match {
              case Some(value) => $Reference(value, location) -> "call"
              case None =>
                val self = scope.find("self")
                  .getOrElse { throw EvalError(s"Could not find name $name in $scope", location) }

                $Reference(self, location) -> name
            }
          } else {
            transform(target, scope) -> name
          }

        (
          ValuePattern.Call(realTarget, realName, valueArgs, location),
          argScope
        )
    }
  }
}

object $MatchResult extends Type {
  val metaType = new Type {
    override def typ(scope: Scope): Type = $Type
    override val methods: Map[String, Method] = Map(
      "of" -> new DefaultMethod {
        override val signature = MethodSignature.any($MatchResult)
        override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): Value =
          $Object(
            ArgumentsWithoutSelf(spec.args.positional, spec.args.named),
            $MatchResult,
            location
          )
      }
    )
  }

  override def typ(scope: Scope): Type = metaType
  override val methods: Map[String, Method] = Map.empty
}

case class MatchResult(bindings: Map[VarName, Value])

case class PatternNames(defined: Set[VarName], unbound: Set[VarName])

sealed trait ValuePattern {
  def names: PatternNames

  def applyTo(value: Value, env: Environment): Option[MatchResult]
  def toASTWithPreBoundNames(names: Map[VarName, String]): ASTValue.Pattern

  // TODO: Compare types by isAssignableTo instead of equals. But then
  //       I need something to actually convert between those when doing
  //       the call.
  def isSupersetOf(
    otherPattern: ValuePattern,
    selfEnv: Environment,
    otherEnv: Environment
  ): Boolean
}
object ValuePattern {
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
      ASTValue.Pattern.SpecificValue(expectedValue.toAST(names))

    override def isSupersetOf(
      otherPattern: ValuePattern,
      selfEnv: Environment,
      otherEnv: Environment
    ): Boolean = otherPattern match {
      case Expected(otherExpectedValue, _) =>
        // TODO: Need `equals` between values here
        expectedValue.evaluate(selfEnv) == otherExpectedValue.evaluate(otherEnv)

      case Binding(name, location) => false
      case callPattern: Call =>
        callPattern.applyTo(expectedValue.evaluate(selfEnv), otherEnv) match {
          case Some(matchResult) => true
          case None => false
        }

      // TODO: Remove this case
      case List(patterns) => ???
    }
  }

  case class Binding(name: VarName, location: Option[Location]) extends ValuePattern {
    override def names = PatternNames(
      defined = Set(name),
      unbound = Set.empty
    )

    override def applyTo(value: Value, env: Environment) =
      Some(MatchResult(Map(name -> value)))

    override def toASTWithPreBoundNames(names: Map[VarName, String]): Pattern =
      ASTValue.Pattern.Binding(
        names.getOrElse(name, throw EvalError(s"Could not find string name for $name", location)),
        location
      )

    override def isSupersetOf(
      otherPattern: ValuePattern,
      selfEnv: Environment,
      otherEnv: Environment
    ): Boolean = otherPattern match {
      case Expected(expectedValue, location) => true
      case Binding(name, location) => true
      case Call(target, name, args, location) => true

      // TODO: Remove this case
      case List(patterns) => ???
    }
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
      // TODO: DelayCall if this needs to be done runtime
      val resultArgs = $Call(
        s"$name$$match",
        Arguments.positional(target, Seq(value)),
        location
      ).evaluate(env) match {
        // TODO: What if the stuff conforms to this type but is wrapped?
        // TODO: The [Value] part is not checked, do something with the warning
        case $Object(args: ArgumentsWithoutSelf[Value], typ, _) if typ == $MatchResult => args
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
      ASTValue.Pattern.Call(
        target.toAST(names),
        name,
        args = args.map(_.toASTWithPreBoundNames(names)),
        mayBeVarCall = false,
        location
      )

    override def isSupersetOf(
      otherPattern: ValuePattern,
      selfEnv: Environment,
      otherEnv: Environment
    ): Boolean = otherPattern match {
      case Expected(expectedValue, _) =>
        this.applyTo(expectedValue.evaluate(otherEnv), selfEnv) match {
          case Some(value) => true
          case None => false
        }

      case Binding(_, _) => false

      case Call(otherTarget, otherName, otherArgs, _) =>
        // TODO: Need `equals` between values here
        val targetsAreTheSame = target.evaluate(selfEnv) == otherTarget.evaluate(otherEnv)
        if (!targetsAreTheSame) {
          return false
        }

        val namesAreTheSame = name == otherName
        if (!namesAreTheSame) {
          return false
        }

        val argsAreTheSameSize = args.argValues.size == otherArgs.argValues.size
        if (!argsAreTheSameSize) {
          return false
        }

        args.argValues.zip(otherArgs.argValues).foreach { case (selfArg, otherArg) =>
          // TODO: Argument envs
          if (!selfArg.isSupersetOf(otherArg, selfEnv, otherEnv)) {
            return false
          }
        }

        true

      // TODO: Remove this case
      case List(patterns) => ???
    }
  }

  case class List(patterns: Seq[ValuePattern]) extends ValuePattern {
    // TODO: Duplication with Call#names
    override def names = patterns
      .foldLeft(PatternNames(defined = Set.empty, unbound = Set.empty)) {
        case names -> pattern =>
          val PatternNames(defined, unbound) = pattern.names

          PatternNames(
            defined = names.defined ++ defined,
            unbound = names.unbound ++ (unbound -- names.defined)
          )
      }

    // TODO: Don't really need this, do I
    override def applyTo(value: Value, env: Environment): Option[MatchResult] = ???

    // TODO: Don't really need this, do I
    override def toASTWithPreBoundNames(names: Map[VarName, String]): Pattern = ???

    // TODO: Don't really need this, do I
    override def isSupersetOf(otherPattern: ValuePattern, selfEnv: Environment, otherEnv: Environment): Boolean = ???
  }
}
