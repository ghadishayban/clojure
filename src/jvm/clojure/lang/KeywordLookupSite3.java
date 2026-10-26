package clojure.lang;

import java.lang.invoke.*;
import java.lang.reflect.Field;

public final class KeywordLookupSite3 extends MutableCallSite {

    final Keyword k;

    static final MethodHandles.Lookup mhLookup = MethodHandles.publicLookup();
    static final MethodHandle SAME_CLASS, FAULT;
    static final MethodType mt = MethodType.genericMethodType(1);

    static {
        try {
            var lk = MethodHandles.lookup();
            SAME_CLASS = lk.findStatic(KeywordLookupSite3.class, "sameClass",
                    MethodType.methodType(boolean.class, Class.class, Object.class));
            FAULT = lk.findVirtual(KeywordLookupSite3.class,"fault",
                    MethodType.methodType(Object.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    KeywordLookupSite3(Keyword k) {
        super(mt);
        this.k = k;
    }

    static boolean sameClass(Class kls, Object target) {
        return kls == target.getClass();
    }

    Object fault(Object target) {
        var fmh = fieldAccessor(target.getClass(), k);
        if (fmh != null) {
            var gwt = MethodHandles.guardWithTest(
                    SAME_CLASS.bindTo(target.getClass()),
                    fmh.asType(type()), // widen prim fields to reference
                    FAULT.bindTo(this)
            );
            setTarget(gwt);
        }
        return RT.get(target, k);
    }

    static MethodHandle fieldAccessor(Class kls, Keyword k) {
        for (Field f : kls.getFields()) {
            // kw->field selection logic intentionally dumb
            if (f.getName() == k.sym.name) {
                try {
                    return mhLookup.findGetter(kls, f.getName(), f.getType());
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return null;
    }

    public static CallSite bootstrap(MethodHandles.Lookup lk, String instructionName, MethodType type, String kw) {
        var cs = new KeywordLookupSite3(Keyword.intern(null, kw));
        cs.setTarget(FAULT.bindTo(cs));
        return cs;
    }

}
