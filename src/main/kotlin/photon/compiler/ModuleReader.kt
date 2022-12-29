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
  fun transformModule(ast: ASTValue): PhotonModule {
    val moduleBuilder = PhotonModule.Builder()

    val main = transformFunction(emptyList(), ast, moduleBuilder)

    return moduleBuilder.main(main).build(language)
  }

  private fun transformFunction(
    params: List<String>,
    ast: ASTValue,
    module: PhotonModule.Builder
  ): PhotonFunction {
    val frameBuilder = FrameDescriptor.newBuilder()
    val rootScope = LexicalScope.newRoot(params, frameBuilder)

    val innerNode = transform(ast, rootScope, module)

    val frameDescriptor = frameBuilder.build()
    val rootNode = PhotonFunctionRootNode(language, innerNode, frameDescriptor)

    return PhotonFunction(rootNode)
  }

  private fun transform(ast: ASTValue, scope: LexicalScope, module: PhotonModule.Builder): Value = when (ast) {
    is ASTValue.Boolean -> PLiteral(ast.value, RootType, ast.location)
    is ASTValue.Int -> PLiteral(ast.value, IntType, ast.location)
    is ASTValue.Float -> PLiteral(ast.value, RootType, ast.location)
    is ASTValue.String -> PLiteral(ast.value, RootType, ast.location)

    is ASTValue.Call -> PCall(
      target = transform(ast.target, scope, module),
      name = ast.name,
      arguments = ast.arguments.positional.map { transform(it, scope, module) }.toTypedArray()
    )

    is ASTValue.Let -> {
      val (innerScope, slot) = scope.newChildWithName(ast.name)
      val value = transform(ast.value, innerScope, module)
      val body = transform(ast.block, innerScope, module)

      PLet(ast.name, slot, value, body, ast.location)
    }

    is ASTValue.NameReference -> {
      val name = scope.find(ast.name) ?: throw EvalError("Could not find name ${ast.name}", ast.location)

      PReference(ast.name, name.isArgument, name.slotOrArgumentIndex, ast.location)
    }

    is ASTValue.Function -> {
      val paramNames = ast.params.map { it.inName }
      val fn = transformFunction(paramNames, ast.body, module)

      module.addFunction(fn)

      // TODO: FunctionType
      PLiteral(fn, RootType, ast.location)
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