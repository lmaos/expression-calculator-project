package com.clmcat.commons.calculator;

import java.util.Map;

public interface ExpressionFormat {
    /**
     * 使用默认规则 {@code ${?}} 格式化文本。
     *
     * <p>文本中匹配到的占位符会调用 {@link ExpressionCalculator#evaluate(String, Map)} 求值，
     * 然后将结果替换回原文本。
     *
     * @param text 需要被格式化的文本
     * @param varMap 变量数据
     * @return 格式化后的文本
     */
    default String format(String text, Map<String, Object> varMap) {
        return format(text, "${?}", varMap);
    }

    /**
     * 使用默认规则 {@code ${?}} 和指定输出注册器格式化文本。
     *
     * @param text 需要被格式化的文本
     * @param varMap 变量数据
     * @param outputRegistry 输出格式注册器；为 {@code null} 时由实现决定默认行为
     * @return 格式化后的文本
     */
    default String format(String text, Map<String, Object> varMap, OutputFormatRegistry outputRegistry) {
        return format(text, "${?}", varMap, outputRegistry);
    }

    /**
     * 按指定规则格式化文本。
     *
     * <p>规则必须包含且只包含一个 {@code ?}，其左侧为前缀，右侧为后缀，例如：
     * <pre>
     * ${?}
     * #{?}
     * {{?}}
     * </pre>
     *
     * @param text 需要被格式化的文本
     * @param rule 占位符规则
     * @param varMap 变量数据
     * @return 格式化后的文本
     */
    String format(String text, String rule, Map<String, Object> varMap);

    /**
     * 按指定规则和输出注册器格式化文本。
     *
     * <p>调用方可通过 {@link OutputFormatRegistry} 为不同类型注册专用输出处理器，
     * 并按类型附带字符集、日期格式、输出模式等动态参数。若需要按变量名或表达式文本做最终覆盖，
     * 可在注册器上配置 {@link OutputFormatCallback}。
     *
     * @param text 需要被格式化的文本
     * @param rule 占位符规则
     * @param varMap 变量数据
     * @param outputRegistry 输出格式注册器；为 {@code null} 时由实现决定默认行为
     * @return 格式化后的文本
     */
    String format(String text, String rule, Map<String, Object> varMap, OutputFormatRegistry outputRegistry);
}
