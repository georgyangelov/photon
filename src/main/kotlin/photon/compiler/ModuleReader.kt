package photon.compiler

import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import photon.compiler.core.*
import photon.compiler.operations.*
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

  private fun transformMainFunction(ast: ASTValue): PhotonFunction {
//    val rootScope = LexicalScope.newRoot(emptyList(), frameBuilder)
    val rootScope = context.newGlobalLexicalScope()

    val innerNode = transform(ast, rootScope)

    val frameDescriptor = rootScope.frameDescriptor()
    val rootNode = PhotonFunctionRootNode(context.language, emptyList(), innerNode, frameDescriptor, null)

    return PhotonFunction(rootNode)
  }

  private fun transformFunctionDefinition(ast: ASTValue.Function, scope: LexicalScope): PFunctionDefinition {
    val paramNames = ast.params.map { it.inName }
//    val rootScope = LexicalScope.newRoot(paramNames, frameBuilder)
    val rootScope = context.newGlobalLexicalScope(paramNames)

    val paramTypes = ast.params.map { transform(assertSpecificValue(it.typePattern), scope) }
    val body = transform(ast.body, rootScope)

    val frameDescriptor = rootScope.frameDescriptor()

    return PFunctionDefinition(paramTypes, body, frameDescriptor)
  }

  // TODO: Remove this once I have pattern support
  private fun assertSpecificValue(pattern: Pattern?) = when (pattern) {
    is Pattern.SpecificValue -> pattern.value
    null -> throw EvalError("Parameters need to have explicit parameters for now", null)
    else -> throw EvalError("Patterns in parameter types are not supported for now", pattern.location)
  }

  private fun transform(ast: ASTValue, scope: LexicalScope): Value = when (ast) {
    is ASTValue.Boolean -> PLiteral(ast.value, RootType, ast.location)
    is ASTValue.Int -> PLiteral(ast.value, IntType, ast.location)
    is ASTValue.Float -> PLiteral(ast.value, RootType, ast.location)
    is ASTValue.String -> PLiteral(ast.value, RootType, ast.location)

    is ASTValue.Call -> PCall(
      target = transform(ast.target, scope),
      name = ast.name,
      arguments = ast.arguments.positional.map { transform(it, scope) }.toTypedArray()
    )

    is ASTValue.Let -> {
      val (innerScope, slot) = scope.newChildWithName(ast.name)
      val value = transform(ast.value, innerScope)
      val body = transform(ast.block, innerScope)

      PLet(ast.name, slot, value, body, ast.location)
    }

    is ASTValue.NameReference -> {
      val name = scope.find(ast.name) ?: throw EvalError("Could not find name ${ast.name}", ast.location)

      PReference(ast.name, name.isArgument, name.slotOrArgumentIndex, ast.location)
    }

    is ASTValue.Function -> transformFunctionDefinition(ast, scope)

    is ASTValue.Block -> TODO()
    is ASTValue.FunctionType -> TODO()
  }
}

internal class LexicalScope private constructor(
  private val frameBuilder: FrameDescriptor.Builder,
  private val parent: LexicalScope?,
  private val names: MutableMap<String, Name>
) {
  data class Name(val isArgument: Boolean, val slotOrArgumentIndex: Int)

  companion object {
    fun newRoot(params: List<String>, frameBuilder: FrameDescriptor.Builder): LexicalScope {
      val names = mutableMapOf<String, Name>()

      for (param in params.withIndex()) {
        names[param.value] = Name(isArgument = true, slotOrArgumentIndex = param.index)
      }

      return LexicalScope(frameBuilder, null, names)
    }
  }

  fun newChild() = LexicalScope(frameBuilder, this, mutableMapOf())

  fun newChildWithName(name: String): Pair<LexicalScope, Int> {
    val scope = LexicalScope(frameBuilder, this, mutableMapOf())
    val slot = scope.defineNew(name)

    return Pair(scope, slot)
  }

  fun defineNew(name: String): Int {
    val slot = frameBuilder.addSlot(FrameSlotKind.Object, name, null)

    names[name] = Name(isArgument = false, slot)

    return slot
  }

  fun find(name: String): Name? = names[name] ?: parent?.find(name)

  fun frameDescriptor(): FrameDescriptor = frameBuilder.build()
}