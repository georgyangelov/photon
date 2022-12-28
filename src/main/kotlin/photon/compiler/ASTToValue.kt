package photon.compiler

import photon.compiler.core.Value
import photon.frontend.ASTValue

object ASTToValue {
  fun transform(ast: ASTValue): Value {
    throw RuntimeException("TODO")
  }
//  fun transform(ast: ASTValue): Value = when (ast) {
//    is ASTValue.Boolean -> PObject(ast.value)
//  }
}