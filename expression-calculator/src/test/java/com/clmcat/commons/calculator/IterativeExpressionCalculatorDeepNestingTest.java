package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 深层嵌套专项测试，专门验证迭代实现对超深表达式的承受能力。
 */
class IterativeExpressionCalculatorDeepNestingTest {

    private ExpressionCalculator calculator;
    private Map<String, Object> variables;

    @BeforeEach
    void setUp() {
        calculator = new IterativeExpressionCalculator();
        variables = new HashMap<>();
    }

    @Test
    void shouldHandleVeryDeepNestedArithmeticExpression() {
        String expression = buildNestedExpression("1 + 2", 100_000);
        assertEquals("3", calculator.calculation(expression, variables));
    }

    @Test
    void shouldHandleVeryDeepNestedComparisonExpression() {
        String expression = buildNestedExpression("1 + 2", 100_000) + " == 3";
        assertTrue(calculator.compareCalculation(expression, variables));
    }

    @Test
    void shouldHandleVeryDeepNestedBooleanGroupingExpression() {
        String expression = buildNestedExpression("1 + 2 == 3", 100_000);
        assertTrue(calculator.compareCalculation(expression, variables));
    }

    @Test
    void shouldHandleDeepRightNestedArithmeticWithoutStackOverflow() {
        int depth = 20_000;
        String expression = buildRightDeepAddition(depth);
        assertEquals(sumFromOneTo(depth), calculator.calculation(expression, variables));
    }

    @Test
    void shouldHandleDeepRightNestedComparisonWithoutStackOverflow() {
        int depth = 20_000;
        String expression = buildRightDeepAddition(depth) + " == " + sumFromOneTo(depth);
        assertTrue(calculator.compareCalculation(expression, variables));
    }

    @Test
    void shouldRejectBrokenDeepRightNestedExpressionWithoutStackOverflow() {
        int depth = 20_000;
        String expression = buildRightDeepAddition(depth);
        String brokenExpression = expression.substring(0, expression.length() - 1);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation(brokenExpression, variables));
    }

    private String buildNestedExpression(String innerExpression, int depth) {
        StringBuilder builder = new StringBuilder(innerExpression.length() + depth * 2);
        for (int index = 0; index < depth; index++) {
            builder.append('(');
        }
        builder.append(innerExpression);
        for (int index = 0; index < depth; index++) {
            builder.append(')');
        }
        return builder.toString();
    }

    private String buildRightDeepAddition(int depth) {
        StringBuilder builder = new StringBuilder(depth * 8);
        for (int value = 1; value < depth; value++) {
            builder.append('(').append(value).append(" + ");
        }
        builder.append(depth);
        for (int value = 1; value < depth; value++) {
            builder.append(')');
        }
        return builder.toString();
    }

    private String sumFromOneTo(int depth) {
        BigDecimal bigDepth = BigDecimal.valueOf(depth);
        return bigDepth.multiply(bigDepth.add(BigDecimal.ONE))
                .divide(BigDecimal.valueOf(2))
                .toPlainString();
    }
}
