//package photon.interpreter
//
//import photon._
//
//case class ValueWithScope(value: Value, scope: Option[Scope]) {
//  // TODO: Constructing this, scope can be set to None if there are no unboundNames
//  def toScope(toScope: Scope): ValueWithScope = {
//    if (scope.isEmpty || scope.get.eq(toScope) || value.unboundNames.isEmpty) {
//      return this
//    }
//
//    val newValue = ValueWithScope.moveScope(value, scope.get, toScope, Map.empty)
//
//    ValueWithScope(newValue, Some(toScope))
//  }
//}
//
//object ValueWithScope {
//  // TODO: Does this work with recursive functions?
//  private def moveScope(value: Value, from: Scope, to: Scope, renames: Map[VariableName, VariableName]): Value = {
//    val namesToMove = detectNamesToMove(value.unboundNames, from, to)
//
//    var currentScope: Option[Scope] = Some(from)
//    var valueWithLets = value
//
//    do {
//      val scope = currentScope.get
//      val namesInScopeToMove = scope.variables.keySet.intersect(namesToMove)
//      val variablesToMove = namesInScopeToMove.map(scope.variables.get).map(_.get)
//
//      valueWithLets = variablesToMove.foldLeft(valueWithLets) { case (result, variable) =>
//        Value.Operation(
//          Operation.Let(
//            name = variable.name,
//            value = variable.value,
//            block = result match {
//              case Value.Operation(block @ Operation.Block(_), _) => block
//              case _ => Operation.Block(Seq(result))
//            }
//          ),
//          result.location
//        )
//      }
//
//      currentScope = scope.parent
//    } while (currentScope.isDefined)
//
//    val additionalRenames = namesToMove
//      .map { oldName => oldName -> new VariableName(oldName.originalName) }
//      .toMap
//
//    renameAndUnbind(valueWithLets, renames ++ additionalRenames)
//  }
//
//  private def renameAndUnbind(value: Value, renames: Map[VariableName, VariableName]): Value = value match {
//    case Value.Unknown(_) |
//         Value.Nothing(_) |
//         Value.Boolean(_, _) |
//         Value.Int(_, _, _) |
//         Value.Float(_, _) |
//         Value.String(_, _) |
//         Value.Native(_, _) => value
//
//    case Value.Struct(Struct(values), location) =>
//      Value.Struct(
//        Struct(values.view.mapValues(renameAndUnbind(_, renames)).toMap),
//        location
//      )
//
//    case Value.BoundFunction(BoundFunction(fn, scope, traits), location) =>
//      // TODO: Serialize traits correctly
//      // TODO: Make sure the bound function's scope is expected?
//      // throw EvalError("Encountered BoundFn during renaming", location)
//      renameAndUnbind(
//        Value.Operation(
//          Operation.Function(fn),
//          location
//        ),
//        renames
//      )
//
//    case Value.Operation(Operation.Block(values), location) =>
//      Value.Operation(
//        Operation.Block(values.map(renameAndUnbind(_, renames))),
//        location
//      )
//
//    case Value.Operation(Operation.Let(name, letValue, block), location) =>
//      val newName = new VariableName(name.originalName)
//      val innerRenames = renames + (name -> newName)
//
//      val newLetValue = renameAndUnbind(letValue, innerRenames)
//      val newBlock = Operation.Block(block.values.map(renameAndUnbind(_, innerRenames)))
//
//      Value.Operation(
//        Operation.Let(newName, newLetValue, newBlock),
//        location
//      )
//
//    case Value.Operation(Operation.Reference(name), location) =>
//      Value.Operation(
//        Operation.Reference(renames.getOrElse(name, name)),
//        location
//      )
//
//    case Value.Operation(Operation.Function(fn), location) =>
//      val fnBodyWithRenames = Operation.Block(fn.body.values.map(renameAndUnbind(_, renames)))
//
//      // TODO: Rename function parameters?
//      val functionWithRenames = new Function(fn.params, fnBodyWithRenames)
//
//      Value.Operation(
//        Operation.Function(functionWithRenames),
//        location
//      )
//
//    case Value.Operation(Operation.Call(target, name, arguments), location) =>
//      Value.Operation(
//        Operation.Call(
//          target = renameAndUnbind(target, renames),
//          name,
//          arguments = arguments.map(renameAndUnbind(_, renames))
//        ),
//        location
//      )
//  }
//
//  // TODO: This should consider names that:
//  //       1. Are in unboundNames
//  //       2. Are in `from`
//  //       3. Are not in `to`
//  private def detectNamesToMove(unboundNames: Set[VariableName], from: Scope, to: Scope): Set[VariableName] = {
//    unboundNames
//      .filter(to.find(_).isEmpty)
//      // .filter(from.find(_).nonEmpty)
//      .map(from.find(_).get)
//      .flatMap { variable =>
//        if (unboundNames.contains(variable.name)) {
//          Set(variable.name)
//        } else {
//          detectNamesToMove(variable.value.unboundNames, from, to) + variable.name
//        }
//      }
//  }
//}
