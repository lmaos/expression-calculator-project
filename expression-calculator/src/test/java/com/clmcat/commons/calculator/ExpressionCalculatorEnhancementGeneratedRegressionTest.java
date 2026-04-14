package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 生成式回归测试，枚举更多组合场景，确保增强语法和结果类型长期稳定。
 */
class ExpressionCalculatorEnhancementGeneratedRegressionTest {

    private final ConverterRegistry converterRegistry = ConverterRegistry.getInstance();

    private Map<String, Object> variables;

    @BeforeEach
    void setUp() {
        converterRegistry.resetToDefaults();

        variables = new HashMap<>();
        variables.put("numbers", Arrays.asList(1, 2, 3, 4));
        variables.put("matrix", Arrays.asList(Arrays.asList(1, 2), Arrays.asList(3, 4)));

        Map<String, Object> inventory = new HashMap<>();
        inventory.put("count", 3);
        variables.put("inventory", inventory);

        Map<Boolean, String> flags = new HashMap<>();
        flags.put(Boolean.TRUE, "T");
        flags.put(Boolean.FALSE, "F");
        variables.put("flags", flags);

        Map<Integer, String> lookup = new HashMap<>();
        lookup.put(1, "one");
        lookup.put(3, "three");
        variables.put("lookup", lookup);

        variables.put("prices", Arrays.asList(1.2, 2.5));
        variables.put("index", 1);
        variables.put("flagA", true);
        variables.put("flagB", false);
    }

    @AfterEach
    void tearDown() {
        converterRegistry.resetToDefaults();
    }

    static Stream<Arguments> calculators() {
        return Stream.of(
                Arguments.of("recursive", new RecursiveExpressionCalculator()),
                Arguments.of("iterative", new IterativeExpressionCalculator()));
    }

    static Stream<Arguments> generatedEvaluateCases() {
        return Stream.of(
                Arguments.of("1 + 2", Integer.class, 3),
                Arguments.of("1 + 2L", Long.class, 3L),
                Arguments.of("2147483647 + 1", Long.class, 2147483648L),
                Arguments.of("7 - 2", Integer.class, 5),
                Arguments.of("6 * 7", Integer.class, 42),
                Arguments.of("8 / 2", Integer.class, 4),
                Arguments.of("7 / 2", BigDecimal.class, new BigDecimal("3.5")),
                Arguments.of("numbers[index + 1]", Integer.class, 3),
                Arguments.of("matrix[1][0]", Integer.class, 3),
                Arguments.of("lookup[numbers[0]]", String.class, "one"),
                Arguments.of("inventory['count'] + numbers[0]", Integer.class, 4),
                Arguments.of("(int)(10L + 5)", Integer.class, 15),
                Arguments.of("(String)(inventory['count'] + numbers[0])", String.class, "4"),
                Arguments.of("\"hello\"[1]", Character.class, 'e'),
                Arguments.of("'ab' + 'cd'", String.class, "abcd"),
                Arguments.of("flags[flagA || flagB]", String.class, "T"),
                Arguments.of("(int)(prices[0] + 1.8)", Integer.class, 3));
    }

    static Stream<Arguments> generatedFormatCases() {
        return Stream.of(
                Arguments.of("count=${inventory['count']}", "${?}", "count=3"),
                Arguments.of("next=${numbers[index + 1]}", "${?}", "next=3"),
                Arguments.of("letter=${\"hello\"[4]}", "${?}", "letter=o"),
                Arguments.of("ok=${flagA || flagB}", "${?}", "ok=true"),
                Arguments.of("text=${'ab' + 'cd'}", "${?}", "text=abcd"),
                Arguments.of("\\${inventory['count']} / ${(String)(inventory['count'] + numbers[0])}", "${?}",
                        "${inventory['count']} / 4"),
                Arguments.of("#{inventory['count']} + #{numbers[index + 1]}", "#{?}", "3 + 3"));
    }

    @ParameterizedTest(name = "{0} :: {1}")
    @MethodSource("calculatorAndEvaluateCases")
    void shouldMatchGeneratedEvaluateCases(String calculatorName, ExpressionCalculator calculator, String expression,
            Class<?> expectedType, Object expectedValue) {
        Object actual = calculator.evaluate(expression, variables);
        assertTrue(expectedType.isInstance(actual));
        assertValueEquals(expectedValue, actual);
    }

    @ParameterizedTest(name = "{0} :: {1}")
    @MethodSource("calculatorAndFormatCases")
    void shouldMatchGeneratedFormatCases(String calculatorName, ExpressionCalculator calculator, String text, String rule,
            String expected) {
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);
        assertEquals(expected, formatter.format(text, rule, variables));
    }

    static Stream<Arguments> calculatorAndEvaluateCases() {
        return calculators().flatMap(calculatorArgs ->
                generatedEvaluateCases().map(caseArgs -> Arguments.of(
                        calculatorArgs.get()[0],
                        calculatorArgs.get()[1],
                        caseArgs.get()[0],
                        caseArgs.get()[1],
                        caseArgs.get()[2])));
    }

    static Stream<Arguments> calculatorAndFormatCases() {
        return calculators().flatMap(calculatorArgs ->
                generatedFormatCases().map(caseArgs -> Arguments.of(
                        calculatorArgs.get()[0],
                        calculatorArgs.get()[1],
                        caseArgs.get()[0],
                        caseArgs.get()[1],
                        caseArgs.get()[2])));
    }

    private static void assertValueEquals(Object expected, Object actual) {
        if (expected instanceof BigDecimal && actual instanceof BigDecimal) {
            assertEquals(0, ((BigDecimal) expected).compareTo((BigDecimal) actual));
            return;
        }
        assertEquals(expected, actual);
    }
}
