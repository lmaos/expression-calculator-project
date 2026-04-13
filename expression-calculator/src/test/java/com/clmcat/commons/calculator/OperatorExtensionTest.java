package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OperatorExtensionTest {

    private final OperatorRegistry registry = OperatorRegistry.getInstance();

    private Map<String, Object> variables;

    static Stream<Arguments> calculators() {
        return Stream.of(
                Arguments.of("recursive", new RecursiveExpressionCalculator()),
                Arguments.of("iterative", new IterativeExpressionCalculator()));
    }

    @BeforeEach
    void setUp() {
        registry.resetToDefaults();

        variables = new HashMap<>();
        variables.put("a", 1);
        variables.put("b", 2);
        variables.put("c", 3);
    }

    @AfterEach
    void tearDown() {
        registry.resetToDefaults();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("注册扩展运算符后应保持原有运算行为")
    void shouldKeepExistingOperatorsCompatible(String name, ExpressionCalculator calculator) {
        assertEquals("11", calculator.calculation("a + b * (c + 2)", variables));
        assertTrue(calculator.compareCalculation("(a + b) == c", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持取模运算符")
    void shouldSupportModuloOperator(String name, ExpressionCalculator calculator) {
        assertEquals("1", calculator.calculation("10 % 3", variables));
        assertEquals("1", calculator.calculation("7 % -2", variables));
        assertTrue(calculator.compareCalculation("10 % 3 == 1", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持幂运算与右结合")
    void shouldSupportPowerOperator(String name, ExpressionCalculator calculator) {
        assertEquals("8", calculator.calculation("2 ^ 3", variables));
        assertEquals("2", calculator.calculation("4 ^ 0.5", variables));
        assertEquals("4", calculator.calculation("(-2) ^ 2", variables));
        assertEquals("512", calculator.calculation("2 ^ 3 ^ 2", variables));
        assertEquals("64", calculator.calculation("(2 ^ 3) ^ 2", variables));
        assertTrue(calculator.compareCalculation("2 ^ 3 ^ 2 == 512", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应尊重扩展运算符优先级")
    void shouldRespectExtendedOperatorPrecedence(String name, ExpressionCalculator calculator) {
        assertEquals("50", calculator.calculation("2 + 3 * 4 ^ 2", variables));
        assertEquals("80", calculator.calculation("(2 + 3) * 4 ^ 2", variables));
        assertTrue(calculator.compareCalculation("2 + 3 * 4 ^ 2 == 50", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("扩展运算符的异常路径应保持可预期")
    void shouldHandleExtendedOperatorErrors(String name, ExpressionCalculator calculator) {
        ArithmeticException arithmeticException = assertThrows(
                ArithmeticException.class,
                () -> calculator.calculation("5 % 0", variables));
        assertEquals("除数不能为0", arithmeticException.getMessage());

        IllegalArgumentException formatException = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculation("2 ^ ", variables));
        assertEquals("表达式格式错误", formatException.getMessage());
    }

    @Test
    @DisplayName("应拒绝注册短路逻辑运算符")
    void shouldRejectShortCircuitOperatorRegistration() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.registerBinary("&&", 1, Associativity.LEFT, (left, right) -> RuntimeValue.computed(true)));
        assertEquals("逻辑短路运算符不能通过普通运算符注册: &&", exception.getMessage());
    }

    @Test
    @DisplayName("应拒绝空白运算符符号")
    void shouldRejectBlankOperatorSymbol() {
        IllegalArgumentException binaryException = assertThrows(
                IllegalArgumentException.class,
                () -> registry.registerBinary(" ", 1, Associativity.LEFT, (left, right) -> RuntimeValue.computed(true)));
        assertEquals("运算符符号不能为空", binaryException.getMessage());

        IllegalArgumentException unaryException = assertThrows(
                IllegalArgumentException.class,
                () -> registry.registerUnary("", 1, Associativity.RIGHT, value -> value));
        assertEquals("运算符符号不能为空", unaryException.getMessage());
    }
}
