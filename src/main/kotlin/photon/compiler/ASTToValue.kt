package photon.compiler

import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import photon.compiler.core.*
import photon.compiler.operations.*
import photon.compiler.types.IntType
import photon.core.EvalError
import photon.frontend.ASTValue

class ASTToValue(
  private val language: PhotonLanguage
) {
  class LexicalScope private constructor(
    private val frameBuilder: FrameDescriptor.Builder,
    private val parent: LexicalScope?,
    private val locals: MutableMap<String, Int>
  ) {
    companion object {
      fun newRoot(frameBuilder: FrameDescriptor.Builder) = LexicalScope(frameBuilder, null, mutableMapOf())
    }

    fun newChild() = LexicalScope(frameBuilder, this, mutableMapOf())

    fun newChildWithName(name: String): Pair<LexicalScope, Int> {
      val scope = LexicalScope(frameBuilder, this, mutableMapOf())
      val slot = scope.defineNew(name)

      return Pair(scope, slot)
    }

    fun defineNew(name: String): Int {
      val index = frameBuilder.addSlot(FrameSlotKind.Object, name, null)

      locals[name] = index

      return index
    }

    fun find(name: String): Int? = locals[name] ?: parent?.find(name)
  }

  fun transformFunctionBody(ast: ASTValue): PhotonRootNode {
    val frameBuilder = FrameDescriptor.newBuilder()
    val rootScope = LexicalScope.newRoot(frameBuilder)
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
      val slot = scope.find(ast.name) ?: throw EvalError("Could not find name ${ast.name}", ast.location)

      PLocalReference(ast.name, slot, ast.location)
    }

    is ASTValue.Block -> TODO()
    is ASTValue.Function -> TODO()
    is ASTValue.FunctionType -> TODO()
  }
}