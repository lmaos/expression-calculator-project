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
}
