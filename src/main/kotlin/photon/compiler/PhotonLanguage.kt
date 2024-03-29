package photon.compiler

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.nodes.Node
import photon.compiler.core.RootType
import photon.compiler.macros.*
import photon.compiler.nodes.LiteralNode
import photon.compiler.types.*
import photon.compiler.values.TypeObject
import photon.compiler.types.classes.DefinitionsType
import photon.frontend.*

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
    Pair("def", DefMacro::defMacro),
    Pair("static", StaticMacro::staticMacro)
  )

  val macroHandler: MacroHandler = { keyword, parser, location ->
    val macro = macros[keyword]

    if (macro != null) {
      macro(parser, location)
    } else null
  }

  val globals = listOf(
    Pair("Any", LiteralNode(AnyType, RootType, null)),

    Pair("Type", LiteralNode(RootType, RootType, null)),
    Pair("Int", LiteralNode(IntType, RootType, null)),
    Pair("String", LiteralNode(StringType, RootType, null)),

    Pair("ClassBuilder", LiteralNode(DefinitionsType, RootType, null)),

    Pair("Class", LiteralNode(TypeObject(ClassObjectType), ClassObjectType, null)),
    Pair("Interface", LiteralNode(TypeObject(InterfaceObjectType), InterfaceObjectType, null)),

    Pair("Interop", LiteralNode(TypeObject(InteropMetaType), InteropMetaType, null))
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