package photon.compiler

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import photon.compiler.core.Value
import photon.compiler.types.IntType
import photon.frontend.Lexer
import photon.frontend.Parser
import photon.frontend.Parser.Companion.BlankMacroHandler

class PhotonContext(
  val language: PhotonLanguage
) {
  companion object {
    private val REFERENCE: TruffleLanguage.ContextReference<PhotonContext> =
      TruffleLanguage.ContextReference.create(PhotonLanguage::class.java)

    fun current(): PhotonContext = REFERENCE.get(null)
    fun currentFor(node: Node): PhotonContext = REFERENCE.get(node)
  }

  val globals = listOf<Pair<String, Value>>(
    Pair("Int", IntType)
  )

  internal fun newGlobalLexicalScope(params: List<String> = emptyList()): LexicalScope {
    val frameDescriptorBuilder = FrameDescriptor.newBuilder()

    return LexicalScope.newRoot(params, frameDescriptorBuilder).apply {
      for ((name, _) in globals) {
        defineNew(name)
      }
    }
  }

  fun setGlobalsToFrame(frame: VirtualFrame) {
    for ((index, global) in globals.withIndex()) {
      frame.setObject(index, global.second)
    }
  }
}

@TruffleLanguage.Registration(id = "photon", name = "Photon")
class PhotonLanguage: TruffleLanguage<PhotonContext>() {
  companion object {
    const val id = "photon"
  }

  public override fun createContext(env: Env): PhotonContext {
    return PhotonContext(this)
  }

  public override fun parse(request: ParsingRequest): CallTarget {
    val source = request.source.characters
    val lexer = Lexer("test.y", source.toString())

    val parser = Parser(lexer, BlankMacroHandler)
    val rootAST = parser.parseRoot()

    val moduleReader = ModuleReader(PhotonContext.current())
    val root = moduleReader.read(rootAST)

    return root.callTarget
  }
}