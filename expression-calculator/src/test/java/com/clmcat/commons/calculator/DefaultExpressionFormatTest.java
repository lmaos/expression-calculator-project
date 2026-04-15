package com.clmcat.commons.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 默认格式化器功能测试，同时覆盖 evaluate 与新语法在模板场景中的协同行为。
 */
class DefaultExpressionFormatTest {

    private final ConverterRegistry converterRegistry = ConverterRegistry.getInstance();
    private final OutputFormatRegistry outputFormatRegistry = OutputFormatRegistry.getInstance();

    @TempDir
    Path tempDir;

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

    private static final class DateRenderConfig {
        private final String pattern;
        private final String timeZone;

        private DateRenderConfig(String pattern, String timeZone) {
            this.pattern = pattern;
            this.timeZone = timeZone;
        }

        private String format(Date value) {
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            format.setTimeZone(TimeZone.getTimeZone(timeZone));
            return format.format(value);
        }
    }

    static Stream<Arguments> calculators() {
        return Stream.of(
                Arguments.of("recursive", new RecursiveExpressionCalculator()),
                Arguments.of("iterative", new IterativeExpressionCalculator()));
    }

    @BeforeEach
    void setUp() throws IOException {
        converterRegistry.resetToDefaults();
        outputFormatRegistry.resetToDefaults();

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
        variables.put("payload", "hello".getBytes(StandardCharsets.UTF_8));
        variables.put("date", new Date(0L));
        variables.put("amount", new BigDecimal("12.50"));
        variables.put("return.value", "success");
        variables.put("deep.path.value", "done");

        Path filePath = tempDir.resolve("format-output.txt");
        Files.write(filePath, "file-content".getBytes(StandardCharsets.UTF_8));
        variables.put("file", filePath.toFile());
    }

    @AfterEach
    void tearDown() {
        converterRegistry.resetToDefaults();
        outputFormatRegistry.resetToDefaults();
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldResolveMissingVariableDotChainsAsDirectKeys(String name, ExpressionCalculator calculator) {
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);

        assertEquals("success", calculator.evaluate("return.value", variables));
        assertEquals("done", calculator.evaluate("deep.path.value", variables));
        assertEquals(7, calculator.evaluate("return.value.length()", variables));

        assertEquals("success", formatter.format("${return.value}", variables));
        assertEquals("7", formatter.format("${return.value.length()}", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldSupportTypeSpecificOutputRegistryOverrides(String name, ExpressionCalculator calculator) {
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);
        OutputFormatRegistry localRegistry = outputFormatRegistry.copy();
        localRegistry.setOption(byte[].class, "mode", "base64");
        localRegistry.setOption(File.class, "mode", "content");
        localRegistry.setOption(File.class, "charset", "UTF-8");
        localRegistry.setOption(Date.class, "pattern", "yyyyMMdd");
        localRegistry.setOption(Date.class, "timeZone", "UTC");
        localRegistry.register(BigDecimal.class, (value, context) ->
                context.stringOption("prefix", "") + ((BigDecimal) value).stripTrailingZeros().toPlainString());
        localRegistry.setOption(BigDecimal.class, "prefix", "N=");

        String formatted = formatter.format(
                "bytes=${payload}|file=${file}|date=${date}|amount=${amount}",
                "${?}",
                variables,
                localRegistry);
        assertEquals("bytes=aGVsbG8=|file=file-content|date=19700101|amount=N=12.5", formatted);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldUseGlobalDefaultOutputRegistryForLegacyOverload(String name, ExpressionCalculator calculator) {
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);
        outputFormatRegistry.setOption(Date.class, "pattern", "yyyy/MM/dd");
        outputFormatRegistry.setOption(Date.class, "timeZone", "UTC");

        assertEquals("1970/01/01", formatter.format("${date}", variables));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldAllowOutputFormatCallbackToOverrideReferenceOutput(String name, ExpressionCalculator calculator) {
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);
        OutputFormatRegistry localRegistry = outputFormatRegistry.copy();
        localRegistry.setOption(Date.class, "pattern", "yyyyMMdd");
        localRegistry.setOption(Date.class, "timeZone", "UTC");
        localRegistry.setFormatCallback((value, context) -> {
            if (!(value instanceof Date) || context.expressionKind() != OutputExpressionKind.REFERENCE) {
                return null;
            }
            assertEquals("date", context.expression());
            assertEquals("19700101", context.defaultFormattedValue());
            assertEquals(Date.class, context.registeredType());
            return "ref(" + context.defaultFormattedValue() + ")";
        });

        assertEquals("ref=ref(19700101)|expr=3", formatter.format("ref=${date}|expr=${1 + 2}", variables, localRegistry));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calculators")
    void shouldSupportDynamicDateFormattingFromExpressionCallback(String name, ExpressionCalculator calculator) {
        ExpressionFormat formatter = new DefaultExpressionFormat(calculator);
        OutputFormatRegistry localRegistry = outputFormatRegistry.copy();
        Map<String, Object> localVariables = new HashMap<String, Object>(variables);
        localVariables.put("dateUtc", new Date(0L));
        localVariables.put("dateChina", new Date(0L));

        Map<String, DateRenderConfig> renderConfigs = new HashMap<String, DateRenderConfig>();
        renderConfigs.put("dateUtc", new DateRenderConfig("yyyy-MM-dd HH:mm", "UTC"));
        renderConfigs.put("dateChina", new DateRenderConfig("yyyy-MM-dd HH:mm", "GMT+08:00"));

        localRegistry.setFormatCallback((value, context) -> {
            if (!(value instanceof Date) || context.expressionKind() != OutputExpressionKind.REFERENCE) {
                return null;
            }
            DateRenderConfig renderConfig = renderConfigs.get(context.expression());
            return renderConfig == null ? null : renderConfig.format((Date) value);
        });

        assertEquals(
                "zxx-1970-01-01 00:00-1970-01-01 08:00-zip",
                formatter.format("zxx-${dateUtc}-${dateChina}-zip", localVariables, localRegistry));
    }
}
