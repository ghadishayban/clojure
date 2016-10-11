package clojure.lang;

import java.util.Arrays;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

public class BootstrapMethods {

        private static final MethodHandle MAP;
        private static final MethodHandle MAP_UNIQUE;
        private static final MethodHandle ARRAY_MAP;
        private static final MethodHandle ARRAY_MAP_UNIQUE;

        public static final MethodHandle RT_MAP;
        public static final MethodHandle VECTOR;

        private static final MethodType IPERSISTENT_MAP_TYPE = MethodType.methodType(IPersistentMap.class, Object[].class);

        private static MethodHandle mapCreator(MethodHandle src) {
        	return src.asType(IPERSISTENT_MAP_TYPE).asVarargsCollector(Object[].class); 
        }

    static {
        try {
                MethodHandles.Lookup lk = MethodHandles.lookup();

                MethodType vectype = MethodType.methodType(IPersistentVector.class, Object[].class);
                VECTOR = lk.findStatic(RT.class, "vector", vectype);

                MethodType amaptype = MethodType.methodType(PersistentArrayMap.class, Object[].class);
                MethodType pmaptype = MethodType.methodType(PersistentHashMap.class, Object[].class);

                MAP              = mapCreator(lk.findStatic(PersistentHashMap.class, "createWithCheck", pmaptype));
                MAP_UNIQUE       = mapCreator(lk.findStatic(PersistentHashMap.class, "create", pmaptype));

                ARRAY_MAP        = mapCreator(lk.findStatic(PersistentArrayMap.class, "createWithCheck", amaptype));
                ARRAY_MAP_UNIQUE = mapCreator(lk.findConstructor(PersistentArrayMap.class, MethodType.methodType(void.class, Object[].class)));

                RT_MAP = lk.findStatic(RT.class, "map", IPERSISTENT_MAP_TYPE);
        } catch (Exception e) {
                System.err.println(e);
                throw new RuntimeException("Couldn't init bootstrapmethods");
        }
        }

	public static CallSite varExpr(MethodHandles.Lookup lk, String methodName, MethodType t, String varNs, String varName) {
		Var v = RT.var(varNs, varName);
                MethodHandle mh = Var.ROOT.bindTo(v);
		return new ConstantCallSite(mh);
	}

        public static CallSite keywordExpr(MethodHandles.Lookup lk, String methodName, MethodType t, String sym) {
                Keyword k = Keyword.intern(sym);
                return new ConstantCallSite(MethodHandles.constant(Keyword.class, k));
        }

        public static CallSite createVector(MethodHandles.Lookup lk, String methodName, MethodType t) {
                return new ConstantCallSite(VECTOR.asType(t));
        }

        public static CallSite createMap(MethodHandles.Lookup lk, String methodName, MethodType t) {
		            MethodHandle mh = t.parameterCount() <= PersistentArrayMap.HASHTABLE_THRESHOLD ?
			              ARRAY_MAP : MAP;
                return new ConstantCallSite(mh.asType(t));
        }

        public static CallSite createMapUnique(MethodHandles.Lookup lk, String methodName, MethodType t){
                MethodHandle mh = t.parameterCount() <= PersistentArrayMap.HASHTABLE_THRESHOLD ?
                    ARRAY_MAP_UNIQUE : MAP_UNIQUE;
                return new ConstantCallSite(mh.asType(t));
        }

        public static CallSite keywordInvoke(MethodHandles.Lookup lk, String methodName, MethodType t, String rep){
	    Keyword k = Keyword.intern(rep);
	    return KeywordInvokeCallSite.create(k);
	}
}
