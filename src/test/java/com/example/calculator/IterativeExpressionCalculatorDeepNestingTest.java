package com.example.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
