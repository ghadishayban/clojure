package clojure.lang;

import java.lang.invoke.CallSite;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

public class KeywordInvokeCallSite {

    static final MethodType getType = MethodType.methodType(Object.class, Object.class, Object.class);

    static CallSite create(Keyword kw) {
	CallSite cs = new MutableCallSite(MethodType.methodType(Object.class, Object.class));
	cs.setTarget(genericHandle(cs, kw));
	return cs;
    }

    // (if record relink rt_get)
    private static final MethodHandle genericHandle(CallSite cs, Keyword kw) {
	MethodHandle relinker = MethodHandles.insertArguments(RELINK, 0, cs, kw);

	return MethodHandles.guardWithTest(classGuard(IRecord.class),
					   relinker,
					   MethodHandles.insertArguments(RT_GET, 1, kw));
    }

    private static final MethodHandle classGuard(Class klass) {
	return INSTANCE_OF.bindTo(klass);
    }

    private static final MethodHandle fieldHandle(Object target, Keyword kw) {
	String sym = kw.sym.toString();
	Class klass = target.getClass();

	// use java.util.reflect to avoid having to know specific type of field
	try {
	    Field f = klass.getField(sym);
	    return MethodHandles.lookup().unreflectGetter(f).asType(getType);
	}
	catch (Exception e) {
	    return null;
	}
    }

    private static final Object relink(CallSite cs, Keyword kw, Object target) throws Throwable {
	Object result;
	if (target instanceof IRecord) {
	    MethodHandle field = fieldHandle(target, kw);

	    if (field != null) {
		result = field.invoke(target);

		// GWT ( if is_instance get_field relink)
		MethodHandle gwt = MethodHandles.guardWithTest(classGuard(target.getClass()),
							       field,
							       MethodHandles.insertArguments(RELINK,0,cs,kw));
	    cs.setTarget(gwt);
		return result;
	    }
	}

        result = RT.get(target, kw);
	cs.setTarget(genericHandle(cs, kw));
	return result;
    }

    private static final MethodHandle RT_GET;
    private static final MethodHandle RELINK;
    private static final MethodHandle INSTANCE_OF;

    static {
        try {
                MethodHandles.Lookup lk = MethodHandles.lookup();

                RT_GET = lk.findStatic(RT.class, "get", getType);

		MethodType lt = MethodType.methodType(Object.class, CallSite.class, Keyword.class, Object.class);
                RELINK = lk.findStatic(KeywordInvokeCallSite.class, "relink", lt);

	        INSTANCE_OF = lk.findVirtual(Class.class, "isInstance", MethodType.methodType(boolean.class, Object.class));

        } catch (Exception e) {
                System.err.println(e);
                throw new RuntimeException("Couldn't init bootstrapmethods");
        }
    }

}
