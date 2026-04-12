package com.example.calculator;

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

    static String stripRedundantOuterParentheses(String text) {
        String required = requireText(text);
        int start = 0;
        int end = required.length() - 1;
        while (start <= end && Character.isWhitespace(required.charAt(start))) {
            start++;
        }
        while (end >= start && Character.isWhitespace(required.charAt(end))) {
            end--;
        }
        if (start > end || required.charAt(start) != '(' || required.charAt(end) != ')') {
            return required;
        }

        int openCount = 0;
        int cursor = start;
        while (cursor <= end && required.charAt(cursor) == '(') {
            openCount++;
            cursor++;
        }
        if (openCount == 0) {
            return required;
        }

        int[] leadingOpenIndexes = new int[openCount];
        for (int index = 0; index < openCount; index++) {
            leadingOpenIndexes[index] = start + index;
        }
        int[] matchingCloseIndexes = new int[openCount];
        int[] stack = new int[end - start + 1];
        int stackSize = 0;
        for (int index = start; index <= end; index++) {
            char current = required.charAt(index);
            if (current == '(') {
                stack[stackSize++] = index;
            } else if (current == ')') {
                if (stackSize == 0) {
                    throw new IllegalArgumentException("括号不匹配");
                }
                int openIndex = stack[--stackSize];
                int leadingIndex = openIndex - start;
                if (leadingIndex >= 0 && leadingIndex < openCount) {
                    matchingCloseIndexes[leadingIndex] = index;
                }
            }
        }
        if (stackSize != 0) {
            throw new IllegalArgumentException("括号不匹配");
        }

        int removableLayers = 0;
        while (removableLayers < openCount && matchingCloseIndexes[removableLayers] == end - removableLayers) {
            removableLayers++;
        }
        if (removableLayers == 0) {
            return required;
        }
        return required.substring(start + removableLayers, end - removableLayers + 1);
    }

    static BigDecimal toBigDecimal(RuntimeValue value) {
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
        if (raw instanceof BigDecimal) {
            return (BigDecimal) raw;
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
        return RuntimeValue.computed(toBigDecimal(left).add(toBigDecimal(right)));
    }

    static RuntimeValue subtract(RuntimeValue left, RuntimeValue right) {
        return RuntimeValue.computed(toBigDecimal(left).subtract(toBigDecimal(right)));
    }

    static RuntimeValue multiply(RuntimeValue left, RuntimeValue right) {
        return RuntimeValue.computed(toBigDecimal(left).multiply(toBigDecimal(right)));
    }

    static RuntimeValue divide(RuntimeValue left, RuntimeValue right) {
        BigDecimal divisor = toBigDecimal(right);
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("除数不能为0");
        }
        return RuntimeValue.computed(toBigDecimal(left).divide(divisor, DIVISION_CONTEXT));
    }

    static RuntimeValue negate(RuntimeValue value) {
        Object raw = value.raw();
        if (value.origin() == ValueOrigin.LITERAL) {
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
        return value;
    }

    static boolean toStandaloneBoolean(RuntimeValue value) {
        Object raw = value.raw();
        if (raw == null) {
            return false;
        }
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
        if (raw instanceof Number || raw instanceof CharSequence) {
            throw new IllegalArgumentException("比较表达式缺少比较运算符");
        }
        return true;
    }

    static boolean compare(RuntimeValue left, String operator, RuntimeValue right) {
        Object leftRaw = left.raw();
        Object rightRaw = right.raw();

        if ("==".equals(operator)) {
            return equality(leftRaw, rightRaw);
        }
        if ("!=".equals(operator)) {
            return !equality(leftRaw, rightRaw);
        }

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

    private static boolean equality(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (isNumericCandidate(left) && isNumericCandidate(right)) {
            return toBigDecimal(RuntimeValue.computed(left)).compareTo(toBigDecimal(RuntimeValue.computed(right))) == 0;
        }
        return Objects.equals(left, right);
    }

    private static boolean compareByOperator(int result, String operator) {
        return switch (operator) {
            case ">" -> result > 0;
            case "<" -> result < 0;
            case ">=" -> result >= 0;
            case "<=" -> result <= 0;
            default -> throw new IllegalArgumentException("不支持的比较运算符: " + operator);
        };
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
        Object raw = value.raw();
        if (raw == null) {
            return value.origin() == ValueOrigin.VARIABLE ? "false" : "null";
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

    enum ValueOrigin {
        LITERAL,
        VARIABLE,
        COMPUTED
    }

    static final class RuntimeValue {
        private final Object raw;
        private final ValueOrigin origin;

        private RuntimeValue(Object raw, ValueOrigin origin) {
            this.raw = raw;
            this.origin = origin;
        }

        static RuntimeValue literal(Object raw) {
            return new RuntimeValue(raw, ValueOrigin.LITERAL);
        }

        static RuntimeValue variable(Object raw) {
            return new RuntimeValue(raw, ValueOrigin.VARIABLE);
        }

        static RuntimeValue computed(Object raw) {
            return new RuntimeValue(raw, ValueOrigin.COMPUTED);
        }

        Object raw() {
            return raw;
        }

        ValueOrigin origin() {
            return origin;
        }

        Object toInvocationArgument() {
            if (origin == ValueOrigin.VARIABLE && raw instanceof Number && !(raw instanceof BigDecimal)) {
                return new BigDecimal(raw.toString());
            }
            return raw;
        }
    }
}
