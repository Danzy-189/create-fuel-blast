package com.danzy.fuelblast.compat;

import com.danzy.fuelblast.FuelBlast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny reflection toolbox. Create, Create Aeronautics and Valkyrien Skies are soft
 * dependencies: the addon never compiles against them, so every access goes through
 * here and degrades gracefully when a mod is missing or changed its internals.
 */
public final class Reflect {
    private static final Map<String, Class<?>> CLASSES = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    public static Class<?> clazz(String name) {
        return CLASSES.computeIfAbsent(name, n -> {
            try {
                return Class.forName(n);
            } catch (Throwable t) {
                return Void.class;    // marker for "not present"
            }
        });
    }

    public static boolean present(String name) {
        return clazz(name) != Void.class;
    }

    public static Method method(Class<?> owner, String name, Class<?>... args) {
        String key = owner.getName() + "#" + name + args.length;
        Method cached = METHODS.get(key);
        if (cached != null) return cached;
        Class<?> c = owner;
        while (c != null && c != Object.class) {
            try {
                Method m = c.getDeclaredMethod(name, args);
                m.setAccessible(true);
                METHODS.put(key, m);
                return m;
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    /** Public method lookup with exact parameter types, including default interface methods. */
    public static Method publicMethod(Class<?> owner, String name, Class<?>... args) {
        if (owner == null) return null;
        try {
            Method method = owner.getMethod(name, args);
            try { method.setAccessible(true); } catch (Throwable ignored) {}
            return method;
        } catch (NoSuchMethodException ignored) {
            return method(owner, name, args);
        }
    }

    /** Public method lookup including inherited/default interface methods. */
    public static Method publicMethodByName(Class<?> owner, String name, int arity) {
        if (owner == null) return null;
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) {
                try { m.setAccessible(true); } catch (Throwable ignored) {}
                return m;
            }
        }
        return methodByName(owner, name, arity);
    }

    /** First method with the given name and arity, whatever the parameter types are. */
    public static Method methodByName(Class<?> owner, String name, int arity) {
        String key = owner.getName() + "~" + name + arity;
        Method cached = METHODS.get(key);
        if (cached != null) return cached;
        Class<?> c = owner;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == arity) {
                    m.setAccessible(true);
                    METHODS.put(key, m);
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    public static Object invoke(Method m, Object target, Object... args) {
        if (m == null) return null;
        try {
            return m.invoke(target, args);
        } catch (Throwable t) {
            FuelBlast.LOGGER.debug("Soft-compat call failed: {}", m, t);
            return null;
        }
    }

    public static Object field(Object target, String... candidates) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            for (String name : candidates) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /** First readable field whose runtime value is an instance of {@code type}. */
    public static Object fieldOfType(Object target, Class<?> type) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object value = f.get(target);
                    if (type.isInstance(value)) return value;
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /** First BlockPos-keyed map field whose values satisfy the predicate. */
    public static Object mapFieldMatching(Object target, java.util.function.Predicate<Object> valueTest) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object value = f.get(target);
                    if (value instanceof Map<?, ?> map && !map.isEmpty()
                            && valueTest.test(map.values().iterator().next())) {
                        return value;
                    }
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private Reflect() {}
}
