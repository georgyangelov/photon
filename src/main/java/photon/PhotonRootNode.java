package photon;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import photon.compiler.core.Value;

public class PhotonRootNode extends RootNode {
    private final Value value;

    public PhotonRootNode(TruffleLanguage<?> language, Value value) {
        super(language);

        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return this.value.executeGeneric(frame);
    }
}
