package photon.core.operations

import photon.base._
import photon.core.$Lazy
import photon.frontend.ASTValue
import photon.lib.Lazy

case class $Let(name: VarName, value: Value, body: Value, location: Option[Location]) extends Value {
  override def isOperation = true
  override def evalMayHaveSideEffects = value.evalMayHaveSideEffects || body.evalMayHaveSideEffects
  override def unboundNames = value.unboundNames ++ body.unboundNames - name
  override def typ(scope: Scope) = {
    val innerScope = scope.newChild(Seq(name -> value))

    body.typ(innerScope)
  }

  override def evaluate(env: Environment) = {
    var evalue: Option[EvalResult[Value]] = None
    val lazyValue = $Lazy(Lazy.of(() => {
      if (evalue.isEmpty) {
        throw EvalError("Cannot evaluate self-referencing value directly", location)
      }

      evalue.get.value
    }), location)

    val innerEnv = Environment(
      scope = env.scope.newChild(Seq(name -> lazyValue)),
      evalMode = env.evalMode
    )

    evalue = Some(value.evaluate(innerEnv))
    val ebody = body.evaluate(innerEnv)

    ebody.value match {
      // Inline if the body is a direct reference to this let value
      case $Reference(refName, _) if refName == name => evalue.get
      case value if value.unboundNames.contains(name) =>
        val result = $Let(name, evalue.get.value, value, location)

        EvalResult(result, evalue.get.closures ++ ebody.closures)

      case _ if evalue.get.value.evalMayHaveSideEffects =>
        val result = $Block(Seq(evalue.get.value, ebody.value), location)

        EvalResult(result, evalue.get.closures ++ ebody.closures)

      case _ => ebody
    }
  }

  override def partialValue(env: Environment, followReferences: Boolean) =
    body.partialValue(env, followReferences).withOuterVariable(name, value)

  override def toAST(names: Map[VarName, String]) = {
    val uniqueName = findUniqueNameFor(name, names.values.toSet)
    val innerNames = names + (name -> uniqueName)

    ASTValue.Let(
      uniqueName,
      value.toAST(innerNames),
      body.toAST(innerNames),
      location
    )
  }

  private def findUniqueNameFor(name: VarName, usedNames: Set[String]): String = {
    // TODO: Define `Value#unboundNames` and check if the duplicate name is actually used before renaming
    if (!usedNames.contains(name.originalName)) {
      return name.originalName
    }

    var i = 1
    while (usedNames.contains(s"${name.originalName}__$i")) {
      i += 1
    }

    s"${name.originalName}__$i"
  }
}
