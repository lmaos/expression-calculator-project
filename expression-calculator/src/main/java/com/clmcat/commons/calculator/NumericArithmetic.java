package com.clmcat.commons.calculator;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 数值算术工具，负责在整数结果可表达时保留更精确的整数类型。
 */
final class NumericArithmetic {

    private static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;

    private NumericArithmetic() {
    }

    static RuntimeValue add(RuntimeValue left, RuntimeValue right) {
        if (isIntegralPair(left.raw(), right.raw())) {
            try {
                long result = Math.addExact(asLong(left.raw()), asLong(right.raw()));
                return RuntimeValue.computed(narrowIntegral(result, prefersLong(left.raw(), right.raw())));
            } catch (ArithmeticException exception) {
                // long 溢出后回退到 BigDecimal，避免错误截断。
            }
        }
        return RuntimeValue.computed(ExpressionRuntimeSupport.toBigDecimal(left).add(ExpressionRuntimeSupport.toBigDecimal(right)));
    }

    static RuntimeValue subtract(RuntimeValue left, RuntimeValue right) {
        if (isIntegralPair(left.raw(), right.raw())) {
            try {
                long result = Math.subtractExact(asLong(left.raw()), asLong(right.raw()));
                return RuntimeValue.computed(narrowIntegral(result, prefersLong(left.raw(), right.raw())));
            } catch (ArithmeticException exception) {
                // long 溢出后回退到 BigDecimal，避免错误截断。
            }
        }
        return RuntimeValue.computed(ExpressionRuntimeSupport.toBigDecimal(left)
                .subtract(ExpressionRuntimeSupport.toBigDecimal(right)));
    }

    static RuntimeValue multiply(RuntimeValue left, RuntimeValue right) {
        if (isIntegralPair(left.raw(), right.raw())) {
            try {
                long result = Math.multiplyExact(asLong(left.raw()), asLong(right.raw()));
                return RuntimeValue.computed(narrowIntegral(result, prefersLong(left.raw(), right.raw())));
            } catch (ArithmeticException exception) {
                // long 溢出后回退到 BigDecimal，避免错误截断。
            }
        }
        return RuntimeValue.computed(ExpressionRuntimeSupport.toBigDecimal(left)
                .multiply(ExpressionRuntimeSupport.toBigDecimal(right)));
    }

    static RuntimeValue divide(RuntimeValue left, RuntimeValue right) {
        BigDecimal divisor = ExpressionRuntimeSupport.toBigDecimal(right);
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("除数不能为0");
        }
        if (isIntegralPair(left.raw(), right.raw())) {
            long dividendLong = asLong(left.raw());
            long divisorLong = asLong(right.raw());
            if (dividendLong % divisorLong == 0L) {
                long result = dividendLong / divisorLong;
                return RuntimeValue.computed(narrowIntegral(result, prefersLong(left.raw(), right.raw())));
            }
        }
        return RuntimeValue.computed(ExpressionRuntimeSupport.toBigDecimal(left).divide(divisor, DIVISION_CONTEXT));
    }

    static RuntimeValue remainder(RuntimeValue left, RuntimeValue right) {
        BigDecimal divisor = ExpressionRuntimeSupport.toBigDecimal(right);
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("除数不能为0");
        }
        if (isIntegralPair(left.raw(), right.raw())) {
            long result = asLong(left.raw()) % asLong(right.raw());
            return RuntimeValue.computed(narrowIntegral(result, prefersLong(left.raw(), right.raw())));
        }
        return RuntimeValue.computed(ExpressionRuntimeSupport.toBigDecimal(left).remainder(divisor));
    }

    static RuntimeValue negate(RuntimeValue value) {
        Object raw = value.raw();
        if (raw instanceof Integer || raw instanceof Short || raw instanceof Byte) {
            int number = ((Number) raw).intValue();
            if (number == Integer.MIN_VALUE) {
                return RuntimeValue.computed(Long.valueOf(-((long) number)));
            }
            return RuntimeValue.computed(-number);
        }
        if (raw instanceof Long) {
            long number = (Long) raw;
            if (number == Long.MIN_VALUE) {
                return RuntimeValue.computed(new BigDecimal(Long.toString(number)).negate());
            }
            return RuntimeValue.computed(-number);
        }
        if (raw instanceof Float) {
            return RuntimeValue.computed(-((Float) raw));
        }
        if (raw instanceof Double) {
            return RuntimeValue.computed(-((Double) raw));
        }
        if (raw instanceof BigDecimal) {
            return RuntimeValue.computed(((BigDecimal) raw).negate());
        }
        return RuntimeValue.computed(ExpressionRuntimeSupport.toBigDecimal(value).negate());
    }

    private static boolean isIntegralPair(Object left, Object right) {
        return isIntegralNumber(left) && isIntegralNumber(right);
    }

    private static boolean isIntegralNumber(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private static boolean prefersLong(Object left, Object right) {
        return left instanceof Long || right instanceof Long;
    }

    private static Number narrowIntegral(long value, boolean preferLong) {
        if (!preferLong && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return Integer.valueOf((int) value);
        }
        return Long.valueOf(value);
    }
}
