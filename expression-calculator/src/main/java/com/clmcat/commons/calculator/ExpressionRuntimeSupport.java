package com.clmcat.commons.calculator;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表达式运行时语义中心，统一处理数值转换、算术、位运算、比较、真值和方法调用。
 */
final class ExpressionRuntimeSupport {

    private static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;

    private ExpressionRuntimeSupport() {
    }

    // ----- 基础校验与深度限制 -----
    static String requireText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        return text;
    }

    static void ensureWithinDepthLimit(String text, int maxDepth) {
        if (maxDepth < 0) {
            return;
        }
        int depth = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '(') {
                depth++;
                if (depth > maxDepth) {
                    throw new IllegalArgumentException("表达式层级超过限制: " + maxDepth);
                }
            } else if (current == ')') {
                depth--;
                if (depth < 0) {
                    throw new IllegalArgumentException("括号不匹配");
                }
            }
        }
        if (depth != 0) {
            throw new IllegalArgumentException("括号不匹配");
        }
    }

    // ----- 数值归一化与基础算术 -----
    static BigDecimal toBigDecimal(RuntimeValue value) {
        ensurePresent(value);
        Object raw = value.raw();
        if (raw instanceof BigDecimal) {
            return (BigDecimal) raw;
        }
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            return BigDecimal.valueOf(((Number) raw).longValue());
        }
        if (raw instanceof Float || raw instanceof Double) {
            return BigDecimal.valueOf(((Number) raw).doubleValue());
        }
        if (raw instanceof Number) {
            return new BigDecimal(((Number) raw).toString());
        }
        if (raw instanceof CharSequence) {
            CharSequence charSequence = (CharSequence) raw;
            try {
                return new BigDecimal(charSequence.toString().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("值不是数字: " + charSequence);
            }
        }
        if (raw == null) {
            throw new IllegalArgumentException("值不能为空");
        }
        throw new IllegalArgumentException("值不是数字: " + raw);
    }

    static RuntimeValue add(RuntimeValue left, RuntimeValue right) {
        ensurePresent(left);
        ensurePresent(right);
        if (shouldConcatenate(left.raw(), right.raw())) {
            return RuntimeValue.computed(String.valueOf(left.raw()) + String.valueOf(right.raw()));
        }
        return NumericArithmetic.add(left, right);
    }

    static RuntimeValue subtract(RuntimeValue left, RuntimeValue right) {
        ensurePresent(left);
        ensurePresent(right);
        return NumericArithmetic.subtract(left, right);
    }

    static RuntimeValue multiply(RuntimeValue left, RuntimeValue right) {
        ensurePresent(left);
        ensurePresent(right);
        return NumericArithmetic.multiply(left, right);
    }

    static RuntimeValue divide(RuntimeValue left, RuntimeValue right) {
        ensurePresent(left);
        ensurePresent(right);
        return NumericArithmetic.divide(left, right);
    }

    // ----- 扩展算术与位运算 -----
    static RuntimeValue remainder(RuntimeValue left, RuntimeValue right) {
        ensurePresent(left);
        ensurePresent(right);
        return NumericArithmetic.remainder(left, right);
    }

    static RuntimeValue power(RuntimeValue left, RuntimeValue right) {
        ensurePresent(left);
        ensurePresent(right);
        BigDecimal base = toBigDecimal(left);
        BigDecimal exponent = toBigDecimal(right);
        BigDecimal normalizedExponent = exponent.stripTrailingZeros();
        if (normalizedExponent.scale() <= 0 && normalizedExponent.compareTo(BigDecimal.ZERO) >= 0) {
            try {
                return RuntimeValue.computed(base.pow(normalizedExponent.intValueExact()));
            } catch (ArithmeticException exception) {
                // 回退到 double 幂计算，允许超大整数指数继续走近似结果。
            }
        }
        double result = Math.pow(base.doubleValue(), exponent.doubleValue());
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new IllegalArgumentException("幂运算结果无效");
        }
        return RuntimeValue.computed(BigDecimal.valueOf(result));
    }

    static RuntimeValue bitwiseAnd(RuntimeValue left, RuntimeValue right) {
        return RuntimeValue.computed(toIntegralLong(left) & toIntegralLong(right));
    }

    static RuntimeValue bitwiseOr(RuntimeValue left, RuntimeValue right) {
        return RuntimeValue.computed(toIntegralLong(left) | toIntegralLong(right));
    }

    static RuntimeValue bitwiseXor(RuntimeValue left, RuntimeValue right) {
        return RuntimeValue.computed(toIntegralLong(left) ^ toIntegralLong(right));
    }

    static RuntimeValue bitwiseNot(RuntimeValue value) {
        return RuntimeValue.computed(~toIntegralLong(value));
    }

    static RuntimeValue shiftLeft(RuntimeValue left, RuntimeValue right) {
        return RuntimeValue.computed(toIntegralLong(left) << toShiftDistance(right));
    }

    static RuntimeValue shiftRight(RuntimeValue left, RuntimeValue right) {
        return RuntimeValue.computed(toIntegralLong(left) >> toShiftDistance(right));
    }

    static RuntimeValue unsignedShiftRight(RuntimeValue left, RuntimeValue right) {
        return RuntimeValue.computed(toIntegralLong(left) >>> toShiftDistance(right));
    }

    static RuntimeValue unsignedShiftLeft(RuntimeValue left, RuntimeValue right) {
        // 64 位定长语义下，左移的位模式与是否按无符号解释无关，这里保留 <<< 作为 DSL 对称别名。
        return RuntimeValue.computed(toIntegralLong(left) << toShiftDistance(right));
    }

    static RuntimeValue negate(RuntimeValue value) {
        ensurePresent(value);
        return NumericArithmetic.negate(value);
    }

    static RuntimeValue positive(RuntimeValue value) {
        ensurePresent(value);
        return value;
    }

    static RuntimeValue logicalNot(RuntimeValue value) {
        return RuntimeValue.computed(!toStandaloneBoolean(value));
    }

    static RuntimeValue indexAccess(RuntimeValue target, RuntimeValue index) {
        ensurePresent(target);
        ensurePresent(index);
        Object receiver = target.raw();
        if (receiver == null) {
            throw new IllegalArgumentException("下标访问失败: 目标对象为空");
        }
        if (receiver instanceof Map<?, ?>) {
            return RuntimeValue.computed(((Map<?, ?>) receiver).get(index.raw()));
        }

        int position = toIndex(index);
        if (receiver instanceof List<?>) {
            List<?> list = (List<?>) receiver;
            if (position < 0 || position >= list.size()) {
                throw new IllegalArgumentException("下标越界: " + position);
            }
            return RuntimeValue.computed(list.get(position));
        }
        if (receiver.getClass().isArray()) {
            int length = Array.getLength(receiver);
            if (position < 0 || position >= length) {
                throw new IllegalArgumentException("下标越界: " + position);
            }
            return RuntimeValue.computed(Array.get(receiver, position));
        }
        if (receiver instanceof CharSequence) {
            CharSequence charSequence = (CharSequence) receiver;
            if (position < 0 || position >= charSequence.length()) {
                throw new IllegalArgumentException("下标越界: " + position);
            }
            return RuntimeValue.computed(charSequence.charAt(position));
        }
        throw new IllegalArgumentException("类型不支持下标访问: " + receiver.getClass().getSimpleName());
    }

    // ----- 布尔真值与比较 -----
    static boolean toStandaloneBoolean(RuntimeValue value) {
        ensurePresent(value);
        Object raw = value.raw();
        if (raw == null) {
            return false;
        }
        /*
         * 真值规则按题目要求固定：
         *
         * null            -> false
         * boolean         -> 自身
         * file            -> exists()
         * collection/map  -> 非空为 true
         * array           -> 长度 > 0
         *
         * 数字 / 字符串不允许直接当 compareCalculation 的最终结果，
         * 否则像 "a + b" 这种计算结果会悄悄被解释成 true/false，破坏题目约束。
         *
         * 但“单独变量”的语义单独放宽：
         * - 变量为 null -> false
         * - 变量为非 null -> true
         *
         * File / Collection / Map / Array 仍优先走各自的专用真值规则。
         */
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof File) {
            return ((File) raw).exists();
        }
        if (raw instanceof Collection) {
            return !((Collection<?>) raw).isEmpty();
        }
        if (raw instanceof Map) {
            return !((Map<?, ?>) raw).isEmpty();
        }
        if (raw.getClass().isArray()) {
            return Array.getLength(raw) > 0;
        }
        if (raw instanceof Number || raw instanceof CharSequence || raw instanceof Character) {
            if (value.origin() == RuntimeValue.Origin.VARIABLE) {
                return true;
            }
            throw new IllegalArgumentException("比较表达式缺少比较运算符");
        }
        return true;
    }

    static boolean compare(RuntimeValue left, String operator, RuntimeValue right) {
        if ("==".equals(operator)) {
            return equality(left, right);
        }
        if ("!=".equals(operator)) {
            return !equality(left, right);
        }

        ensurePresent(left);
        ensurePresent(right);
        Object leftRaw = left.raw();
        Object rightRaw = right.raw();
        if (leftRaw == null || rightRaw == null) {
            throw new IllegalArgumentException("比较表达式格式错误");
        }
        if (isNumericCandidate(leftRaw) && isNumericCandidate(rightRaw)) {
            int result = toBigDecimal(RuntimeValue.computed(leftRaw)).compareTo(toBigDecimal(RuntimeValue.computed(rightRaw)));
            return compareByOperator(result, operator);
        }
        if (leftRaw instanceof Boolean || rightRaw instanceof Boolean) {
            throw new IllegalArgumentException("布尔值不支持大小比较");
        }
        if (leftRaw instanceof Comparable<?> && rightRaw instanceof Comparable<?>) {
            if (!leftRaw.getClass().isAssignableFrom(rightRaw.getClass())
                    && !rightRaw.getClass().isAssignableFrom(leftRaw.getClass())) {
                throw new IllegalArgumentException("比较表达式格式错误");
            }
            @SuppressWarnings("unchecked")
            Comparable<Object> comparable = (Comparable<Object>) leftRaw;
            return compareByOperator(comparable.compareTo(rightRaw), operator);
        }
        throw new IllegalArgumentException("比较表达式格式错误");
    }

    private static boolean equality(RuntimeValue left, RuntimeValue right) {
        Object leftRaw = left.isMissingVariable() ? null : left.raw();
        Object rightRaw = right.isMissingVariable() ? null : right.raw();
        if (leftRaw == null || rightRaw == null) {
            return leftRaw == rightRaw;
        }
        if (isNumericCandidate(leftRaw) && isNumericCandidate(rightRaw)) {
            return toBigDecimal(RuntimeValue.computed(leftRaw)).compareTo(toBigDecimal(RuntimeValue.computed(rightRaw))) == 0;
        }
        return Objects.equals(leftRaw, rightRaw);
    }

    private static boolean shouldConcatenate(Object left, Object right) {
        return (isStringLike(left) || isStringLike(right))
                && !(isNumericCandidate(left) && isNumericCandidate(right));
    }

    private static boolean isStringLike(Object value) {
        return value instanceof CharSequence || value instanceof Character;
    }

    private static long toIntegralLong(RuntimeValue value) {
        ensurePresent(value);
        Object raw = value.raw();
        try {
            return toBigDecimal(value).stripTrailingZeros().longValueExact();
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IllegalArgumentException("位运算只支持整数: " + raw, exception);
        }
    }

    private static int toShiftDistance(RuntimeValue value) {
        return (int) (toIntegralLong(value) & 63L);
    }

    private static boolean compareByOperator(int result, String operator) {
        switch (operator) {
            case ">":
                return result > 0;
            case "<":
                return result < 0;
            case ">=":
                return result >= 0;
            case "<=":
                return result <= 0;
            default:
                throw new IllegalArgumentException("不支持的比较运算符: " + operator);
        }
    }

    private static boolean isNumericCandidate(Object value) {
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof CharSequence) {
            CharSequence charSequence = (CharSequence) value;
            try {
                new BigDecimal(charSequence.toString().trim());
                return true;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return false;
    }

    // ----- 公开字段读取、方法调用与重载匹配 -----
    static RuntimeValue accessField(RuntimeValue receiverValue, String fieldName) {
        ensurePresent(receiverValue);
        Object receiver = receiverValue.raw();
        if (receiver == null) {
            throw new IllegalArgumentException("字段访问失败: 对象为空, 字段: " + fieldName);
        }
        if ("length".equals(fieldName) && receiver.getClass().isArray()) {
            return RuntimeValue.computed(Array.getLength(receiver));
        }
        Field field = BeanUtils.findPublicFields(receiver.getClass()).get(fieldName);
        if (field == null) {
            throw new IllegalArgumentException(
                    "字段访问失败，不存在公开字段: " + receiver.getClass().getSimpleName() + "." + fieldName);
        }
        try {
            return RuntimeValue.computed(field.get(receiver));
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException(
                    "字段访问失败: " + receiver.getClass().getSimpleName() + "." + fieldName,
                    exception);
        }
    }

    static RuntimeValue invokeMethod(RuntimeValue receiverValue, String methodName, List<RuntimeValue> arguments) {
        ensurePresent(receiverValue);
        Object receiver = receiverValue.raw();
        if (receiver == null) {
            throw new IllegalArgumentException("方法调用失败: 对象为空, 方法: " + methodName);
        }
        Method method = resolveMethod(receiver.getClass(), methodName, arguments);
        if (method == null) {
            throw new IllegalArgumentException("方法调用失败，参数类型不匹配: " + receiver.getClass().getSimpleName() + "."
                    + methodName);
        }
        method = findInvocableMethod(receiver.getClass(), method);
        Object[] converted = convertArguments(method, arguments);
        try {
            return RuntimeValue.computed(method.invoke(receiver, converted));
        } catch (IllegalAccessException | InvocationTargetException exception) {
            Throwable cause = exception;
            if (exception instanceof InvocationTargetException) {
                cause = ((InvocationTargetException) exception).getTargetException();
            }
            throw new IllegalArgumentException("方法调用失败: " + receiver.getClass().getSimpleName() + "." + methodName, cause);
        }
    }

    private static Method resolveMethod(Class<?> receiverType, String methodName, List<RuntimeValue> arguments) {
        List<Method> methods = BeanUtils.findPublicMethods(receiverType).get(methodName);
        if (methods == null) {
            return null;
        }
        /*
         * 这里不是“找到一个能用的方法”就结束，而是要挑最合适的那个：
         *
         * score = 0  精确类型命中
         * score = 1  父类型/接口命中
         * score < 0  不匹配
         *
         * 总分越小越优先，避免在重载方法里选错目标。
         */
        Method bestMatch = null;
        int bestScore = Integer.MAX_VALUE;
        for (Method method : methods) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != arguments.size()) {
                continue;
            }
            int totalScore = 0;
            boolean matched = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                int score = matchScore(arguments.get(index), parameterTypes[index]);
                if (score < 0) {
                    matched = false;
                    break;
                }
                totalScore += score;
            }
            if (!isReflectivelyAccessible(method)) {
                totalScore += 100;
            }
            if (matched && totalScore < bestScore) {
                bestScore = totalScore;
                bestMatch = method;
            }
        }
        return bestMatch;
    }

    private static boolean isReflectivelyAccessible(Method method) {
        Class<?> declaringClass = method.getDeclaringClass();
        return declaringClass.isInterface() || Modifier.isPublic(declaringClass.getModifiers());
    }

    private static Method findInvocableMethod(Class<?> receiverType, Method method) {
        if (isReflectivelyAccessible(method)) {
            return method;
        }
        Method accessibleMethod = findAccessibleMethod(receiverType, method.getName(), method.getParameterTypes());
        return accessibleMethod == null ? method : accessibleMethod;
    }

    private static Method findAccessibleMethod(Class<?> receiverType, String methodName, Class<?>[] parameterTypes) {
        Method interfaceMethod = findAccessibleMethodOnInterfaces(receiverType, methodName, parameterTypes);
        if (interfaceMethod != null) {
            return interfaceMethod;
        }
        Class<?> current = receiverType;
        while (current != null) {
            if (Modifier.isPublic(current.getModifiers())) {
                try {
                    return current.getMethod(methodName, parameterTypes);
                } catch (NoSuchMethodException exception) {
                    // 继续向上查找
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findAccessibleMethodOnInterfaces(Class<?> type, String methodName, Class<?>[] parameterTypes) {
        if (type == null) {
            return null;
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            Method interfaceMethod = findAccessibleMethodInInterface(interfaceType, methodName, parameterTypes);
            if (interfaceMethod != null) {
                return interfaceMethod;
            }
        }
        return findAccessibleMethodOnInterfaces(type.getSuperclass(), methodName, parameterTypes);
    }

    private static Method findAccessibleMethodInInterface(Class<?> interfaceType, String methodName, Class<?>[] parameterTypes) {
        if (Modifier.isPublic(interfaceType.getModifiers())) {
            try {
                return interfaceType.getMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException exception) {
                // 继续在父接口中查找
            }
        }
        for (Class<?> parentInterface : interfaceType.getInterfaces()) {
            Method interfaceMethod = findAccessibleMethodInInterface(parentInterface, methodName, parameterTypes);
            if (interfaceMethod != null) {
                return interfaceMethod;
            }
        }
        return null;
    }

    private static int matchScore(RuntimeValue value, Class<?> parameterType) {
        Class<?> targetType = wrap(parameterType);
        Object argument = value.toInvocationArgument();
        if (argument == null) {
            return parameterType.isPrimitive() ? -1 : 10;
        }
        Class<?> argumentType = argument.getClass();
        if (targetType == argumentType) {
            return 0;
        }
        if (targetType.isAssignableFrom(argumentType)) {
            return 1;
        }
        if (parameterType.isPrimitive() && wrap(parameterType) == argumentType) {
            return 0;
        }
        return -1;
    }

    private static Object[] convertArguments(Method method, List<RuntimeValue> arguments) {
        Object[] converted = new Object[arguments.size()];
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int index = 0; index < arguments.size(); index++) {
            Object argument = arguments.get(index).toInvocationArgument();
            if (argument == null && parameterTypes[index].isPrimitive()) {
                throw new IllegalArgumentException("方法调用失败，参数类型不匹配: " + method.getName());
            }
            converted[index] = argument;
        }
        return converted;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    // ----- 结果格式化 -----
    static String formatForCalculation(RuntimeValue value) {
        ensurePresent(value);
        Object raw = value.raw();
        if (raw == null) {
            // 单独变量值为 null 时，题目要求按“假值”语义返回 false。
            return value.origin() == RuntimeValue.Origin.VARIABLE ? "false" : "null";
        }
        if (raw instanceof BigDecimal) {
            BigDecimal bigDecimal = (BigDecimal) raw;
            BigDecimal normalized = bigDecimal.stripTrailingZeros();
            return normalized.compareTo(BigDecimal.ZERO) == 0 ? "0" : normalized.toPlainString();
        }
        if (raw instanceof Boolean) {
            return Boolean.toString((Boolean) raw);
        }
        if (raw instanceof File || raw instanceof Collection || raw instanceof Map || raw.getClass().isArray()) {
            return Boolean.toString(toStandaloneBoolean(value));
        }
        return String.valueOf(raw);
    }

    static Object toEvaluateResult(RuntimeValue value) {
        return value.isMissingVariable() ? null : value.raw();
    }

    private static int toIndex(RuntimeValue index) {
        Object raw = index.raw();
        if (!(raw instanceof Number) && !(raw instanceof CharSequence)) {
            throw new IllegalArgumentException("下标必须是整数: " + raw);
        }
        try {
            return toBigDecimal(index).stripTrailingZeros().intValueExact();
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IllegalArgumentException("下标必须是整数: " + raw, exception);
        }
    }

    private static void ensurePresent(RuntimeValue value) {
        if (value.isMissingVariable()) {
            throw new IllegalArgumentException("变量不存在: " + value.missingVariableName());
        }
    }
}
