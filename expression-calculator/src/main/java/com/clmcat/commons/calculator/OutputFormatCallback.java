package com.clmcat.commons.calculator;

/**
 * 模板占位输出回调。
 *
 * <p>它会在默认类型格式化完成后被调用，可根据当前值、占位表达式和表达式类型决定是否覆盖最终输出。
 * 返回 {@code null} 时沿用默认格式化结果；返回非 {@code null} 时使用回调结果替换。
 */
@FunctionalInterface
public interface OutputFormatCallback {

    /**
     * 尝试覆盖当前占位符的输出结果。
     *
     * @param value 当前占位符对应的原始值，可能为 {@code null}
     * @param context 当前占位符输出上下文
     * @return 覆盖后的输出；返回 {@code null} 时表示保持默认格式化结果
     */
    String format(Object value, OutputFormatCallbackContext context);
}
