package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
        Files.write(filePath, "edge".getBytes(StandardCharsets.UTF_8));
        File file = filePath.toFile();
        variables.put("file", file);
        variables.put("notExistFile", new File("abc.txt")); // 不存在的文件
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
        assertTrue(calculator.compareCalculation("file", variables));
        assertFalse(calculator.compareCalculation("!file", variables));
        assertFalse(calculator.compareCalculation("!file.exists()", variables));
        assertTrue(calculator.compareCalculation("!notExistFile", variables));
        assertFalse(calculator.compareCalculation("notExistFile", variables));
        assertFalse(calculator.compareCalculation("notExistFile.exists()", variables));
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldRejectInvalidPublicFieldAccess(String name, ExpressionCalculator calculator) {
        IllegalArgumentException missingField = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculation("file.missingField", variables));
        assertTrue(missingField.getMessage().contains("字段访问失败"));

        IllegalArgumentException nullReceiver = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculation("nullable.value", variables));
        assertTrue(nullReceiver.getMessage().contains("对象为空"));
    }
}
