package photon;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import photon.compiler.ASTToValue;
import photon.compiler.core.Value;
import photon.frontend.ASTValue;
import photon.frontend.Lexer;
import photon.frontend.Parser;

class PhotonContext {}

@TruffleLanguage.Registration(id = "photon", name = "Photon")
public class PhotonLanguage extends TruffleLanguage<PhotonContext> {
    public static final String id = "photon";

    public PhotonContext createContext(final TruffleLanguage.Env env) {
        return new PhotonContext();
    }

    public CallTarget parse(final TruffleLanguage.ParsingRequest request) {
        CharSequence source = request.getSource().getCharacters();
        Lexer lexer = new Lexer("test.y", source.toString());
        Parser parser = new Parser(lexer, Parser.Companion.getBlankMacroHandler());
        ASTValue rootAST = parser.parseRoot();
        Value value = ASTToValue.INSTANCE.transform(rootAST);
        PhotonRootNode root = new PhotonRootNode(this, value);
        return root.getCallTarget();
    }
}
