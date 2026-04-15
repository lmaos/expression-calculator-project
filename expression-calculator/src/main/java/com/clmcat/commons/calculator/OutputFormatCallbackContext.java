package com.clmcat.commons.calculator;

import java.util.Collections;
import java.util.Map;

/**
 * 模板占位输出回调上下文。
 */
public final class OutputFormatCallbackContext {

    private final Object value;
    private final String expression;
    private final OutputExpressionKind expressionKind;
    private final String defaultFormattedValue;
    private final OutputFormatContext formatContext;

    OutputFormatCallbackContext(
            Object value,
            String expression,
            OutputExpressionKind expressionKind,
            String defaultFormattedValue,
            OutputFormatContext formatContext) {
        this.value = value;
        this.expression = expression;
        this.expressionKind = expressionKind;
        this.defaultFormattedValue = defaultFormattedValue;
        this.formatContext = formatContext;
    }

    public Object value() {
        return value;
    }

    public Class<?> runtimeType() {
        return value == null ? null : value.getClass();
    }

    public String expression() {
        return expression;
    }

    public OutputExpressionKind expressionKind() {
        return expressionKind;
    }

    public String defaultFormattedValue() {
        return defaultFormattedValue;
    }

    public boolean hasFormatter() {
        return formatContext != null;
    }

    public OutputFormatContext formatContext() {
        return formatContext;
    }

    public Class<?> registeredType() {
        return formatContext == null ? null : formatContext.registeredType();
    }

    public Map<String, Object> options() {
        return formatContext == null ? Collections.<String, Object>emptyMap() : formatContext.options();
    }
}
