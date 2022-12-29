package photon.compiler

import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import photon.compiler.core.*
import photon.compiler.operations.*
import photon.compiler.types.IntType
import photon.core.EvalError
import photon.frontend.ASTValue

class ModuleReader(
  private val language: PhotonLanguage
) {
  private val functions = mutableListOf<PhotonFunction>()

  fun transformRoot(ast: ASTValue): PhotonRootNode {
    return transformFunctionBody(emptyList(), ast)
  }

  private fun transformFunctionBody(params: List<String>, ast: ASTValue): PhotonRootNode {
    val frameBuilder = FrameDescriptor.newBuilder()
    val rootScope = LexicalScope.newRoot(params, frameBuilder)

    val innerNode = transform(ast, rootScope)

    val frameDescriptor = frameBuilder.build()

    return PhotonRootNode(language, innerNode, frameDescriptor)
  }

  private fun transform(ast: ASTValue, scope: LexicalScope): Value = when (ast) {
    is ASTValue.Boolean -> PObject(ast.value, RootType)
    is ASTValue.Int -> PObject(ast.value, IntType)
    is ASTValue.Float -> TODO()
    is ASTValue.String -> PObject(ast.value, RootType)

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

    is ASTValue.Function -> {
      val paramNames = ast.params.map { it.inName }
      val body = transformFunctionBody(paramNames, ast.body)
      val fn = PhotonFunction(body)

      functions.add(fn)

      // TODO: FunctionType
      PObject(fn, RootType)
    }

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
}