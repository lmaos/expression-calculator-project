package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 生成式逻辑测试，覆盖方法链、布尔短路和特殊类型的求值行为。
 */
class ExpressionCalculatorGeneratedLogicTest {

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
        variables.put("x", 5);
        variables.put("y", 10);
        variables.put("a", 10);
        variables.put("isTrue", true);
        variables.put("str", "geekbang");
        variables.put("num1", new BigDecimal("123"));
        variables.put("num2", 55);
        variables.put("items", Arrays.asList(1, 2, 3));
        variables.put("emptyItems", Collections.emptyList());
        variables.put("nullableFile", null);

        Path existingPath = tempDir.resolve("logic.txt");
        Files.write(existingPath, "ok".getBytes(StandardCharsets.UTF_8));
        File file = existingPath.toFile();
        variables.put("file", file);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldInvokeMethodsAndFormatResults(String name, ExpressionCalculator calculator) {
        assertEquals("8", calculator.calculation("str.length()", variables));
        assertEquals("geek", calculator.calculation("str.substring(0, 4)", variables));
        assertEquals("178", calculator.calculation("num1.add(num2)", variables));
        assertEquals("true", calculator.calculation("file.exists()", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldSupportGeneratedLogicalScenarios(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("x + y > a + 1", variables));
        assertTrue(calculator.compareCalculation("x > 0 && (y < 20 || a == 10)", variables));
        assertTrue(calculator.compareCalculation("isTrue == true && x > 0", variables));
        assertTrue(calculator.compareCalculation("items && file", variables));
        assertFalse(calculator.compareCalculation("emptyItems && file", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldHandleStandaloneSpecialValuesInCalculation(String name, ExpressionCalculator calculator) {
        assertEquals("true", calculator.calculation("file", variables));
        assertEquals("false", calculator.calculation("nullableFile", variables));
        assertEquals("true", calculator.calculation("items", variables));
        assertEquals("false", calculator.calculation("emptyItems", variables));
    }
}
