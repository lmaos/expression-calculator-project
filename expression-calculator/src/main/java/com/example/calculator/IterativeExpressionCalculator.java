package com.example.calculator;

import com.example.calculator.ExpressionRuntimeSupport.RuntimeValue;
import java.util.Map;

/**
 * 迭代版表达式计算器。
 *
 * <p>设计目标有两个：
 * <pre>
 * 1. 语义上和递归版保持一致
 * 2. 在深层正常嵌套表达式上不消耗 JVM 调用栈
 * </pre>
 *
 * <p>其中最关键的是第二点。像下面这种“不是纯包裹括号”的正常输入：
 * <pre>
 * (1 + (2 + (3 + (4 + ... ))))
 * </pre>
 *
 * <p>如果继续依赖递归下降，调用深度会和表达式层级一起增长，最终触发
 * {@link StackOverflowError}。因此本类把“值表达式求值”切换为显式栈实现，
 * 只把布尔层面的 {@code && / ||} 维持为短路递归拆分：
 * <pre>
 * boolean:  用短路递归保证 enabled || missingVariable 这类语义
 * value  :  用显式栈避免深层算术 / 比较表达式压垮调用栈
 * </pre>
 */
public class IterativeExpressionCalculator implements ExpressionCalculator {
    private static final String AND_OPERATOR = "&&";
    private static final String OR_OPERATOR = "||";
    private static final String[] COMPARISON_OPERATORS = {">=", "<=", "==", "!=", ">", "<"};

    @Override
    public String calculation(String text, Map<String, Object> varMap) {
        return IterativeExpressionEngine.evaluateForCalculation(text, varMap);
    }

    @Override
    public boolean compareCalculation(String text, Map<String, Object> varMap) {
        return evaluateBoolean(ExpressionRuntimeSupport.requireText(text), varMap);
    }

    /**
     * 这里仍然保留布尔层的短路语义：
     * <pre>
     * a || b   -> a 为 true  时，b 不再求值
     * a && b   -> a 为 false 时，b 不再求值
     * </pre>
     *
     * <p>实现上先找最外层 {@code ||}，再找最外层 {@code &&}，
     * 让求值顺序和布尔优先级完全一致。
     */
    private boolean evaluateBoolean(String text, Map<String, Object> varMap) {
        String normalized = ExpressionRuntimeSupport.stripRedundantOuterParentheses(text);

        BooleanSplit orSplit = findTopLevelBooleanSplit(normalized, OR_OPERATOR);
        if (orSplit != null) {
            return evaluateBoolean(orSplit.leftExpression(), varMap) || evaluateBoolean(orSplit.rightExpression(), varMap);
        }

        BooleanSplit andSplit = findTopLevelBooleanSplit(normalized, AND_OPERATOR);
        if (andSplit != null) {
            return evaluateBoolean(andSplit.leftExpression(), varMap)
                    && evaluateBoolean(andSplit.rightExpression(), varMap);
        }

        ComparisonParts comparison = splitTopLevelComparison(normalized);
        if (comparison != null) {
            RuntimeValue leftValue = IterativeExpressionEngine.evaluateValue(comparison.leftExpression(), varMap);
            RuntimeValue rightValue = IterativeExpressionEngine.evaluateValue(comparison.rightExpression(), varMap);
            return ExpressionRuntimeSupport.compare(leftValue, comparison.operator(), rightValue);
        }

        return ExpressionRuntimeSupport.toStandaloneBoolean(IterativeExpressionEngine.evaluateValue(normalized, varMap));
    }

    private BooleanSplit findTopLevelBooleanSplit(String text, String operator) {
        int level = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '(') {
                level++;
            } else if (current == ')') {
                level--;
                if (level < 0) {
                    throw new IllegalArgumentException("括号不匹配");
                }
            }
            if (level == 0 && index <= text.length() - operator.length() && text.startsWith(operator, index)) {
                String leftExpression = text.substring(0, index).trim();
                String rightExpression = text.substring(index + operator.length()).trim();
                if (leftExpression.isEmpty() || rightExpression.isEmpty()) {
                    throw new IllegalArgumentException("比较表达式格式错误");
                }
                return new BooleanSplit(leftExpression, rightExpression);
            }
        }
        if (level != 0) {
            throw new IllegalArgumentException("括号不匹配");
        }
        return null;
    }

    /**
     * 只在最外层拆比较符，避免把括号内部的表达式误切开。
     *
     * <pre>
     * ((a + b) * c) >= d
     * ^^^^^^^^^^^^^    ^
     *      left       right
     * </pre>
     */
    private ComparisonParts splitTopLevelComparison(String text) {
        int level = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '(') {
                level++;
            } else if (current == ')') {
                level--;
                if (level < 0) {
                    throw new IllegalArgumentException("括号不匹配");
                }
            }
            if (level != 0) {
                continue;
            }
            for (String operator : COMPARISON_OPERATORS) {
                if (!text.startsWith(operator, index)) {
                    continue;
                }
                String leftExpression = text.substring(0, index).trim();
                String rightExpression = text.substring(index + operator.length()).trim();
                if (leftExpression.isEmpty() || rightExpression.isEmpty()) {
                    throw new IllegalArgumentException("比较表达式格式错误");
                }
                return new ComparisonParts(leftExpression, operator, rightExpression);
            }
        }
        if (level != 0) {
            throw new IllegalArgumentException("括号不匹配");
        }
        return null;
    }

    private record BooleanSplit(String leftExpression, String rightExpression) {
    }

    private record ComparisonParts(String leftExpression, String operator, String rightExpression) {
    }
}
