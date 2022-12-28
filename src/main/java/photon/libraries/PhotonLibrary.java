package photon.libraries;

import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;

@GenerateLibrary
@GenerateLibrary.DefaultExport(IntegerMethods.class)
public abstract class PhotonLibrary extends Library {
    public Object invokeMember(Object receiver, String member, Object... arguments) {
        return false;
    }
}
