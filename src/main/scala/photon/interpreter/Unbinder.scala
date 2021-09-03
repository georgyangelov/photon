package photon.interpreter

import photon.core.Core
import photon.{Arguments, BoundValue, Operation, PureValue, RealValue, Scope, UnboundValue, Value, Variable, VariableName}

class Unbinder(core: Core) {
  def unbind(value: Value, toScope: Scope): UnboundValue = value match {
    case boundValue: BoundValue => unbind(boundValue, toScope)
    case other: UnboundValue => other
  }

  def unbind(boundValue: BoundValue, toScope: Scope): UnboundValue = {
    val dependentNames = detectNamesToMove(boundValue.unboundNames, boundValue.scope, toScope)
    val dependentVariables = extractVariablesInOrder(dependentNames, boundValue.scope)

    val renames = dependentNames.view
      .map { name => name -> new VariableName(name.originalName) }
      .toMap

    val unboundValue = renameReferences(unsafeUnbind(boundValue), renames)

    dependentVariables.foldLeft(unboundValue) { case (value, variable) =>
      val varValue = variable.value.getOrElse { throw EvalError(s"Cannot get variable value for ${variable.name.originalName}", None) }

      Operation.Let(
        variable.name,
        renameReferences(unsafeUnbind(varValue), renames),
        value.asBlock,
        value.realValue,
        value.location
      )
    }
  }

  private def unsafeUnbind(value: Value): UnboundValue = value match {
    case unbound: UnboundValue => unbound
    case boundFn @ BoundValue.Function(fn, traits, _, location) =>
      // TODO: Serialize traits correctly
      Operation.Function(fn, Some(boundFn), location)
    case boundObject @ BoundValue.Object(values, _, location) =>
      Operation.Call(
        // TODO: I don't like this cast
        Operation.Reference(core.objectRoot.name, Some(core.objectRoot.value.get.asInstanceOf[PureValue]), location),
        "call",
        Arguments(
          None,
          positional = Seq.empty,
          named = values.view.mapValues(unsafeUnbind).toMap
        ),
        Some(boundObject),
        location
      )
  }

  // This considers names that:
  //   1. Are in unboundNames
  //   2. Are in `from`
  //   3. Are not in `to`
  private def detectNamesToMove(unboundNames: Set[VariableName], from: Scope, to: Scope): Set[VariableName] = {
    unboundNames
      .filter(to.find(_).isEmpty)
      .flatMap(from.find)
      .flatMap { variable =>
        if (unboundNames.contains(variable.name)) {
          Set(variable.name)
        } else {
          detectNamesToMove(variable.value.get.unboundNames, from, to) + variable.name
        }
      }
  }

  private def extractVariablesInOrder(names: Set[VariableName], startScope: Scope): Seq[Variable] = {
    var currentScope: Option[Scope] = Some(startScope)
    val variables = Seq.newBuilder[Variable]

    do {
      val scope = currentScope.get

      val namesInCurrentScope = scope.variables.keySet.intersect(names)
      variables.addAll(namesInCurrentScope.map(scope.variables.get).map(_.get))

      currentScope = scope.parent
    } while (currentScope.isDefined)

    variables.result
  }

  // TODO: Do we need to rename references in the `realValue`s as well? I think we don't
  private def renameReferences(value: UnboundValue, renames: Map[VariableName, VariableName]): UnboundValue = value match {
    case pure: PureValue => pure

    case Operation.Block(values, realValue, location) =>
      Operation.Block(
        values.map(renameReferences(_, renames)),
        realValue,
        location
      )

    case Operation.Let(name, letValue, block, realValue, location) =>
      Operation.Let(
        name,
        renameReferences(letValue, renames),
        Operation.Block(
          block.values.map(renameReferences(_, renames)),
          block.realValue,
          block.location
        ),
        realValue,
        location
      )

    case Operation.Reference(name, realValue, location) =>
      Operation.Reference(
        renames.getOrElse(name, name),
        realValue,
        location
      )

    case Operation.Function(fn, realValue, location) =>
      val fnWithRenames = new photon.Function(
        fn.selfName,
        fn.params,
        Operation.Block(
          fn.body.values.map(renameReferences(_, renames)),
          fn.body.realValue,
          fn.body.location
        )
      )

      Operation.Function(fnWithRenames, realValue, location)

    case Operation.Call(target, name, arguments, realValue, location) =>
      Operation.Call(
        renameReferences(target, renames),
        name,
        arguments.map(renameReferences(_, renames)),
        realValue,
        location
      )
  }
}
