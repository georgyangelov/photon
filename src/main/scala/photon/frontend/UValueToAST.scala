package photon.frontend

import photon.base._

object UValueToAST {
  def transformRenamingAll(value: UValue, prefix: String): ASTValue =
    transform(value, Map.empty, renameAllPrefix = Some(prefix), forInspection = false)

  def transform(value: UValue): ASTValue =
    transform(value, Map.empty, renameAllPrefix = None, forInspection = false)

  def transformForInspection(value: UValue): ASTValue = {
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

            // This is intentionally not `varNames.values.toSet`.
            // If there is another `let` with this name externally, but it is not
            // used in this let's body or value, then we want to reuse the name,
            // shadowing the old one.
            value.unboundNames.map(name => varNames.getOrElse(name, name.originalName))
          )
      }

      val innerVarNames = varNames + (variable -> name)

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
      transformFn(fn, varNames, renameAllPrefix, location, forInspection)

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

  private def transform(
    value: UPattern,
    varNames: Map[VariableName, String],
    renameAllPrefix: Option[String],
    forInspection: Boolean
  ): ASTPattern = value match {
    case UPattern.SpecificValue(value) => ASTPattern.SpecificValue(
      transform(value, varNames, renameAllPrefix, forInspection)
    )
    case UPattern.Val(name) => ASTPattern.Val(name)
  }

  private def transformFn(
    fn: UFunction,
    varNames: Map[VariableName, String],
    renameAllPrefix: Option[String],
    location: Option[Location],
    forInspection: Boolean
  ) = {
    val unboundNames = fn.unboundNames.map(name => varNames.getOrElse(name, name.originalName))
    val params = fn.params.map { param =>
      // TODO: This is not exactly the reverse of ASTToUValue in that names are not taken
      //       one by one, but at the same time. It shouldn't have any practical issues for now
      //       but keep this in mind. It may have issues with parameters that have the same name,
      //       but this shouldn't happen anyway.
      val newInName = renameAllPrefix match {
        case Some(prefix) => s"$prefix$$${param.inName}"
        case None => uniqueName(param.inName, unboundNames)
      }

      val typePattern = transform(param.typ, varNames, renameAllPrefix, forInspection)

      ASTParameter(param.outName, newInName, Some(typePattern), param.location)
    }

    val body = transform(fn.body, varNames, renameAllPrefix, forInspection)
    val returnType = fn.returnType.map(transform(_, varNames, renameAllPrefix, forInspection))

    ASTValue.Function(params, body, returnType, location)
  }
}
