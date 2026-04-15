package com.clmcat.commons.calculator;

/**
 * 占位表达式类型。
 *
 * <p>{@link #REFERENCE} 表示不含运算符的简单引用，例如 {@code dateVar}、{@code holder.name}；
 * {@link #EXPRESSION} 表示包含运算、函数调用、下标等更复杂的表达式。
 */
public enum OutputExpressionKind {
    REFERENCE,
    EXPRESSION
}
