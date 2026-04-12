package com.example.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExpressionCalculatorDepthLimitAndBooleanDefenseTest {

    private Map<String, Object> variables;

    static Stream<Arguments> calculatorsWithLimit() {
        return Stream.of(
                Arguments.of("recursive", new RecursiveExpressionCalculator(5)),
                Arguments.of("iterative", new IterativeExpressionCalculator(5)));
    }

    @BeforeEach
    void setUp() {
        variables = new HashMap<>();
        variables.put("a", 1);
        variables.put("b", 2);
        variables.put("x", 3);
        variables.put("y", 4);
    }

    @Test
    void iterativeShouldHandleDeepBooleanShortCircuitWithoutStackOverflow() {
        ExpressionCalculator calculator = new IterativeExpressionCalculator();
        String expression = "a == a || " + buildDeepBooleanExpression(20_000, false);
        assertTrue(calculator.compareCalculation(expression, variables));
    }

    @Test
    void iterativeShouldHandleDeepBooleanTraversalWithoutStackOverflow() {
        ExpressionCalculator calculator = new IterativeExpressionCalculator();
        String expression = buildDeepBooleanExpression(20_000, true);
        assertTrue(calculator.compareCalculation(expression, variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculatorsWithLimit")
    void shouldRejectArithmeticExpressionExceedingDepthLimit(String name, ExpressionCalculator calculator) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculation(buildNestedArithmetic(6), variables));
        assertEquals("表达式层级超过限制: 5", exception.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculatorsWithLimit")
    void shouldRejectBooleanExpressionExceedingDepthLimit(String name, ExpressionCalculator calculator) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.compareCalculation(buildNestedBooleanGrouping(6), variables));
        assertEquals("表达式层级超过限制: 5", exception.getMessage());
    }

    @Test
    void shouldRejectIllegalDepthLimitConstructorArgument() {
        assertThrows(IllegalArgumentException.class, () -> new RecursiveExpressionCalculator(-2));
        assertThrows(IllegalArgumentException.class, () -> new IterativeExpressionCalculator(-2));
    }

    private String buildNestedArithmetic(int depth) {
        String expression = "a + b";
        for (int index = 0; index < depth; index++) {
            expression = "(" + expression + ")";
        }
        return expression;
    }

    private String buildNestedBooleanGrouping(int depth) {
        String expression = "a < b";
        for (int index = 0; index < depth; index++) {
            expression = "(" + expression + ")";
        }
        return expression;
    }

    private String buildDeepBooleanExpression(int depth, boolean finalTrue) {
        StringBuilder builder = new StringBuilder(depth * 18);
        for (int index = 0; index < depth; index++) {
            builder.append("(a == b || ");
        }
        builder.append(finalTrue ? "x == 3" : "missingVariable == 1");
        for (int index = 0; index < depth; index++) {
            builder.append(')');
        }
        return builder.toString();
    }
}
