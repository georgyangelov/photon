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
      throw EvalError("val definitions must be directly inside of a block", ast.location)
    }

    is ASTValue.NameReference -> {
      val slot = scope.accessName(ast.name)
        ?: throw EvalError("Could not find name ${ast.name}", ast.location)

      ReferenceNode(ast.name, slot, ast.location)
    }

    is ASTValue.Function -> transformFunctionDefinition(ast, scope)

    is ASTValue.Block -> {
      var currentScope = scope.newChildBlock()

      ast.values
        .filter { it is ASTValue.Let && it.isRecursive }
        .map { currentScope.defineName((it as ASTValue.Let).name) }

      val expressions = ast.values.map {
        when (it) {
          is ASTValue.Let -> {
            val value = transform(it.value, currentScope)

            val slot = if (it.isRecursive) {
              currentScope.accessName(it.name)!!
            } else {
              val (innerScope, slot) = currentScope.newChildBlockWithName(it.name)
              currentScope = innerScope

              slot
            }

            LetNode(it.name, slot, value, ast.location)
          }

          else -> transform(it, currentScope)
        }
      }.toTypedArray()

      BlockNode(expressions, ast.location)
    }

    is ASTValue.FunctionType -> {
      val parameterTypes = ast.params.map {
        val type = transform(it.typ, scope)

        FunctionTypeDefinitionNode.ParameterNode(it.name, type)
      }.toTypedArray()

      val returnType = transform(ast.returnType, scope)

      FunctionTypeDefinitionNode(parameterTypes, returnType)
    }

    is ASTValue.TypeAssert -> {
      val value = transform(ast.value, scope)
      val type = transform(ast.type, scope)

      TypeAssertNode(value, type)
    }
  }
}
