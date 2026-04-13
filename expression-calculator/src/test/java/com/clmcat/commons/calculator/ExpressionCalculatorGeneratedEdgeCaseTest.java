package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 生成式边界测试，覆盖方法调用、空值、null 比较和异常路径。
 */
class ExpressionCalculatorGeneratedEdgeCaseTest {

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
        variables.put("num1", new BigDecimal("123"));
        variables.put("num2", 55);
        variables.put("enabled", true);
        variables.put("nullable", null);

        Path filePath = tempDir.resolve("edge.txt");
        Files.writeString(filePath, "edge");
        File file = filePath.toFile();
        variables.put("file", file);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldRejectDirectNumericMethodArgumentsThatDoNotMatch(String name, ExpressionCalculator calculator) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculation("num1.add(55)", variables));
        assertTrue(exception.getMessage().contains("方法调用失败"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldCompareNullAndFilesExplicitly(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("nullable == null", variables));
        assertFalse(calculator.compareCalculation("file == null", variables));
        assertTrue(calculator.compareCalculation("file != null && file.exists()", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldSupportNestedMethodChains(String name, ExpressionCalculator calculator) {
        assertEquals("4", calculator.calculation("file.getName().substring(0, 4).length()", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldRejectNullMethodInvocation(String name, ExpressionCalculator calculator) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculation("nullable.toString()", variables));
        assertTrue(exception.getMessage().contains("对象为空"));
    }
}
