package photon;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.lang.reflect.InvocationTargetException;

@ExportLibrary(value = InteropLibrary.class, delegateTo = "object")
public final class PhotonObject<T> extends Value implements TruffleObject {
    public final T object;
    public final Type typeObject;

    public PhotonObject(T object, Type typeObject) {
        this.object = object;
        this.typeObject = typeObject;
    }

    @Override
    public Type typeOf(VirtualFrame frame) {
        return typeObject;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return this;
    }

    @ExportMessage
    final boolean hasMembers() { return true; }

    @ExportMessage final Object getMembers(boolean includeInternal) throws UnsupportedMessageException { return null; }

    @ExportMessage final boolean isMemberInvocable(String member) {
        return typeObject.methods().containsKey(member);
    }

    @ExportMessage
    final Object invokeMember(String member, Object... arguments) throws UnknownIdentifierException {
        var method = typeObject.methods().get(member);

        if (method != null) {
            try {
                return method.invoke(null, arguments);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        throw UnknownIdentifierException.create(member);
    }
}
