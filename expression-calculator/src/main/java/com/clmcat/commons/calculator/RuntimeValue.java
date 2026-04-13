package com.clmcat.commons.calculator;

import java.math.BigDecimal;

/**
 * 运行时值包装器，记录原始值以及它来自字面量、变量还是计算结果。
 *
 * <p>通过来源信息，运行时可以在方法调用、格式化输出和缺失变量处理时保留语义约束。
 */
public final class RuntimeValue {

    /** 值来源，便于区分字面量、变量、计算结果和缺失变量。 */
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

    /** 创建字面量值，保留原始类型。 */
    static RuntimeValue literal(Object raw) {
        return new RuntimeValue(raw, Origin.LITERAL, null);
    }

    /** 创建变量值，保留变量表中的原始对象。 */
    static RuntimeValue variable(Object raw) {
        return new RuntimeValue(raw, Origin.VARIABLE, null);
    }

    /** 创建缺失变量占位符。 */
    static RuntimeValue missingVariable(String name) {
        return new RuntimeValue(null, Origin.MISSING_VARIABLE, name);
    }

    /** 创建计算结果值。 */
    public static RuntimeValue computed(Object raw) {
        return new RuntimeValue(raw, Origin.COMPUTED, null);
    }

    /** 返回底层原始值。 */
    public Object raw() {
        return raw;
    }

    /** 返回值来源。 */
    public Origin origin() {
        return origin;
    }

    /** 判断当前值是否代表缺失变量。 */
    public boolean isMissingVariable() {
        return origin == Origin.MISSING_VARIABLE;
    }

    /** 返回缺失变量名，仅在 {@link #isMissingVariable()} 为 true 时有意义。 */
    public String missingVariableName() {
        return missingVariableName;
    }

    /** 统一把当前值转换为 BigDecimal，集中复用数值归一化逻辑。 */
    public BigDecimal toBigDecimal() {
        return ExpressionRuntimeSupport.toBigDecimal(this);
    }

    /**
     * 把值转换成方法调用参数。
     *
     * <p>变量表中的数字会统一转成 BigDecimal，而直接字面量保留原始类型。
     */
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
