package clojure.lang;

import java.lang.invoke.*;
import java.lang.reflect.Field;

public final class KeywordLookupSite3 extends MutableCallSite {

    final Keyword k;

    static final MethodHandles.Lookup mhLookup = MethodHandles.publicLookup();
    static final MethodHandle SAME_CLASS, FAULT, IFN_OO;
    static final MethodType mt = MethodType.genericMethodType(1);

    static {
        try {
            var lk = MethodHandles.lookup();
            SAME_CLASS = lk.findStatic(KeywordLookupSite3.class, "sameClass",
                    MethodType.methodType(boolean.class, Class.class, Object.class));
            FAULT = lk.findVirtual(KeywordLookupSite3.class,"fault", mt);
            IFN_OO = lk.findVirtual(IFn.class,"invoke", mt);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private KeywordLookupSite3(Keyword k) {
        super(mt);
        this.k = k;
    }

    static boolean sameClass(Class kls, Object target) {
        return kls == target.getClass();
    }

    Object fault(Object target) {
        var f = ((IFnThunkable) target).getFnThunk(k);
        if (f != null) {
            var gwt = MethodHandles.guardWithTest(
                    SAME_CLASS.bindTo(target.getClass()),
                    IFN_OO.bindTo(f),
                    FAULT.bindTo(this)
            );
            setTarget(gwt);
        }
        return RT.get(target, k);
    }


    public static CallSite bootstrap(MethodHandles.Lookup lk, String instructionName, MethodType type, String nsname) {
        var cs = new KeywordLookupSite3(Keyword.intern(nsname));
        cs.setTarget(FAULT.bindTo(cs));
        return cs;
    }

}
