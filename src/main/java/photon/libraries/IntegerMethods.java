package photon.libraries;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(value = PhotonLibrary.class, receiverType = Integer.class)
public class IntegerMethods {
//    @ExportMessage
//    public static boolean hasMembers(Object receiver) {
//        return true;
//    }
//
//    @ExportMessage
//    public static boolean isMemberInvokable(Object receiver, String member) {
//        throw new RuntimeException("???");
//    }

    @ExportMessage
    public static Object invokeMember(Integer receiver, String member, Object... arguments) {
        throw new RuntimeException("???");
    }
}
