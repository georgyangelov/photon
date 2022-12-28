package photon.types;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import photon.PObject;
import photon.compiler.core.Type;

import java.lang.reflect.Method;
import java.util.Map;

import static java.util.Map.entry;

@ExportLibrary(InteropLibrary.class)
public class IntType extends Type implements TruffleObject {
    public static final IntType instance;

    static {
        try {
            instance = new IntType();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private IntType() throws NoSuchMethodException {}

    private final Map<String, Method> _methods = Map.ofEntries(
        entry("+", IntType.class.getMethod("plus", PObject.class, PObject.class))
    );

    public static PObject<Integer> plus(PObject<Integer> a, PObject<Integer> b) {
        return new PObject<>(a.object + b.object, IntType.instance);
    }

    @Override
    public Type typeOf(VirtualFrame frame) {
        return RootType$.MODULE$;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return this;
    }

    @Override
    public final Map<String, Method> getMethods() { return _methods; }
}
