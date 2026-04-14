package com.clmcat.commons.calculator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射工具：收集 public 方法并按实参与形参做宽松匹配。
 */
public class BeanUtils {

    /** 缓存每个类扫描到的 public 方法，避免重复反射。 */
    private final static Map<Class<?>, Map<String, List<Method>>> methodCache = new ConcurrentHashMap<>();
    /** 缓存每个类扫描到的 public 字段，避免重复反射。 */
    private final static Map<Class<?>, Map<String, Field>> fieldCache = new ConcurrentHashMap<>();

    /** 基本类型与包装类的映射，用于宽松重载匹配。 */
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
     * 获取当前类及父类链上所有可调用的 public 实例方法。
     *
     * @param clazz
     * @return 返回当前类下面public方法
     */
    public static Map<String, List<Method>> findPublicMethods(Class<?> clazz) {
        return methodCache.computeIfAbsent(clazz, BeanUtils::collectPublicMethods);
    }

    /**
     * 获取当前类及父类链上所有可读取的 public 实例字段。
     *
     * @param clazz 目标类型
     * @return 字段名到字段对象的映射
     */
    public static Map<String, Field> findPublicFields(Class<?> clazz) {
        return fieldCache.computeIfAbsent(clazz, BeanUtils::collectPublicFields);
    }

    private static Map<String, List<Method>> collectPublicMethods(Class<?> clazz) {
        Map<String, List<Method>> map = new HashMap<>();
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            int modifiers = method.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                String methodName = method.getName();
                List<Method> methodList = map.get(methodName);
                if (methodList == null) {
                    methodList = new ArrayList<>();
                    map.put(methodName, methodList);
                }
                methodList.add(method);
            }
        }
        return map;
    }

    private static Map<String, Field> collectPublicFields(Class<?> clazz) {
        Map<String, Field> map = new HashMap<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                int modifiers = field.getModifiers();
                if (Modifier.isPublic(modifiers)
                        && !Modifier.isStatic(modifiers)
                        && !map.containsKey(field.getName())) {
                    map.put(field.getName(), field);
                }
            }
            current = current.getSuperclass();
        }
        return map;
    }

    /**
     * 搜索匹配的 Method，支持以下宽松匹配规则：
     * <ul>
     *   <li>实参类型是形参类型的子类或实现类（isAssignableFrom）</li>
     *   <li>形参与实参互为基本类型及其包装类（例如 int 与 Integer 可互相匹配）</li>
     * </ul>
     *
     * @param clazz          目标类
     * @param methodName     方法名
     * @param parameterTypes 实参类型数组
     * @return 匹配的方法，若无匹配则返回 null
     */
    public static Method findFuzzyMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        Map<String, List<Method>> methodMap = findPublicMethods(clazz);
        List<Method> methodList = methodMap.get(methodName);
        if (methodList == null || methodList.isEmpty()) {
            return null;
        }

        // 优先精确匹配（类型完全相同或 isAssignableFrom）
        for (Method method : methodList) {
            if (isParameterTypesMatch(method.getParameterTypes(), parameterTypes, false)) {
                return method;
            }
        }

        // 再尝试模糊匹配（允许基本类型与包装类互转）
        for (Method method : methodList) {
            if (isParameterTypesMatch(method.getParameterTypes(), parameterTypes, true)) {
                return method;
            }
        }

        return null;
    }

    /**
     * 判断形参类型数组与实参类型数组是否匹配。
     *
     * @param formalTypes 形参类型
     * @param actualTypes 实参类型
     * @param fuzzy       是否开启模糊匹配（基本类型与包装类互转）
     * @return 是否匹配
     */
    private static boolean isParameterTypesMatch(Class<?>[] formalTypes, Class<?>[] actualTypes, boolean fuzzy) {
        if (formalTypes.length != actualTypes.length) {
            return false;
        }
        for (int i = 0; i < formalTypes.length; i++) {
            Class<?> formal = formalTypes[i];
            Class<?> actual = actualTypes[i];

            // 统一处理 actual 为 null 的情况
            if (actual == null) {
                if (formal.isPrimitive()) {
                    return false;
                }
                continue;
            }

            if (formal == actual || formal.isAssignableFrom(actual)) {
                continue;
            }
            if (fuzzy && isMatchingPrimitiveAndWrapper(formal, actual)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * 判断两个类型是否互为基本类型及其包装类。
     * 例如 int 与 Integer、boolean 与 Boolean 等。
     */
    private static boolean isMatchingPrimitiveAndWrapper(Class<?> type1, Class<?> type2) {
        Class<?> wrapped1 = primitiveWrapperMap.getOrDefault(type1, type1);
        Class<?> wrapped2 = primitiveWrapperMap.getOrDefault(type2, type2);
        return wrapped1 == wrapped2;
    }

}
