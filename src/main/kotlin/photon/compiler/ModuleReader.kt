package photon.compiler

import photon.compiler.core.*
import photon.compiler.nodes.*
import photon.compiler.types.IntType
import photon.core.EvalError
import photon.frontend.ASTValue
import photon.frontend.Pattern

class ModuleReader(
  private val context: PhotonContext
) {
  fun read(ast: ASTValue): PhotonModule {
    val main = transformMainFunction(ast)

    return PhotonModule(context.language, main)
  }

  private fun transformMainFunction(ast: ASTValue): PhotonModuleRootFunction {
    val rootScope = context.newGlobalLexicalScope()
    val innerNode = transform(ast, rootScope)
    val frameDescriptor = rootScope.frameDescriptor()

    return PhotonModuleRootFunction(
      language = context.language,
      frameDescriptor = frameDescriptor,
      body = innerNode
    )
  }

  private fun transformFunctionDefinition(ast: ASTValue.Function, scope: LexicalScope): FunctionDefinitionNode {
    val argumentNames = ast.params.map { it.inName }
    val functionScope = scope.newChildFunction(argumentNames)

    val paramTypes = ast.params.map {
      val typeNode = transform(assertSpecificValue(it.typePattern), scope)

      FunctionDefinitionNode.ParameterNode(it.outName, typeNode)
    }.toTypedArray()

    val returnType =
      if (ast.returnType != null)
        transform(ast.returnType, scope)
      else
        null

    val body = transform(ast.body, functionScope)

    val frameDescriptor = functionScope.frameDescriptor()
    val captures = functionScope.captures()
    val argumentCaptures = functionScope.argumentCaptures()

    return FunctionDefinitionNode(
      paramTypes,
      returnType,
      body,
      ast.isCompileTimeOnly,
      frameDescriptor,
      captures,
      argumentCaptures
    )
  }

  // TODO: Remove this once I have pattern support
  private fun assertSpecificValue(pattern: Pattern?) = when (pattern) {
    is Pattern.SpecificValue -> pattern.value
    null -> throw EvalError("Parameters need to have explicit parameters for now", null)
    else -> throw EvalError("Patterns in parameter types are not supported for now", pattern.location)
  }

  private fun transform(ast: ASTValue, scope: LexicalScope): PhotonNode = when (ast) {
    is ASTValue.Boolean -> LiteralNode(ast.value, RootType, ast.location)
    is ASTValue.Int -> LiteralNode(ast.value, IntType, ast.location)
    is ASTValue.Float -> LiteralNode(ast.value, RootType, ast.location)
    is ASTValue.String -> LiteralNode(ast.value, RootType, ast.location)

    is ASTValue.Call -> CallNode(
      target = transform(ast.target, scope),
      name = ast.name,
      arguments = ast.arguments.positional.map { transform(it, scope) }.toTypedArray()
    )

    is ASTValue.Let -> {
      val (innerScope, slot) = scope.newChildBlockWithName(ast.name)
      val value = transform(ast.value, innerScope)
      val body = transform(ast.block, innerScope)

      LetNode(ast.name, slot, value, body, ast.location)
    }

    is ASTValue.NameReference -> {
      val slot = scope.accessName(ast.name) ?: throw EvalError("Could not find name ${ast.name}", ast.location)

      ReferenceNode(ast.name, slot, ast.location)
    }

    is ASTValue.Function -> transformFunctionDefinition(ast, scope)

    is ASTValue.Block -> {
      val expressions = ast.values.map { transform(it, scope) }.toTypedArray()

      BlockNode(expressions, ast.location)
    }

    is ASTValue.FunctionType -> TODO()
  }
}

