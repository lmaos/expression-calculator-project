package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExpressionCalculatorLiteralBoundaryTest {

    private Map<String, Object> variables;

    static Stream<Arguments> calculators() {
        return Stream.of(
                Arguments.of("recursive", new RecursiveExpressionCalculator()),
                Arguments.of("iterative", new IterativeExpressionCalculator()));
    }

    @BeforeEach
    void setUp() {
        variables = new HashMap<>();
        variables.put("quotedOr", "a||b");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldRejectBrokenStringAndCharacterLiterals(String name, ExpressionCalculator calculator) {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("\"unterminated", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("'ab'", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculation("'\\x'", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldIgnoreQuotedBooleanOperatorsDuringBoundaryParsing(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("\"a||b\" == quotedOr || missing == 1", variables));
    }
}
