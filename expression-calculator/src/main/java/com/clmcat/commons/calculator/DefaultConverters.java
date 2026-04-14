package com.clmcat.commons.calculator;

import java.math.BigDecimal;

/**
 * 内置类型转换器集合。
 */
final class DefaultConverters {

    private static final BigDecimal BYTE_MIN = BigDecimal.valueOf(Byte.MIN_VALUE);
    private static final BigDecimal BYTE_MAX = BigDecimal.valueOf(Byte.MAX_VALUE);
    private static final BigDecimal SHORT_MIN = BigDecimal.valueOf(Short.MIN_VALUE);
    private static final BigDecimal SHORT_MAX = BigDecimal.valueOf(Short.MAX_VALUE);
    private static final BigDecimal INT_MIN = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigDecimal INT_MAX = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final BigDecimal LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);
    private static final BigDecimal CHAR_MIN = BigDecimal.ZERO;
    private static final BigDecimal CHAR_MAX = BigDecimal.valueOf(Character.MAX_VALUE);

    private DefaultConverters() {
    }

    static void registerDefaults(ConverterRegistry registry) {
        registry.register("int", DefaultConverters::toInteger);
        registry.register("long", DefaultConverters::toLong);
        registry.register("double", DefaultConverters::toDouble);
        registry.register("float", DefaultConverters::toFloat);
        registry.register("short", DefaultConverters::toShort);
        registry.register("byte", DefaultConverters::toByte);
        registry.register("char", DefaultConverters::toCharacter);
        registry.register("boolean", DefaultConverters::toBoolean);
        registry.register("String", DefaultConverters::toStringValue);
        registry.register("BigDecimal", DefaultConverters::toBigDecimalValue);
    }

    private static Integer toInteger(Object value) {
        BigDecimal number = toBigDecimalOrNull("int", value);
        if (number == null) {
            return null;
        }
        ensureInRange(number, INT_MIN, INT_MAX, "int");
        return number.intValue();
    }

    private static Long toLong(Object value) {
        BigDecimal number = toBigDecimalOrNull("long", value);
        if (number == null) {
            return null;
        }
        ensureInRange(number, LONG_MIN, LONG_MAX, "long");
        return number.longValue();
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof Character) {
            return (double) ((Character) value).charValue();
        }
        if (value instanceof CharSequence) {
            String text = value.toString().trim();
            try {
                return Double.valueOf(text);
            } catch (NumberFormatException exception) {
                throw cannotConvert("double", value, exception);
            }
        }
        throw cannotConvert("double", value, null);
    }

    private static Float toFloat(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof Character) {
            return (float) ((Character) value).charValue();
        }
        if (value instanceof CharSequence) {
            String text = value.toString().trim();
            try {
                return Float.valueOf(text);
            } catch (NumberFormatException exception) {
                throw cannotConvert("float", value, exception);
            }
        }
        throw cannotConvert("float", value, null);
    }

    private static Short toShort(Object value) {
        BigDecimal number = toBigDecimalOrNull("short", value);
        if (number == null) {
            return null;
        }
        ensureInRange(number, SHORT_MIN, SHORT_MAX, "short");
        return number.shortValue();
    }

    private static Byte toByte(Object value) {
        BigDecimal number = toBigDecimalOrNull("byte", value);
        if (number == null) {
            return null;
        }
        ensureInRange(number, BYTE_MIN, BYTE_MAX, "byte");
        return number.byteValue();
    }

    private static Character toCharacter(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Character) {
            return (Character) value;
        }
        if (value instanceof CharSequence) {
            String text = value.toString();
            if (text.length() == 1) {
                return text.charAt(0);
            }
            throw cannotConvert("char", value, null);
        }
        BigDecimal number = toBigDecimalOrNull("char", value);
        ensureInRange(number, CHAR_MIN, CHAR_MAX, "char");
        return (char) number.intValue();
    }

    private static Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).compareTo(BigDecimal.ZERO) != 0;
        }
        if (value instanceof CharSequence) {
            String text = value.toString().trim();
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
            try {
                return new BigDecimal(text).compareTo(BigDecimal.ZERO) != 0;
            } catch (NumberFormatException exception) {
                throw cannotConvert("boolean", value, exception);
            }
        }
        throw cannotConvert("boolean", value, null);
    }

    private static String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal toBigDecimalValue(Object value) {
        return toBigDecimalOrNull("BigDecimal", value);
    }

    private static BigDecimal toBigDecimalOrNull(String typeName, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        if (value instanceof Character) {
            return BigDecimal.valueOf(((Character) value).charValue());
        }
        if (value instanceof CharSequence) {
            String text = value.toString().trim();
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException exception) {
                throw cannotConvert(typeName, value, exception);
            }
        }
        throw cannotConvert(typeName, value, null);
    }

    private static void ensureInRange(BigDecimal value, BigDecimal min, BigDecimal max, String typeName) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException("值超出 " + typeName + " 范围: " + value);
        }
    }

    private static IllegalArgumentException cannotConvert(String typeName, Object value, Exception cause) {
        String message = "无法转换为 " + typeName + ": " + value;
        return cause == null ? new IllegalArgumentException(message) : new IllegalArgumentException(message, cause);
    }
}
