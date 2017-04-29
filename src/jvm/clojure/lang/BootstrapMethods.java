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
        private static final MethodHandle MAP_FROM_TEMPLATE;

        public static final MethodHandle RT_MAP;
        public static final MethodHandle VECTOR;

        private static final MethodType IPERSISTENT_MAP_TYPE = MethodType.methodType(IPersistentMap.class, Object[].class);

        private static MethodHandle mapCreator(MethodHandle src) {
        	return src.asType(IPERSISTENT_MAP_TYPE).asVarargsCollector(Object[].class); 
        }

        public static int[] slotsFromBitmap(int count, int bitmap) {

            final int mask = (1 << (count & 31)) - 1;
            bitmap = bitmap & mask;
            int[] ret = new int[Integer.bitCount(bitmap)];

            int slotIdx = 0;
            for (int i = 0; i < count; i++) {
                if ((bitmap & 1) == 1) {
                    ret[slotIdx] = i;
                    slotIdx++;
                }
                bitmap = bitmap >>> 1;
            }
            return ret;
        }

        public static IPersistentMap createMapFromTemplate(int[] slots, Object[] template, Object... dynArgs) {
            final int n = template.length;

            Object[] arr = new Object[n];
            System.arraycopy(template, 0, arr, 0, n);

            for (int i = 0; i < slots.length; i++) {
                arr[slots[i]] = dynArgs[i];
            }

            if (n < 16) {
                return new PersistentArrayMap(arr);
            } else {
                return PersistentHashMap.create(arr);
            }
        }

        // this *must* be varargs for proper bsm linkage
        public static CallSite kwMap(MethodHandles.Lookup lk, String methodName, MethodType t, Object... data) {
            int n               = (int) data[0];
            int constantsBitmap = (int) data[1];
            Object[] template = new Object[n];
            int i = 2;

            for (int slot : slotsFromBitmap(n, constantsBitmap)) {
                template[slot] = Keyword.intern((String) data[i]);
                i++;
            }

            // bit-invert the constant markers to get the dynamic markers
            MethodHandle mh = MethodHandles.insertArguments(MAP_FROM_TEMPLATE, 0,
                    slotsFromBitmap(n, ~constantsBitmap), template);

            return new ConstantCallSite(mh.asCollector(Object[].class, t.parameterCount()));
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

                MAP_FROM_TEMPLATE = lk.findStatic(BootstrapMethods.class, "createMapFromTemplate",
                        MethodType.methodType(IPersistentMap.class, int[].class, Object[].class, Object[].class));

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
