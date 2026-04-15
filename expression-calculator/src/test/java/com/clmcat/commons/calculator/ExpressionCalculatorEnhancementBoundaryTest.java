package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 新增能力的边界条件测试，覆盖非法下标、转换失败和模板异常路径。
 */
class ExpressionCalculatorEnhancementBoundaryTest {

    private final ConverterRegistry converterRegistry = ConverterRegistry.getInstance();
    private final OutputFormatRegistry outputFormatRegistry = OutputFormatRegistry.getInstance();

    private Map<String, Object> variables;

    static Stream<Arguments> calculators() {
        return Stream.of(
                Arguments.of("recursive", new RecursiveExpressionCalculator()),
                Arguments.of("iterative", new IterativeExpressionCalculator()));
    }

    @BeforeEach
    void setUp() {
        converterRegistry.resetToDefaults();
        outputFormatRegistry.resetToDefaults();

        variables = new HashMap<>();
        variables.put("list", Arrays.asList(10, 20, 30));
        variables.put("number", 7);
        variables.put("nullable", null);
        variables.put("index", 1);
        variables.put("payload", "abc".getBytes(StandardCharsets.UTF_8));
        variables.put("date", new Date(0L));
    }

    @AfterEach
    void tearDown() {
        converterRegistry.resetToDefaults();
        outputFormatRegistry.resetToDefaults();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldRejectInvalidIndexAndCastOperations(String name, ExpressionCalculator calculator) {
        IllegalArgumentException decimalIndex = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.evaluate("list[1.5]", variables));
        assertTrue(decimalIndex.getMessage().contains("下标必须是整数"));

        IllegalArgumentException outOfBounds = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.evaluate("list[10]", variables));
        assertTrue(outOfBounds.getMessage().contains("下标越界"));

        IllegalArgumentException unsupportedType = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.evaluate("number[0]", variables));
        assertTrue(unsupportedType.getMessage().contains("类型不支持下标访问"));

        IllegalArgumentException byteOverflow = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.evaluate("(byte)300", variables));
        assertTrue(byteOverflow.getMessage().contains("byte"));

        IllegalArgumentException invalidChar = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.evaluate("(char)'AB'", variables));
        assertTrue(invalidChar.getMessage().contains("char"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldHandleFormatBoundaryCases(String name, ExpressionCalculator calculator) {
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);

        assertThrows(IllegalArgumentException.class, () -> formatter.format(null, variables));
        assertThrows(IllegalArgumentException.class, () -> formatter.format("${1+2", variables));
        assertThrows(ArithmeticException.class, () -> formatter.format("${1/0}", variables));
        assertThrows(IllegalArgumentException.class, () -> formatter.format("${1+}", variables));
        assertThrows(IllegalArgumentException.class, () -> formatter.format("text", null, variables));
        assertThrows(IllegalArgumentException.class, () -> formatter.format("text", "{}", variables));
        assertThrows(IllegalArgumentException.class, () -> formatter.format("text", "${??}", variables));
        assertThrows(IllegalArgumentException.class, () -> formatter.format("${name_${index}}", variables));

        assertEquals("", formatter.format("", variables));
        assertEquals("plain", formatter.format("plain", variables));
        assertEquals("", formatter.format("${nullable}", null));
        assertEquals("", formatter.format("${missing}", null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldPreserveMissingVariableSemantics(String name, ExpressionCalculator calculator) {
        assertNull(calculator.evaluate("nullable", variables));
        assertNull(calculator.evaluate("missing", variables));

        IllegalArgumentException missingValue = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.evaluate("missing + 1", variables));
        assertTrue(missingValue.getMessage().contains("变量不存在"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldRejectInvalidOutputRegistryConfiguration(String name, ExpressionCalculator calculator) {
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);

        OutputFormatRegistry invalidBytes = outputFormatRegistry.copy();
        invalidBytes.setOption(byte[].class, "mode", "unknown");
        IllegalArgumentException invalidByteMode = assertThrows(
                IllegalArgumentException.class,
                () -> formatter.format("${payload}", "${?}", variables, invalidBytes));
        assertTrue(invalidByteMode.getMessage().contains("byte[]"));

        OutputFormatRegistry invalidDate = outputFormatRegistry.copy();
        invalidDate.setOption(Date.class, "timeZone", "Not/AZone");
        IllegalArgumentException invalidTimeZone = assertThrows(
                IllegalArgumentException.class,
                () -> formatter.format("${date}", "${?}", variables, invalidDate));
        assertTrue(invalidTimeZone.getMessage().contains("时区"));
    }
}
