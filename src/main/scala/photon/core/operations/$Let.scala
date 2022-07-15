package photon.core.operations

import photon.base._
import photon.core.$Lazy
import photon.frontend.ASTValue
import photon.lib.Lazy

case class $Let(name: VarName, value: Value, body: Value, location: Option[Location]) extends Value {
  override def typ(scope: Scope) = body.typ(scope)

  override def evaluate(scope: Scope, evalMode: EvalMode): Value = {
    var evalue: Option[Value] = None
    val lazyValue = $Lazy(Lazy.of(() => {
      if (evalue.isEmpty) {
        throw EvalError("Cannot evaluate self-referencing value directly", location)
      }

      evalue.get
    }
    ), location
    )

    val innerScope = scope.newChild(Seq(name -> lazyValue))

    evalue = Some(value.evaluate(innerScope, evalMode))
    val ebody = body.evaluate(innerScope, evalMode)

    // Inline if the body is a direct reference to this let value
    // case UValue.Reference(refName, _) if refName == name => evalue
    // case _ if ebody.unboundNames.contains(name) => $Let.Value(name, evalue, ebody, location)
    // case _ => UValue.Block(Seq(value, body), location)
    $Let(name, evalue.get, ebody, location)

    //    val unknown = $Unknown(location)
    //    val innerScope = scope.newChild(Seq(name -> $Lazy(unknown, location)))
    //
    //    val evalue = evaluate(value, innerScope, mode)
    //    innerScope.dangerouslySetValue(name, evalue)
    //
    //    val ebody = evaluate(body, innerScope, mode)
    //    ebody.value match {
    //      // Inline if the body is a direct reference to this let value
    //      case UValue.Reference(refName, _) if refName == name => evalue
    //      // case _ if ebody.unboundNames.contains(name) => $Let.Value(name, evalue, ebody, location)
    //      // case _ => UValue.Block(Seq(value, body), location)
    //      case _ =>
    //        EValue(
    //          UValue.Let(name, evalue.value, ebody.value, location),
    //          ebody.typ
    //        )
    //    }
  }

  override def toAST(names: Map[VarName, String]): ASTValue = ???
}
