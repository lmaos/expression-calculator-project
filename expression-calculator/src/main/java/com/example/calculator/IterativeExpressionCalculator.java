package com.example.calculator;

import java.util.Map;

/**
 * 面向深层括号场景的实现。
 *
 * <p>它优先以非递归方式消除完整包裹表达式、以及顶层比较两侧的冗余括号，
 * 再复用共享语义层完成求值，从而避免十万层纯包裹括号把递归栈耗尽。
 */
public class IterativeExpressionCalculator implements ExpressionCalculator {
    private static final String[] COMPARISON_OPERATORS = {">=", "<=", "==", "!=", ">", "<"};
    private static final String AND_OPERATOR = "&&";
    private static final String OR_OPERATOR = "||";

    @Override
    public String calculation(String text, Map<String, Object> varMap) {
        return RecursiveExpressionEngine.evaluateForCalculation(
                ExpressionRuntimeSupport.stripRedundantOuterParentheses(text), varMap);
    }

    @Override
    public boolean compareCalculation(String text, Map<String, Object> varMap) {
        String normalized = ExpressionRuntimeSupport.stripRedundantOuterParentheses(text);
        if (containsTopLevelBooleanOperator(normalized)) {
            return RecursiveExpressionEngine.evaluateForComparison(normalized, varMap);
        }
        ComparisonParts comparison = splitTopLevelComparison(normalized);
        if (comparison == null) {
            return RecursiveExpressionEngine.evaluateForComparison(normalized, varMap);
        }

        ExpressionRuntimeSupport.RuntimeValue leftValue = RecursiveExpressionEngine.evaluateValue(
                ExpressionRuntimeSupport.stripRedundantOuterParentheses(comparison.leftExpression()), varMap);
        ExpressionRuntimeSupport.RuntimeValue rightValue = RecursiveExpressionEngine.evaluateValue(
                ExpressionRuntimeSupport.stripRedundantOuterParentheses(comparison.rightExpression()), varMap);
        return ExpressionRuntimeSupport.compare(leftValue, comparison.operator(), rightValue);
    }

    private boolean containsTopLevelBooleanOperator(String text) {
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
            if (level == 0 && (text.startsWith(AND_OPERATOR, index) || text.startsWith(OR_OPERATOR, index))) {
                return true;
            }
        }
        if (level != 0) {
            throw new IllegalArgumentException("括号不匹配");
        }
        return false;
    }

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

    private record ComparisonParts(String leftExpression, String operator, String rightExpression) {
    }
}
