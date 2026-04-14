package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * 默认格式化器功能测试，同时覆盖 evaluate 与新语法在模板场景中的协同行为。
 */
class DefaultExpressionFormatTest {

    private final ConverterRegistry converterRegistry = ConverterRegistry.getInstance();

    private Map<String, Object> variables;

    public static final class PublicChild {
        public final int count;

        private PublicChild(int count) {
            this.count = count;
        }
    }

    public static final class PublicHolder {
        public final String name;
        public final PublicChild child;

        private PublicHolder(String name, PublicChild child) {
            this.name = name;
            this.child = child;
        }
    }

    static Stream<Arguments> calculators() {
        return Stream.of(
                Arguments.of("recursive", new RecursiveExpressionCalculator()),
                Arguments.of("iterative", new IterativeExpressionCalculator()));
    }

    @BeforeEach
    void setUp() {
        converterRegistry.resetToDefaults();

        variables = new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        map.put("name", "World");
        map.put("count", 3);
        variables.put("map", map);
        variables.put("list", Arrays.asList(10, 20, 30));
        variables.put("array", new String[] {"alpha", "beta", "gamma"});
        variables.put("index", 0);
        variables.put("nullable", null);
        variables.put("flagA", true);
        variables.put("flagB", false);
        variables.put("str", "123");
        variables.put("holder", new PublicHolder("alpha", new PublicChild(7)));
    }

    @AfterEach
    void tearDown() {
        converterRegistry.resetToDefaults();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldEvaluateRawValuesWithNewEnhancements(String name, ExpressionCalculator calculator) {
        Object integerResult = calculator.evaluate("1 + 2", variables);
        assertTrue(integerResult instanceof Integer);
        assertEquals(3, integerResult);

        Object longResult = calculator.evaluate("1 + 2L", variables);
        assertTrue(longResult instanceof Long);
        assertEquals(3L, longResult);

        Object decimalResult = calculator.evaluate("1 + 2.0", variables);
        assertTrue(decimalResult instanceof BigDecimal);
        assertEquals(0, ((BigDecimal) decimalResult).compareTo(BigDecimal.valueOf(3)));

        assertEquals(Boolean.TRUE, calculator.evaluate("flagA || flagB", variables));
        assertEquals("World", calculator.evaluate("map['name']", variables));
        assertNull(calculator.evaluate("missing", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldFormatDefaultAndCustomRules(String name, ExpressionCalculator calculator) {
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);

        assertEquals("1 + 2 = 3", formatter.format("1 + 2 = ${1 + 2}", variables));
        assertEquals("Hello World", formatter.format("Hello ${map['name']}", variables));
        assertEquals("3", formatter.format("#{1 + 2}", "#{?}", variables));
        assertEquals("World", formatter.format("{{ map['name'] }}", "{{?}}", variables));
        assertEquals("ok=true", formatter.format("ok=${flagA || flagB}", variables));
        assertEquals("", formatter.format("${nullable}", variables));
        assertEquals("", formatter.format("${missing}", variables));
        assertEquals("plain text", formatter.format("plain text", variables));
        assertEquals("", formatter.format("", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldSupportEscapingAndAdvancedExpressions(String name, ExpressionCalculator calculator) {
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);

        assertEquals("${1+2}", formatter.format("\\${1+2}", variables));
        assertEquals("a${map['name']}", formatter.format("a\\${map['name']}", variables));
        assertEquals("20 / beta / 15", formatter.format("${list[1]} / ${array[index + 1]} / ${(int)(10L + 5)}", variables));
        assertEquals("abcd", formatter.format("${'ab' + 'cd'}", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldFormatDenseTemplateWithoutLosingOrder(String name, ExpressionCalculator calculator) {
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);
        StringBuilder template = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        for (int index = 0; index < 50; index++) {
            if (index > 0) {
                template.append(" | ");
                expected.append(" | ");
            }
            template.append("${map['count'] + ").append(index).append("}");
            expected.append(3 + index);
        }
        assertEquals(expected.toString(), formatter.format(template.toString(), variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldSupportCustomConvertersInEvaluateAndFormat(String name, ExpressionCalculator calculator) {
        converterRegistry.register("wrapped", value -> value == null ? null : "[" + value + "]");
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);

        assertEquals("[123]", calculator.evaluate("(wrapped)123", variables));
        assertEquals("value=[3]", formatter.format("value=${(wrapped)(map['count'])}", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldSupportPublicMethodAndFieldAccessOnObjects(String name, ExpressionCalculator calculator) {
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);
        assertEquals("3", formatter.format("${str.length()}", variables));
        assertEquals("3", formatter.format("${list.size()}", variables));
        assertEquals("3", formatter.format("${'123'.length()}", variables));
        assertEquals("3", formatter.format("${array.length}", variables));
        assertEquals("alpha", formatter.format("${holder.name}", variables));
        assertEquals("7", formatter.format("${holder.child.count}", variables));
        assertEquals("5", formatter.format("${holder.name.length()}", variables));

        assertEquals(3, calculator.evaluate("array.length", variables));
        assertEquals("alpha", calculator.evaluate("holder.name", variables));
        assertEquals(7, calculator.evaluate("holder.child.count", variables));
    }
}
