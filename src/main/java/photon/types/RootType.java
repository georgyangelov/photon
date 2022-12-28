package photon.types;

import com.oracle.truffle.api.frame.VirtualFrame;
import photonscala.Type;

import java.lang.reflect.Method;
import java.util.Map;

public class RootType extends Type {
    public static RootType instance = new RootType();
    private RootType() {}

    @Override
    public Type typeOf(VirtualFrame frame) { return this; }

    @Override
    public Object executeGeneric(VirtualFrame frame) { return this; }

    @Override
    public Map<String, Method> methods() { return Map.of(); }
}
