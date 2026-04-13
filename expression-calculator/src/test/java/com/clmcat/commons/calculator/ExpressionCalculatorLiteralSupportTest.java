package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExpressionCalculatorLiteralSupportTest {

    private Map<String, Object> variables;

    static Stream<Arguments> calculators() {
        return Stream.of(
                Arguments.of("recursive", new RecursiveExpressionCalculator()),
                Arguments.of("iterative", new IterativeExpressionCalculator()));
    }

    @BeforeEach
    void setUp() {
        variables = new HashMap<>();
        variables.put("name", "copilot");
        variables.put("quotedOr", "a||b");
        variables.put("quotedAnd", "x&&y");
        variables.put("quotedEscaped", "a\"||b");
        variables.put("wrapped", "(demo)");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldSupportStringAndCharacterLiteralsInCalculation(String name, ExpressionCalculator calculator) {
        assertEquals("hello", calculator.calculation("\"hello\"", variables));
        assertEquals("A", calculator.calculation("'A'", variables));
        assertEquals("line\nbreak", calculator.calculation("\"line\\nbreak\"", variables));
        assertEquals("hello, copilot", calculator.calculation("\"hello, \" + name", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldSupportStringLiteralsInComparisonAndMethodArguments(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("\"\" == \"\"", variables));
        assertTrue(calculator.compareCalculation("\"a||b\" == quotedOr && \"x&&y\" == quotedAnd", variables));
        assertTrue(calculator.compareCalculation("\"a\\\"||b\" == quotedEscaped", variables));
        assertTrue(calculator.compareCalculation("'\\n' == '\\n'", variables));
        assertEquals("c;pilot", calculator.calculation("name.replaceFirst(\"o\", \";\")", variables));
        assertEquals("a;b", calculator.calculation("\"a,b\".replace(\",\", \";\")", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldPreserveQuotedParenthesesDuringBooleanParsing(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("\"(demo)\" == wrapped", variables));
    }
}
