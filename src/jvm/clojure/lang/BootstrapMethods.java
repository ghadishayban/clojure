package clojure.lang;

import java.util.Arrays;

import clojure.asm.ConstantDynamic;
import clojure.asm.Handle;
import clojure.asm.Opcodes;
import clojure.asm.Type;
import clojure.asm.commons.Method;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandle;

import static java.lang.invoke.MethodType.*;

import java.lang.invoke.MethodType;

public class BootstrapMethods {
    static final Handle CB_INVOKE = new Handle(Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/ConstantBootstraps",
            "invoke",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;",
            false);

    public static Keyword keywordUnk(String name) {
        return Keyword.intern(Symbol.intern(null, name));
    }

    static Handle staticHandle(Class klass, String method) {
        Method m = Method.getMethod(method);
        return new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(klass), m.getName(), m.getDescriptor(), false);
    }

    static ConstantDynamic condy(String instName, Class constant_type, Class owner, String method, Object... extraArgs) {
        String rtype = Type.getDescriptor(constant_type);

        Object[] args = new Object[extraArgs.length + 1];
        args[0] = staticHandle(owner, method);
        System.arraycopy(extraArgs, 0, args, 1, extraArgs.length);

        return new ConstantDynamic(instName, rtype, CB_INVOKE, args);
    }

}
