package photon.operations;

import com.oracle.truffle.api.frame.VirtualFrame;
import photonscala.Type;
import photonscala.Value;
import photon.types.RootType;

public class PCall extends Value {
    @Override
    public Type typeOf(VirtualFrame frame) {
        return RootType.instance;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return null;
    }
}
