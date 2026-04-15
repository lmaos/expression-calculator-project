package com.clmcat.commons.calculator;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

/**
 * 输出格式化上下文，向 {@link OutputFormatter} 暴露当前注册类型和动态配置项。
 */
public final class OutputFormatContext {

    private static final Set<String> AVAILABLE_TIME_ZONES =
            new HashSet<String>(Arrays.asList(TimeZone.getAvailableIDs()));

    private final Class<?> registeredType;
    private final Map<String, Object> options;
    private final String expression;
    private final OutputExpressionKind expressionKind;

    OutputFormatContext(
            Class<?> registeredType,
            Map<String, Object> options,
            String expression,
            OutputExpressionKind expressionKind) {
        this.registeredType = Objects.requireNonNull(registeredType, "registeredType");
        this.options = Collections.unmodifiableMap(new HashMap<String, Object>(options));
        this.expression = expression;
        this.expressionKind = expressionKind;
    }

    public Class<?> registeredType() {
        return registeredType;
    }

    public Map<String, Object> options() {
        return options;
    }

    /**
     * 返回当前模板占位符中的表达式文本；当不是模板格式化链路触发时可能为 {@code null}。
     */
    public String expression() {
        return expression;
    }

    /**
     * 返回当前模板占位符的表达式类型；当不是模板格式化链路触发时可能为 {@code null}。
     */
    public OutputExpressionKind expressionKind() {
        return expressionKind;
    }

    public boolean hasOption(String name) {
        return options.containsKey(normalizeOptionName(name));
    }

    public Object option(String name) {
        return options.get(normalizeOptionName(name));
    }

    public String stringOption(String name, String defaultValue) {
        Object value = option(name);
        return value == null ? defaultValue : String.valueOf(value);
    }

    public Charset charsetOption(String name, Charset defaultValue) {
        Object value = option(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Charset) {
            return (Charset) value;
        }
        if (value instanceof CharSequence) {
            String charsetName = value.toString().trim();
            if (charsetName.isEmpty()) {
                throw new IllegalArgumentException("输出选项 " + name + " 不能为空");
            }
            try {
                return Charset.forName(charsetName);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("不支持的字符集: " + charsetName, exception);
            }
        }
        throw new IllegalArgumentException("输出选项 " + name + " 不是合法的字符集: " + value);
    }

    public TimeZone timeZoneOption(String name, TimeZone defaultValue) {
        Object value = option(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof TimeZone) {
            return (TimeZone) value;
        }
        if (value instanceof CharSequence) {
            String timeZoneId = value.toString().trim();
            if (timeZoneId.isEmpty()) {
                throw new IllegalArgumentException("输出选项 " + name + " 不能为空");
            }
            if (!AVAILABLE_TIME_ZONES.contains(timeZoneId) && !timeZoneId.startsWith("GMT") && !timeZoneId.startsWith("UTC")) {
                throw new IllegalArgumentException("不支持的时区: " + timeZoneId);
            }
            return TimeZone.getTimeZone(timeZoneId);
        }
        throw new IllegalArgumentException("输出选项 " + name + " 不是合法的时区: " + value);
    }

    private static String normalizeOptionName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("输出选项名称不能为空");
        }
        return name.trim();
    }
}
