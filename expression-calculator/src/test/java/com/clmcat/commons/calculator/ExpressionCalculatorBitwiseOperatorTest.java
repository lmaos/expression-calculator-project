package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExpressionCalculatorBitwiseOperatorTest {

    private Map<String, Object> variables;

    static Stream<Arguments> calculators() {
        return Stream.of(
                Arguments.of("recursive", new RecursiveExpressionCalculator()),
                Arguments.of("iterative", new IterativeExpressionCalculator()));
    }

    @BeforeEach
    void setUp() {
        variables = new HashMap<>();
        variables.put("a", 10);
        variables.put("b", 12);
        variables.put("negative", -8);
        variables.put("decimal", 1.5);
        variables.put("enabled", true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持默认位运算")
    void shouldSupportDefaultBitwiseOperators(String name, ExpressionCalculator calculator) {
        assertEquals("8", calculator.calculation("a & b", variables));
        assertEquals("14", calculator.calculation("a | b", variables));
        assertEquals("6", calculator.calculation("a xor b", variables));
        assertEquals("-11", calculator.calculation("~a", variables));
        assertEquals("40", calculator.calculation("a << 2", variables));
        assertEquals("2", calculator.calculation("a >> 2", variables));
        assertEquals("2", calculator.calculation("a >>> 2", variables));
        assertEquals("40", calculator.calculation("a <<< 2", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持无符号右移与位运算比较")
    void shouldSupportUnsignedShiftAndComparisons(String name, ExpressionCalculator calculator) {
        assertEquals("9223372036854775804", calculator.calculation("negative >>> 1", variables));
        assertTrue(calculator.compareCalculation("(a & b) == 8", variables));
        assertTrue(calculator.compareCalculation("(a | b) == 14", variables));
        assertTrue(calculator.compareCalculation("(a xor b) == 6", variables));
        assertTrue(calculator.compareCalculation("(negative >>> 1) > 0", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应尊重位运算优先级")
    void shouldRespectBitwiseOperatorPrecedence(String name, ExpressionCalculator calculator) {
        assertEquals("12", calculator.calculation("2 + 1 << 2", variables));
        assertEquals("8", calculator.calculation("1 << 2 + 1", variables));
        assertEquals("8", calculator.calculation("8 | 2 & 1", variables));
        assertEquals("13", calculator.calculation("8 | 4 xor 1", variables));
        assertTrue(calculator.compareCalculation("1 | 2 == 3", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应拒绝非整数位运算")
    void shouldRejectNonIntegralBitwiseOperands(String name, ExpressionCalculator calculator) {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("decimal << 1", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("enabled & 1", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("1 xor 1.5", variables));
    }
}
