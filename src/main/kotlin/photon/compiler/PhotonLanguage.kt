package photon.compiler

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import photon.frontend.Lexer
import photon.frontend.Parser
import photon.frontend.Parser.Companion.BlankMacroHandler

class PhotonContext

@TruffleLanguage.Registration(id = "photon", name = "Photon")
class PhotonLanguage: TruffleLanguage<PhotonContext>() {
  companion object {
    const val id = "photon"
  }

  public override fun createContext(env: Env): PhotonContext {
    return PhotonContext()
  }

  public override fun parse(request: ParsingRequest): CallTarget {
    val source = request.source.characters
    val lexer = Lexer("test.y", source.toString())

    val parser = Parser(lexer, BlankMacroHandler)
    val rootAST = parser.parseRoot()

    val moduleReader = ModuleReader(this)

    val root = moduleReader.transformRoot(rootAST)

    return root.callTarget
  }
}