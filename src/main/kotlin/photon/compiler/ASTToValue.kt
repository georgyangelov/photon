package photon.compiler

import photon.compiler.core.*
import photon.compiler.operations.PCall
import photon.compiler.types.IntType
import photon.frontend.ASTValue

object ASTToValue {
  fun transform(ast: ASTValue): Value = when (ast) {
    is ASTValue.Boolean -> PObject(ast.value, RootType)
    is ASTValue.Int -> PObject(ast.value, IntType)
    is ASTValue.Float -> TODO()
    is ASTValue.String -> PObject(ast.value, RootType)

    is ASTValue.Call -> PCall(
      target = transform(ast.target),
      name = ast.name,
      arguments = ast.arguments.positional.map { transform(it) }.toTypedArray()
    )

    is ASTValue.Block -> TODO()
    is ASTValue.Function -> TODO()
    is ASTValue.FunctionType -> TODO()
    is ASTValue.Let -> TODO()
    is ASTValue.NameReference -> TODO()
  }
}