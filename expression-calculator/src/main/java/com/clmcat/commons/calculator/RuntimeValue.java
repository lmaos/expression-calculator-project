package com.clmcat.commons.calculator;

import java.math.BigDecimal;

public final class RuntimeValue {

    public enum Origin {
        LITERAL,
        VARIABLE,
        COMPUTED,
        MISSING_VARIABLE
    }

    private final Object raw;
    private final Origin origin;
    private final String missingVariableName;

    private RuntimeValue(Object raw, Origin origin, String missingVariableName) {
        this.raw = raw;
        this.origin = origin;
        this.missingVariableName = missingVariableName;
    }

    static RuntimeValue literal(Object raw) {
        return new RuntimeValue(raw, Origin.LITERAL, null);
    }

    static RuntimeValue variable(Object raw) {
        return new RuntimeValue(raw, Origin.VARIABLE, null);
    }

    static RuntimeValue missingVariable(String name) {
        return new RuntimeValue(null, Origin.MISSING_VARIABLE, name);
    }

    public static RuntimeValue computed(Object raw) {
        return new RuntimeValue(raw, Origin.COMPUTED, null);
    }

    public Object raw() {
        return raw;
    }

    public Origin origin() {
        return origin;
    }

    public boolean isMissingVariable() {
        return origin == Origin.MISSING_VARIABLE;
    }

    public String missingVariableName() {
        return missingVariableName;
    }

    public BigDecimal toBigDecimal() {
        return ExpressionRuntimeSupport.toBigDecimal(this);
    }

    Object toInvocationArgument() {
        if (isMissingVariable()) {
            throw new IllegalArgumentException("变量不存在: " + missingVariableName);
        }
        /*
         * 题目的关键约束：
         * 1. 直接字面量参数保留原始类型，例如 55 -> int
         * 2. 变量表里的数字统一按 BigDecimal 参与方法匹配
         *
         * 因此只有 VARIABLE + Number 时才做 BigDecimal 归一化。
         */
        if (origin == Origin.VARIABLE && raw instanceof Number && !(raw instanceof BigDecimal)) {
            return new BigDecimal(raw.toString());
        }
        return raw;
    }
}
