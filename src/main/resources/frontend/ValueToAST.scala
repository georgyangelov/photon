package photon.frontend

import photon.{Arguments, Location, UFunction, ULiteral, UOperation, UValue, VariableName}

object ValueToAST {
  def transformRenamingAll(value: UValue, prefix: String): ASTValue =
    transform(value, Map.empty, renameAllPrefix = Some(prefix), forInspection = false)

  def transform(value: UValue): ASTValue =
    transform(value, Map.empty, renameAllPrefix = None, forInspection = false)

  def transformForInspection(value: UValue): ASTValue = {
    // TODO: Remove this branching for ASTBlock
    transform(value, Map.empty, renameAllPrefix = None, forInspection = true)
  }

  def transformForInspection(arguments: Arguments[UValue]) = ASTArguments(
    positional = arguments.positional.map(transform(_, Map.empty, None, forInspection = true)),
    named = arguments.named.view.mapValues(transform(_, Map.empty, None, forInspection = true)).toMap
  )

  private def transform(
    value: UValue,
    varNames: Map[VariableName, String],
    renameAllPrefix: Option[String],
    forInspection: Boolean
  ): ASTValue = value match {
    case ULiteral.Nothing(location) => ASTValue.NameReference("None", location)
    case ULiteral.Boolean(value, location) => ASTValue.Boolean(value, location)
    case ULiteral.Int(value, location) => ASTValue.Int(value, location)
    case ULiteral.Float(value, location) => ASTValue.Float(value, location)
    case ULiteral.String(value, location) => ASTValue.String(value, location)

    case UOperation.Block(values, location) =>
      ASTValue.Block(
        values.map(transform(_, varNames, renameAllPrefix, forInspection)),
        location
      )

    case UOperation.Let(variable, letValue, block, location) =>
      val name = renameAllPrefix match {
        case Some(prefix) => s"$prefix$$${variable.originalName}"
        case None =>
          uniqueName(
            variable.originalName,
            letValue.unboundNames.map(_.originalName)
          )
      }

      val innerVarNames = varNames.updated(variable, name)

      ASTValue.Let(
        name,
        transform(letValue, innerVarNames, renameAllPrefix, forInspection),
        transform(block, innerVarNames, renameAllPrefix, forInspection),
        location
      )

    case UOperation.Reference(name, location) =>
      ASTValue.NameReference(
        varNames.getOrElse(name, name.originalName),
        location
      )

    case UOperation.Function(fn, location) =>
      transformFn(fn, varNames, location, forInspection)

    case UOperation.Call(name, arguments, location) =>
      val astTarget = transform(arguments.self, varNames, renameAllPrefix, forInspection)
      val astArguments = ASTArguments(
        positional = arguments.positional.map(transform(_, varNames, renameAllPrefix, forInspection)),

        // TODO: Support renames of function parameters
        named = arguments.named.view.mapValues(transform(_, varNames, renameAllPrefix, forInspection)).toMap
      )

      astTarget match {
        case ASTValue.NameReference(targetName, _) if name == "call" =>
          ASTValue.Call(
            target = ASTValue.NameReference("self", location),
            name = targetName,
            arguments = astArguments,
            mayBeVarCall = true,
            location = location
          )
        case _ =>
          ASTValue.Call(
            target = astTarget,
            name = name,
            arguments = astArguments,
            mayBeVarCall = false,
            location = location
          )
      }
  }

  private def uniqueName(original: String, used: Set[String]) = {
    var name = original
    var i = 1

    while (used.contains(name)) {
      name = s"${original}__$i"
      i += 1
    }

    name
  }

  private def transformFn(
    fn: UFunction,
    varNames: Map[VariableName, String],
    location: Option[Location],
    forInspection: Boolean
  ) =
    ASTValue.Function(
      params = fn.params.map { param =>
        ASTParameter(
          // TODO: Support renames of function parameters
          param.name,
          // TODO: Is this correct that the rename prefix is empty?
          Some(param.typ).map(transform(_, varNames, None, forInspection)),
          location
        )
      },
      // TODO: Is this correct that the rename prefix is empty?
      body = transform(fn.body, varNames, None, forInspection),
      returnType = fn.returnType.map(transform(_, varNames, None, forInspection)),
      location
    )
}