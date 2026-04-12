package com.example.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class ExpressionCalculatorTest {

    private Map<String, Object> variables;

    @BeforeEach
    void setUp() {
        variables = new HashMap<>();
        variables.put("a", 1);
        variables.put("b", 2);
        variables.put("c", 3);
        variables.put("x_y1", 7);
        variables.put("total_1", 10);
        variables.put("price", "12.5");
        variables.put("discount", 2.5);
    }

    static Stream<Arguments> calculators() {
        return Stream.of(
                Arguments.of("recursive", new RecursiveExpressionCalculator()),
                Arguments.of("iterative", new IterativeExpressionCalculator()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("单独输出变量值")
    void testSingleVariableCalculation(String name, ExpressionCalculator calculator) {
        assertEquals("1", calculator.calculation("a", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("单独输出纯值")
    void testSingleValueCalculation(String name, ExpressionCalculator calculator) {
        assertEquals("111", calculator.calculation("(111)", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持变量和括号计算")
    void shouldCalculateExpressionWithVariablesAndParentheses(String name, ExpressionCalculator calculator) {
        assertEquals("11", calculator.calculation("a + b * (c + 2)", variables));
    }


    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持空格和一元负号")
    void shouldRespectWhitespaceAndUnaryMinus(String name, ExpressionCalculator calculator) {
        assertEquals("-6", calculator.calculation(" -(a + b + c) ", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持小数除法")
    void shouldCalculateDecimalDivision(String name, ExpressionCalculator calculator) {
        assertEquals("5", calculator.calculation("price / discount", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持下划线变量和多层括号")
    void shouldCalculateUnderscoreVariablesAndNestedParentheses(String name, ExpressionCalculator calculator) {
        assertEquals("10.5", calculator.calculation("((x_y1 + total_1) / 2) + 2", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持连续一元运算")
    void shouldSupportRepeatedUnaryOperators(String name, ExpressionCalculator calculator) {
        assertEquals("3", calculator.calculation("--a + ++b", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持零值和前导零")
    void shouldHandleZeroAndLeadingZero(String name, ExpressionCalculator calculator) {
        assertEquals("0", calculator.calculation("000 + a - 1", variables));
        assertEquals("0", calculator.calculation("a - 1", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应拒绝非法数字格式")
    void shouldRejectInvalidNumberFormat(String name, ExpressionCalculator calculator) {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("1..2 + a", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation(".", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持大于比较")
    void shouldCompareGreaterThanExpression(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("a + b * (c + 2) > 10", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持相等比较")
    void shouldCompareEqualityExpression(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("(a + b) == c", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持不等比较")
    void shouldCompareInequalityExpression(String name, ExpressionCalculator calculator) {
        assertFalse(calculator.compareCalculation("price != 12.5", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持比较表达式中的空格和括号")
    void shouldSupportWhitespaceAndParenthesesInComparison(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation(" ( a + b ) >= 3 ", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持与运算")
    void shouldSupportAndOperator(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("a < b && c == 3", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持多段与运算链")
    void shouldSupportAndChain(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("a < b && b < c && c == 3", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持或运算")
    void shouldSupportOrOperator(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("a > b || c == 3", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持多段或运算链")
    void shouldSupportOrChain(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("a > b || b > c || c == 3", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应保证与运算优先级高于或运算")
    void shouldRespectAndPrecedenceOverOr(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("a > 0 || b > 10 && c > 10", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持布尔括号覆盖优先级")
    void shouldSupportBooleanParentheses(String name, ExpressionCalculator calculator) {
        assertFalse(calculator.compareCalculation("(a > 0 || b > 10) && c > 10", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应支持多层布尔括号")
    void shouldSupportDeepBooleanParentheses(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("(((a < b && c == 3)))", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("空表达式应报错")
    void shouldThrowWhenExpressionIsBlank(String name, ExpressionCalculator calculator) {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("   ", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("应拒绝非法字符")
    void shouldRejectIllegalCharacters(String name, ExpressionCalculator calculator) {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("a + #", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.compareCalculation("a > #", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("括号不匹配时应报错")
    void shouldThrowWhenParenthesesMismatch(String name, ExpressionCalculator calculator) {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("((a + b)", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("布尔表达式缺少操作数时应报错")
    void shouldThrowWhenBooleanOperandMissing(String name, ExpressionCalculator calculator) {
        assertThrows(IllegalArgumentException.class, () -> calculator.compareCalculation("a < b && ", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("缺少变量时应报错")
    void shouldThrowWhenVariableIsMissing(String name, ExpressionCalculator calculator) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculation("missing + 1", variables));
        assertEquals("变量不存在: missing", exception.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("除零时应报错")
    void shouldThrowWhenDividingByZero(String name, ExpressionCalculator calculator) {
        ArithmeticException exception = assertThrows(
                ArithmeticException.class,
                () -> calculator.calculation("a / (c - 3)", variables));
        assertEquals("除数不能为0", exception.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    @DisplayName("缺少比较符时应报错")
    void shouldThrowWhenComparisonOperatorIsMissing(String name, ExpressionCalculator calculator) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.compareCalculation("a + b", variables));
        assertEquals("比较表达式缺少比较运算符", exception.getMessage());
    }
}
