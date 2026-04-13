package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IterativeExpressionCalculatorSpecialTypesTest {

    @TempDir
    Path tempDir;

    private ExpressionCalculator calculator;
    private Map<String, Object> variables;

    static Stream<Arguments> calculators() {
        return Stream.of(
                Arguments.of("recursive", new RecursiveExpressionCalculator()),
                Arguments.of("iterative", new IterativeExpressionCalculator()));
    }

    @BeforeEach
    void setUp() throws Exception {
        variables = new HashMap<>();
        variables.put("a", 1);
        variables.put("b", 2);
        variables.put("c", 3);
        variables.put("enabled", true);
        variables.put("disabled", false);
        variables.put("startDate", LocalDate.of(2024, 1, 1));
        variables.put("endDate", LocalDate.of(2024, 1, 2));
        variables.put("alpha", "alpha");
        variables.put("beta", "beta");
        variables.put("items", List.of(1, 2));
        variables.put("emptyItems", Collections.emptyList());
        variables.put("metadata", Map.of("k", "v"));
        variables.put("emptyMetadata", Collections.emptyMap());
        variables.put("nullable", null);

        Path existingPath = tempDir.resolve("exists.txt");
        Files.writeString(existingPath, "ok");
        variables.put("existingFile", existingPath.toFile());
        variables.put("missingFile", tempDir.resolve("missing.txt").toFile());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldEvaluateStandaloneBooleanAndFileAndCollectionVariables(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("enabled", variables));
        assertFalse(calculator.compareCalculation("disabled", variables));
        assertTrue(calculator.compareCalculation("existingFile", variables));
        assertFalse(calculator.compareCalculation("missingFile", variables));
        assertTrue(calculator.compareCalculation("items", variables));
        assertFalse(calculator.compareCalculation("emptyItems", variables));
        assertTrue(calculator.compareCalculation("metadata", variables));
        assertFalse(calculator.compareCalculation("emptyMetadata", variables));
        assertFalse(calculator.compareCalculation("nullable", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldSupportComparableOrderingForVariables(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("startDate < endDate", variables));
        assertTrue(calculator.compareCalculation("alpha < beta", variables));
        assertTrue(calculator.compareCalculation("enabled == true && disabled == false", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldCombineSpecialOperandsWithBooleanOperators(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("enabled && existingFile && items && metadata", variables));
        assertFalse(calculator.compareCalculation("enabled && missingFile && items", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldShortCircuitBooleanOperators(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("enabled || missingBoolean", variables));
        assertFalse(calculator.compareCalculation("disabled && missingBoolean", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldHandleNestedBooleanWrappersAndWhitespace(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("  ((( enabled )))  ", variables));
        assertTrue(calculator.compareCalculation(" ( ( startDate < endDate ) ) ", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldEvaluateComplexLogicFromSimpleToNested(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("(a + b) > c || existingFile", variables));
        assertFalse(calculator.compareCalculation("(a + b) > c || existingFile && ((a + 1) == 3)", variables));
        assertTrue(calculator.compareCalculation("(a + b) > 1 || existingFile && ((a + 1) == 2)", variables));
        assertTrue(calculator.compareCalculation("(a + b) > c || existingFile && ((a + 2) == 3)", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldRejectOrderingComparisonForBooleanValues(String name, ExpressionCalculator calculator) {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.compareCalculation("enabled > disabled", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldRejectDanglingBooleanOperators(String name, ExpressionCalculator calculator) {
        assertThrows(IllegalArgumentException.class, () -> calculator.compareCalculation("enabled & items", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.compareCalculation("enabled | items", variables));
        assertThrows(IllegalArgumentException.class, () -> calculator.compareCalculation("enabled ? items : existingFile", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldHandleMixedComplexLogicWithCollectionsAndFiles(String name, ExpressionCalculator calculator) {
        assertTrue(calculator.compareCalculation("enabled && (items || existingFile) && (startDate < endDate)", variables));
        assertFalse(calculator.compareCalculation("disabled || emptyItems && existingFile", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldCompareFilesByTruthinessAndEquality(String name, ExpressionCalculator calculator) {
        File existingFile = (File) variables.get("existingFile");
        File samePath = new File(existingFile.getPath());
        variables.put("sameExistingFile", samePath);

        assertTrue(calculator.compareCalculation("sameExistingFile == existingFile", variables));
        assertTrue(calculator.compareCalculation("sameExistingFile", variables));
    }
}
