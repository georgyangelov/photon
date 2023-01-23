package photon.compiler.nodes

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import photon.compiler.*
import photon.compiler.core.*
import photon.compiler.values.Closure
import photon.core.EvalError

class TemplateFunctionDefinitionNode(
  @Children @JvmField var argumentPatterns: Array<ParameterNode>,
  @Child @JvmField var returnType: PhotonNode?,
  @Child @JvmField var body: PhotonNode,

  private val typeFrameDescriptor: FrameDescriptor,
  private val typeRequiredCaptures: Array<NameCapture>,
  private val executionFrameDescriptor: FrameDescriptor,
  private val requiredCaptures: Array<NameCapture>,
  private val argumentCaptures: Array<ArgumentCapture>
): OperationNode() {
  class ParameterNode(
    @JvmField val name: String,
    @Child @JvmField var pattern: PatternNode
  ): Node()

  @CompilationFinal
  private var function: PhotonTemplateFunction? = null

  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    CompilerAsserts.neverPartOfCompilation()

    if (function != null) {
      // TODO: Location
      throw EvalError("Template function definition is already evaluated in a partial context", null)
    }

    val function = PhotonTemplateFunction(
      module = context.module,
      typeFrameDescriptor = typeFrameDescriptor,
      executionFrameDescriptor = executionFrameDescriptor,

      partialEvalFrame = frame.materialize(),
      argumentPatterns = argumentPatterns.map { Pair(it.name, it.pattern) },
      returnType = returnType,

      typeRequiredCaptures = typeRequiredCaptures,

      requiredCaptures = requiredCaptures,
      argumentCaptures = argumentCaptures,
      body = body
    )

    this.function = function

    type = function.type

    return this
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    assert(function != null)

    return Closure(function!!.type, frame.materialize())
  }
}