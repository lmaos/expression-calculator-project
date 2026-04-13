package com.clmcat.commons.calculator;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ExpressionRuntimeSupport {

    private static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;

    private ExpressionRuntimeSupport() {
    }

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

    static BigDecimal toBigDecimal(RuntimeValue value) {
        ensurePresent(value);
        Object raw = value.raw();
        if (raw instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            return BigDecimal.valueOf(((Number) raw).longValue());
        }
        if (raw instanceof Float || raw instanceof Double) {
            return BigDecimal.valueOf(((Number) raw).doubleValue());
        }
        if (raw instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (raw instanceof CharSequence charSequence) {
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
        return RuntimeValue.computed(toBigDecimal(left).add(toBigDecimal(right)));
    }

    static RuntimeValue subtract(RuntimeValue left, RuntimeValue right) {
        ensurePresent(left);
        ensurePresent(right);
        return RuntimeValue.computed(toBigDecimal(left).subtract(toBigDecimal(right)));
    }

    static RuntimeValue multiply(RuntimeValue left, RuntimeValue right) {
        ensurePresent(left);
        ensurePresent(right);
        return RuntimeValue.computed(toBigDecimal(left).multiply(toBigDecimal(right)));
    }

    static RuntimeValue divide(RuntimeValue left, RuntimeValue right) {
        ensurePresent(left);
        ensurePresent(right);
        BigDecimal divisor = toBigDecimal(right);
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("除数不能为0");
        }
        return RuntimeValue.computed(toBigDecimal(left).divide(divisor, DIVISION_CONTEXT));
    }

    static RuntimeValue remainder(RuntimeValue left, RuntimeValue right) {
        ensurePresent(left);
        ensurePresent(right);
        BigDecimal divisor = toBigDecimal(right);
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("除数不能为0");
        }
        return RuntimeValue.computed(toBigDecimal(left).remainder(divisor));
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

    static RuntimeValue negate(RuntimeValue value) {
        ensurePresent(value);
        Object raw = value.raw();
        if (value.origin() == RuntimeValue.Origin.LITERAL) {
            if (raw instanceof Integer current) {
                return RuntimeValue.literal(-current);
            }
            if (raw instanceof Long current) {
                return RuntimeValue.literal(-current);
            }
            if (raw instanceof Float current) {
                return RuntimeValue.literal(-current);
            }
            if (raw instanceof Double current) {
                return RuntimeValue.literal(-current);
            }
            if (raw instanceof BigDecimal current) {
                return RuntimeValue.literal(current.negate());
            }
        }
        return RuntimeValue.computed(toBigDecimal(value).negate());
    }

    static RuntimeValue positive(RuntimeValue value) {
        ensurePresent(value);
        return value;
    }

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
         * 否则像 "a + b" 这种输入会悄悄被解释成 true/false，破坏题目约束。
         */
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof File file) {
            return file.exists();
        }
        if (raw instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (raw instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (raw.getClass().isArray()) {
            return Array.getLength(raw) > 0;
        }
        if (raw instanceof Number || raw instanceof CharSequence || raw instanceof Character) {
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
        if (value instanceof CharSequence charSequence) {
            try {
                new BigDecimal(charSequence.toString().trim());
                return true;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return false;
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
        Object[] converted = convertArguments(method, arguments);
        try {
            return RuntimeValue.computed(method.invoke(receiver, converted));
        } catch (IllegalAccessException | InvocationTargetException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocationTargetException
                    ? invocationTargetException.getTargetException()
                    : exception;
            throw new IllegalArgumentException("方法调用失败: " + receiver.getClass().getSimpleName() + "." + methodName,
                    cause);
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
            if (matched && totalScore < bestScore) {
                bestScore = totalScore;
                bestMatch = method;
            }
        }
        return bestMatch;
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

    static String formatForCalculation(RuntimeValue value) {
        ensurePresent(value);
        Object raw = value.raw();
        if (raw == null) {
            // 单独变量值为 null 时，题目要求按“假值”语义返回 false。
            return value.origin() == RuntimeValue.Origin.VARIABLE ? "false" : "null";
        }
        if (raw instanceof BigDecimal bigDecimal) {
            BigDecimal normalized = bigDecimal.stripTrailingZeros();
            return normalized.compareTo(BigDecimal.ZERO) == 0 ? "0" : normalized.toPlainString();
        }
        if (raw instanceof Boolean bool) {
            return Boolean.toString(bool);
        }
        if (raw instanceof File || raw instanceof Collection<?> || raw instanceof Map<?, ?> || raw.getClass().isArray()) {
            return Boolean.toString(toStandaloneBoolean(value));
        }
        return String.valueOf(raw);
    }

    private static void ensurePresent(RuntimeValue value) {
        if (value.isMissingVariable()) {
            throw new IllegalArgumentException("变量不存在: " + value.missingVariableName());
        }
    }
}
