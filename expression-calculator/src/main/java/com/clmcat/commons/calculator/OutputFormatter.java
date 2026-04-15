package com.clmcat.commons.calculator;

/**
 * 表达式格式化输出处理器。
 *
 * <p>用于把 {@link ExpressionCalculator#evaluate(String, java.util.Map)} 得到的原始对象
 * 渲染为最终文本，适合按类型扩展 byte[]、File、Date 等特殊对象的输出方式。
 */
@FunctionalInterface
public interface OutputFormatter {

    /**
     * 把原始值渲染为字符串。
     *
     * @param value 原始值
     * @param context 当前输出上下文
     * @return 渲染结果，返回 {@code null} 时模板中会替换为空字符串
     */
    String format(Object value, OutputFormatContext context);
}
