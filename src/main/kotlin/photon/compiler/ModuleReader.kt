package photon.compiler

import photon.compiler.core.*
import photon.compiler.nodes.*
import photon.compiler.types.IntType
import photon.core.EvalError
import photon.frontend.*

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

  private fun transformTemplateFunctionDefinition(
    ast: ASTValue.Function,
    scope: LexicalScope
  ): TemplateFunctionDefinitionNode {
    val argumentNames = ast.params.map { it.inName }
    val typeScope = scope.newChildFunction(emptyList())

    // We need this to be at slot 0
    typeScope.defineName("patternTarget")

    val functionScope = scope.newChildFunction(argumentNames)

    val (paramPatterns, returnType, _) = transformArgumentPatterns(ast.params, ast.returnType, typeScope)

    val body = transform(ast.body, functionScope)

    val typeFrameDescriptor = typeScope.frameDescriptor()
    val executionFrameDescriptor = functionScope.frameDescriptor()
    val captures = functionScope.captures()
    val argumentCaptures = functionScope.argumentCaptures()

    return TemplateFunctionDefinitionNode(
      argumentPatterns = paramPatterns,
      returnType = returnType,
      body = body,

      typeFrameDescriptor = typeFrameDescriptor,
      executionFrameDescriptor = executionFrameDescriptor,
      requiredCaptures = captures,
      argumentCaptures = argumentCaptures
    )
  }

  private fun transformArgumentPatterns(
    parameters: List<ASTParameter>,
    returnType: ASTValue?,
    typeScope: LexicalScope.FunctionScope
  ): Triple<
      Array<TemplateFunctionDefinitionNode.ParameterNode>,
      PhotonNode?,
      LexicalScope
    >
  {
    var scope: LexicalScope = typeScope

    val paramPatterns = parameters.map {
      val pattern = it.typePattern
        ?: throw EvalError("Parameters need to have explicit types for now", null)

      val (node, newScope) = transformPattern(pattern, scope)
      scope = newScope

      TemplateFunctionDefinitionNode.ParameterNode(it.outName, node)
    }.toTypedArray()

    val returnTypeNode =
      if (returnType != null)
        transform(returnType, scope)
      else
        null

    return Triple(paramPatterns, returnTypeNode, scope)
  }

  private fun transformPattern(
    pattern: Pattern,
    scope: LexicalScope
  ): Pair<PatternNode, LexicalScope> = when (pattern) {
    is Pattern.SpecificValue -> {
      val node = PatternNode.SpecificValue(transform(pattern.value, scope))

      Pair(node, scope)
    }

    is Pattern.Binding -> {
      val (newScope, slot) = scope.newChildBlockWithName(pattern.name)
      val node = PatternNode.Binding(pattern.name, slot)

      Pair(node, newScope)
    }

    is Pattern.Call -> {
      val varCallSlot = if (pattern.mayBeVarCall) scope.accessName(pattern.name) else null
      var newScope = scope

      val arguments = pattern.arguments.positional.map {
        val (node, innerScope) = transformPattern(it, newScope)
        newScope = innerScope

        node
      }

      val node = if (varCallSlot != null) {
        PatternNode.Call(
          target = ReferenceNode(pattern.name, varCallSlot, pattern.location),
          name = "call",
          arguments = arguments
        )
      } else {
        PatternNode.Call(
          target = transform(pattern.target, scope),
          name = pattern.name,
          arguments = arguments
        )
      }

      Pair(node, newScope)
    }

    is Pattern.FunctionType -> TODO()
  }

  private fun assertSpecificValue(pattern: Pattern?) = when (pattern) {
    is Pattern.SpecificValue -> pattern.value
    null -> throw EvalError("Parameters need to have explicit types for now", null)
    else -> throw RuntimeException("This should not happen - regular function has template parameter")
  }

  private fun transform(ast: ASTValue, scope: LexicalScope): PhotonNode = when (ast) {
    is ASTValue.Boolean -> LiteralNode(ast.value, RootType, ast.location)
    is ASTValue.Int -> LiteralNode(ast.value, IntType, ast.location)
    is ASTValue.Float -> LiteralNode(ast.value, RootType, ast.location)
    is ASTValue.String -> LiteralNode(ast.value, RootType, ast.location)

    is ASTValue.Call -> {
      val varCallSlot = if (ast.mayBeVarCall) scope.accessName(ast.name) else null
      val arguments = ast.arguments.positional.map { transform(it, scope) }.toTypedArray()

      if (varCallSlot != null) {
        CallNode(
          target = ReferenceNode(ast.name, varCallSlot, ast.location),
          name = "call",
          arguments = arguments
        )
      } else {
        CallNode(
          target = transform(ast.target, scope),
          name = ast.name,
          arguments = arguments
        )
      }
    }

    is ASTValue.Let -> {
      throw EvalError("val definitions must be directly inside of a block", ast.location)
    }

    is ASTValue.NameReference -> {
      val slot = scope.accessName(ast.name)

      if (slot == null) {
        if (ast.name == "self") {
          throw EvalError("Could not find name ${ast.name}", ast.location)
        } else {
          val selfSlot = scope.accessName("self")
            ?: throw EvalError("Could not find name ${ast.name}", ast.location)

          CallNode(
            target = ReferenceNode("self", selfSlot, ast.location),
            name = ast.name,
            arguments = emptyArray()
          )
        }
      } else {
        ReferenceNode(ast.name, slot, ast.location)
      }
    }

    is ASTValue.Function -> {
      val isTemplateFunction = ast.params.any {
        when (it.typePattern) {
          is Pattern.SpecificValue -> false
          null -> throw EvalError("Parameters need to have explicit types for now", null)
          else -> true
        }
      }

      if (isTemplateFunction) {
        transformTemplateFunctionDefinition(ast, scope)
      } else {
        transformFunctionDefinition(ast, scope)
      }
    }

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
