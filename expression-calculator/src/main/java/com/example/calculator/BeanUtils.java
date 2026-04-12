package com.example.calculator;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bean 操作
 */
public class BeanUtils {

    private final static Map<Class<?>, Map<String, List<Method>>> methodCache = new ConcurrentHashMap<>();

    private final static Map<Class<?>, Class<?>> primitiveWrapperMap = new HashMap<>();
    static {
        primitiveWrapperMap.put(boolean.class, Boolean.class);
        primitiveWrapperMap.put(char.class, Character.class);
        primitiveWrapperMap.put(byte.class, Byte.class);
        primitiveWrapperMap.put(double.class, Double.class);
        primitiveWrapperMap.put(float.class, Float.class);
        primitiveWrapperMap.put(int.class, Integer.class);
        primitiveWrapperMap.put(long.class, Long.class);
        primitiveWrapperMap.put(short.class, Short.class);
    }
    /**
     * 获取当前类下面所有方法。
     * @param clazz
     * @return 返回当前类下面public方法
     */
    public static Map<String, List<Method>> findPublicMethods(Class<?> clazz) {
        Map<String, List<Method>> map = methodCache.get(clazz);
        if (map == null) {
            map = new HashMap<>();
            Class<?> c = clazz;
            while (c != null && c != Object.class) {
                Method[] methods = c.getDeclaredMethods();
                for (Method m : methods) {
                    // 不查询 static 方法 不查询抽象
                    int modifiers = m.getModifiers();
                    if (Modifier.isPublic(modifiers) &&
                            !Modifier.isStatic(modifiers)
                            && !Modifier.isAbstract(modifiers)
                            && !Modifier.isNative(modifiers)) {
                            String methodName = m.getName();
                            List<Method> methodList = map.get(methodName);
                            if (methodList == null) {
                                methodList = new ArrayList<>();
                                map.put(methodName, methodList);
                            }
                            methodList.add(m);
                    }
                }
                c = c.getSuperclass();
            }
        }
        return map;
    }

    /**
     * 搜索匹配的Method，类型匹配: int  (int , Integer)均匹配
     * @param clazz bean type
     * @param methodName 方法名字
     * @param parameterTypes 参数类型
     * @return 查找方法
     */
    public static Method findFuzzyMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        Map<String, List<Method>> methodMap = findPublicMethods(clazz);
        List<Method> methodList = methodMap.get(methodName);
        if (methodList == null) {
            return null;
        }
        for (Method m : methodList) {
            Class<?>[] methodParameterTypes = m.getParameterTypes();
            //  先进性一次完整匹配
            if (methodParameterTypes.length == parameterTypes.length) {
                boolean match = false;
                for (int i = 0; i < methodParameterTypes.length; i++) {
                    if (methodParameterTypes[i] == parameterTypes[i]) {
                        match =  true;
                    } else {
                        match =  false;
                        break;
                    }
                }
                if (match) {
                    return m;
                }
            }
        }

        boolean match = false;

        for (Method m : methodList) {
            Class<?>[] methodParameterTypes = m.getParameterTypes();
            // 是否进行模糊匹配
            boolean isFuzzyMatch = false;
            // 变量存在基本类型，和布尔类型时候，尝试一次模糊匹配。
            for (Class<?> parameterType : parameterTypes) {
                if (isFuzzyMatch = parameterType.isPrimitive()) {
                    break;
                }
            }
            //  先进性一次完整匹配, 在进行模糊匹配
            if (isFuzzyMatch) {
                for (int i = 0; i < methodParameterTypes.length; i++) {
                    Class<?> aClass = primitiveWrapperMap.getOrDefault(methodParameterTypes[i], methodParameterTypes[i]);
                    if (aClass == parameterTypes[i]) {
                        match = true;
                    } else {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return m;
                }
            }
        }
        return null;
    }

}
