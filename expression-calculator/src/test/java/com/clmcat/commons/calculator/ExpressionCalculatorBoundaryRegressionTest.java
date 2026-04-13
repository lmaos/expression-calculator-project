package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 边界回归测试，覆盖非法输入、真值规则、文件/集合/日期比较和跨实现一致性。
 */
class ExpressionCalculatorBoundaryRegressionTest {

    @TempDir
    Path tempDir;

    private Map<String, Object> variables;

    static Stream<Arguments> calculators() {
        return Stream.of(
                Arguments.of("recursive", new RecursiveExpressionCalculator()),
                Arguments.of("iterative", new IterativeExpressionCalculator()));
    }

    @BeforeEach
    void setUp() throws Exception {
        variables = new HashMap<>();
        variables.put("a", 1);
        variables.put("b", 2);
        variables.put("c", 3);
        variables.put("x_y1", 7);
        variables.put("total_1", 10);
        variables.put("price", "12.5");
        variables.put("discount", 2.5);
        variables.put("enabled", true);
        variables.put("disabled", false);
        variables.put("alpha", "alpha");
        variables.put("beta", "beta");
        variables.put("startDate", LocalDate.of(2024, 1, 1));
        variables.put("endDate", LocalDate.of(2024, 1, 2));
        variables.put("items", List.of(1, 2));
        variables.put("emptyItems", Collections.emptyList());
        variables.put("metadata", Map.of("k", "v"));
        variables.put("emptyMetadata", Collections.emptyMap());

        Path existingPath = tempDir.resolve("exists.txt");
        Files.writeString(existingPath, "ok");
        variables.put("existingFile", existingPath.toFile());
        variables.put("missingFile", tempDir.resolve("missing.txt").toFile());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("边界算术表达式")
    void shouldEvaluateArithmeticBoundaryCases(String name, ExpressionCalculator calculator) {
        assertEquals("0", calculator.calculation("0", variables));
        assertEquals("7", calculator.calculation("0007", variables));
        assertEquals("1", calculator.calculation("(a)", variables));
        assertEquals("-1", calculator.calculation("a + -b", variables));
        assertEquals("3", calculator.calculation("a - -b", variables));
        assertEquals("2", calculator.calculation("a * (b + c * 0)", variables));
        assertEquals("15", calculator.calculation("price + discount", variables));
        assertEquals("10.5", calculator.calculation("((x_y1 + total_1) / 2) + 2", variables));
        assertEquals("9", calculator.calculation("a + b + c + total_1 - x_y1", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("边界布尔表达式")
    void shouldEvaluateBooleanBoundaryCases(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("enabled", variables));
        assertFalse(calculator.compareCalculation("disabled", variables));
        assertTrue(calculator.compareCalculation("existingFile", variables));
        assertFalse(calculator.compareCalculation("missingFile", variables));
        assertTrue(calculator.compareCalculation("items && metadata", variables));
        assertFalse(calculator.compareCalculation("emptyItems || emptyMetadata", variables));
        assertTrue(calculator.compareCalculation("(startDate < endDate) && (alpha < beta)", variables));
        assertTrue(calculator.compareCalculation("enabled || missingBoolean", variables));
        assertFalse(calculator.compareCalculation("disabled && missingBoolean", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("边界非法输入")
    void shouldRejectBoundaryInvalidInputs(String name, ExpressionCalculator calculator) {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("   ", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation(".", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("1..2", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("a + #", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("missing + 1", variables));
        assertThrows(ArithmeticException.class, () -> calculator.calculation("a / (c - 3)", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.compareCalculation("a + b", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.compareCalculation("enabled > disabled", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.compareCalculation("a > #", variables));
    }

    static Stream<Arguments> additionalBooleanBoundaryCases() {
        return Stream.of(
                new Object[] {"recursive", new RecursiveExpressionCalculator(), "((enabled))", true},
                new Object[] {"recursive", new RecursiveExpressionCalculator(), "((disabled))", false},
                new Object[] {"recursive", new RecursiveExpressionCalculator(), "((items))", true},
                new Object[] {"recursive", new RecursiveExpressionCalculator(), "((emptyItems))", false},
                new Object[] {"recursive", new RecursiveExpressionCalculator(), "((metadata))", true},
                new Object[] {"recursive", new RecursiveExpressionCalculator(), "((emptyMetadata))", false},
                new Object[] {"recursive", new RecursiveExpressionCalculator(), "((existingFile))", true},
                new Object[] {"recursive", new RecursiveExpressionCalculator(), "((missingFile))", false},
                new Object[] {"recursive", new RecursiveExpressionCalculator(), "(((a + b) == 3) || (enabled && items))", true},
                new Object[] {"recursive", new RecursiveExpressionCalculator(), "(((a + b) == 4) || (disabled && items))", false},
                new Object[] {"iterative", new IterativeExpressionCalculator(), "((enabled))", true},
                new Object[] {"iterative", new IterativeExpressionCalculator(), "((disabled))", false},
                new Object[] {"iterative", new IterativeExpressionCalculator(), "((items))", true},
                new Object[] {"iterative", new IterativeExpressionCalculator(), "((emptyItems))", false},
                new Object[] {"iterative", new IterativeExpressionCalculator(), "((metadata))", true},
                new Object[] {"iterative", new IterativeExpressionCalculator(), "((emptyMetadata))", false},
                new Object[] {"iterative", new IterativeExpressionCalculator(), "((existingFile))", true},
                new Object[] {"iterative", new IterativeExpressionCalculator(), "((missingFile))", false},
                new Object[] {"iterative", new IterativeExpressionCalculator(), "(((a + b) == 3) || (enabled && items))", true},
                new Object[] {"iterative", new IterativeExpressionCalculator(), "(((a + b) == 4) || (disabled && items))", false})
                .map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("additionalBooleanBoundaryCases")
    @DisplayName("额外边界布尔组合")
    void shouldEvaluateAdditionalBooleanBoundaryCases(String name, ExpressionCalculator calculator, String expression,
            boolean expected) {
        assertEquals(expected, calculator.compareCalculation(expression, variables));
    }

    @Test
    @DisplayName("跨实现一致性边界场景")
    void shouldMatchAcrossImplementationsForCombinedBoundaryScenario() {
        String expression = "(a + b) > c || existingFile && ((a + 1) == 2)";
        ExpressionCalculator recursive = new RecursiveExpressionCalculator();
        ExpressionCalculator iterative = new IterativeExpressionCalculator();

        assertTrue(recursive.compareCalculation(expression, variables));
        assertTrue(iterative.compareCalculation(expression, variables));
    }
}
