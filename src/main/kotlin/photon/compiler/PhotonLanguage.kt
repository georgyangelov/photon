package photon.compiler

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.nodes.Node
import photon.compiler.core.RootType
import photon.compiler.macros.ClassMacro
import photon.compiler.macros.DefMacro
import photon.compiler.nodes.LiteralNode
import photon.compiler.types.ClassObjectType
import photon.compiler.types.IntType
import photon.compiler.values.ClassBuilderType
import photon.frontend.*
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

  private val macros = mapOf(
    Pair("class", ClassMacro::classMacro),
    Pair("object", ClassMacro::objectMacro),
    Pair("interface", ClassMacro::interfaceMacro),
    Pair("def", DefMacro::defMacro)
  )

  val macroHandler: MacroHandler = { keyword, parser, location ->
    val macro = macros[keyword]

    if (macro != null) {
      macro(parser, location)
    } else null
  }

  val globals = listOf(
    Pair("Type", LiteralNode(RootType, RootType, null)),
    Pair("Int", LiteralNode(IntType, RootType, null)),

    // TODO: `value` should not be 1
    Pair("Class", LiteralNode(1, ClassObjectType, null)),
    Pair("ClassBuilder", LiteralNode(ClassBuilderType, RootType, null))
  )

  internal fun newGlobalLexicalScope(params: List<String> = emptyList()): LexicalScope.FunctionScope {
    return LexicalScope.newFunction(params).apply {
      for ((name, _) in globals) {
        defineName(name)
      }
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

    val context = PhotonContext.current()

    val parser = Parser(lexer, context.macroHandler)
    val rootAST = parser.parseRoot()

    val moduleReader = ModuleReader(context)
    val root = moduleReader.read(rootAST)

    return root.callTarget
  }
}