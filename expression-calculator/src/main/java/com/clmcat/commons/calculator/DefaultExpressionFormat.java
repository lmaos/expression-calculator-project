package com.clmcat.commons.calculator;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 默认的表达式模板格式化实现。
 *
 * <p>规则通过单个 {@code ?} 指定表达式占位位置，例如：
 * <pre>
 * ${?}
 * #{?}
 * {{?}}
 * </pre>
 *
 * <p>当占位符前缀前紧邻反斜杠时，会按普通文本输出，例如 {@code \${name}} 会得到 {@code ${name}}。
 *
 * <p>当前实现不支持占位符嵌套；如果需要动态变量名，请在表达式内部使用
 * {@code map['name_' + index]} 这一类写法。
 */
public class DefaultExpressionFormat implements ExpressionFormat {

    private final ExpressionCalculator calculator;

    /**
     * 创建默认格式化器。
     *
     * @param calculator 用于求值占位表达式的计算器，不能为空
     */
    public DefaultExpressionFormat(ExpressionCalculator calculator) {
        this.calculator = Objects.requireNonNull(calculator, "calculator");
    }

    /**
     * 按指定规则格式化文本。
     *
     * @param text 原始文本，不能为空
     * @param rule 占位符规则，必须且只能包含一个 {@code ?}
     * @param varMap 变量上下文，为 {@code null} 时按空 Map 处理
     * @return 格式化后的文本
     * @throws IllegalArgumentException 当文本、规则或占位符结构非法时抛出
     */
    @Override
    public String format(String text, String rule, Map<String, Object> varMap) {
        if (text == null) {
            throw new IllegalArgumentException("text 不能为空");
        }
        if (text.isEmpty()) {
            return "";
        }

        RuleParts ruleParts = parseRule(rule);
        Map<String, Object> safeVarMap = varMap == null ? Collections.<String, Object>emptyMap() : varMap;
        StringBuilder result = new StringBuilder(text.length());
        int position = 0;
        while (position < text.length()) {
            int start = text.indexOf(ruleParts.prefix, position);
            if (start < 0) {
                result.append(text, position, text.length());
                break;
            }
            if (!ruleParts.prefix.isEmpty() && start > 0 && text.charAt(start - 1) == '\\') {
                result.append(text, position, start - 1).append(ruleParts.prefix);
                position = start + ruleParts.prefix.length();
                continue;
            }

            result.append(text, position, start);
            int expressionStart = start + ruleParts.prefix.length();
            int expressionEnd = ruleParts.suffix.isEmpty()
                    ? text.length()
                    : text.indexOf(ruleParts.suffix, expressionStart);
            if (expressionEnd < 0) {
                throw new IllegalArgumentException("未找到匹配的后缀: " + ruleParts.suffix);
            }

            Object value = calculator.evaluate(text.substring(expressionStart, expressionEnd).trim(), safeVarMap);
            result.append(value == null ? "" : String.valueOf(value));
            position = expressionEnd + ruleParts.suffix.length();
            if (ruleParts.suffix.isEmpty()) {
                break;
            }
        }
        return result.toString();
    }

    private RuleParts parseRule(String rule) {
        if (rule == null) {
            throw new IllegalArgumentException("rule 不能为空");
        }
        int placeholderIndex = rule.indexOf('?');
        if (placeholderIndex < 0 || placeholderIndex != rule.lastIndexOf('?')) {
            throw new IllegalArgumentException("rule 必须且只能包含一个 '?'");
        }
        return new RuleParts(rule.substring(0, placeholderIndex), rule.substring(placeholderIndex + 1));
    }

    private static final class RuleParts {
        private final String prefix;
        private final String suffix;

        private RuleParts(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }
    }
}
