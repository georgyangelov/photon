package photon.objects;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import photon.Value;

@ExportLibrary(InteropLibrary.class)
public class PhotonInt extends Value implements TruffleObject {
    private final int value;

    public PhotonInt(int value) {
        this.value = value;
    }

    @ExportMessage
    final boolean isNumber() { return true; }

    @ExportMessage
    final boolean fitsInInt() { return true; }

    @ExportMessage
    final int asInt() { return value; }

    @ExportMessage final boolean fitsInByte() { return false; }
    @ExportMessage final boolean fitsInShort() { return false; }
    @ExportMessage final boolean fitsInLong() { return false; }
    @ExportMessage final boolean fitsInFloat() { return false; }
    @ExportMessage final boolean fitsInDouble() { return false; }

    @ExportMessage final byte asByte() throws UnsupportedMessageException { return (byte) 0; }
    @ExportMessage final short asShort() throws UnsupportedMessageException { return (short) 0; }
    @ExportMessage final long asLong() throws UnsupportedMessageException { return 0L; }
    @ExportMessage final float asFloat() throws UnsupportedMessageException { return 0.0F; }
    @ExportMessage final double asDouble() throws UnsupportedMessageException { return 0.0D; }

    @ExportMessage final boolean hasMembers() { return true; }
    @ExportMessage final boolean isMemberInvocable(String member) { return member.equals("+"); }
    @ExportMessage final Object getMembers(boolean includeInternal) throws UnsupportedMessageException { return null; }

    @ExportMessage
    final Object invokeMember(String member, Object... arguments) throws UnknownIdentifierException {
        if (member.equals("+")) {
            return value + ((PhotonInt)(arguments[0])).value;
        }

        throw UnknownIdentifierException.create(member);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return this;
    }
}
