package com.clmcat.commons.calculator;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表达式输出格式注册中心。
 *
 * <p>它和 {@link ConverterRegistry} 的职责类似，但面向模板输出阶段：
 * 表达式先通过 {@link ExpressionCalculator#evaluate(String, java.util.Map)} 求值得到原始对象，
 * 再由本注册中心按对象类型和动态配置渲染成最终字符串。
 *
 * <p>除了按类型注册 {@link OutputFormatter}，还可以通过 {@link #setFormatCallback(OutputFormatCallback)}
 * 挂载一个最终输出回调。回调会拿到原始值、占位表达式文本、表达式类型以及默认格式化结果，
 * 适合按变量名、存储来源或对象自身动态属性覆盖最终输出。
 */
public final class OutputFormatRegistry {

    private static final OutputFormatRegistry INSTANCE = new OutputFormatRegistry(true);

    private final ConcurrentHashMap<Class<?>, OutputFormatter> formatters = new ConcurrentHashMap<Class<?>, OutputFormatter>();
    private final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Object>> options =
            new ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Object>>();
    private volatile OutputFormatCallback formatCallback;

    private OutputFormatRegistry(boolean registerDefaults) {
        if (registerDefaults) {
            registerDefaults();
        }
    }

    public static OutputFormatRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized OutputFormatter register(Class<?> type, OutputFormatter formatter) {
        return formatters.put(normalizeType(type), Objects.requireNonNull(formatter, "formatter"));
    }

    public synchronized OutputFormatter unregister(Class<?> type) {
        return formatters.remove(normalizeType(type));
    }

    public synchronized Object setOption(Class<?> type, String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("输出选项值不能为空");
        }
        ConcurrentHashMap<String, Object> typeOptions = options.computeIfAbsent(
                normalizeType(type),
                key -> new ConcurrentHashMap<String, Object>());
        return typeOptions.put(normalizeOptionName(name), value);
    }

    public synchronized void setOptions(Class<?> type, Map<String, Object> optionMap) {
        Objects.requireNonNull(optionMap, "optionMap");
        for (Map.Entry<String, Object> entry : optionMap.entrySet()) {
            setOption(type, entry.getKey(), entry.getValue());
        }
    }

    public synchronized Object removeOption(Class<?> type, String name) {
        Map<String, Object> typeOptions = options.get(normalizeType(type));
        if (typeOptions == null) {
            return null;
        }
        return typeOptions.remove(normalizeOptionName(name));
    }

    public synchronized void clearOptions(Class<?> type) {
        options.remove(normalizeType(type));
    }

    public Map<String, Object> optionsOf(Class<?> type) {
        Map<String, Object> typeOptions = options.get(normalizeType(type));
        if (typeOptions == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<String, Object>(typeOptions));
    }

    public synchronized void resetToDefaults() {
        formatters.clear();
        options.clear();
        formatCallback = null;
        registerDefaults();
    }

    public OutputFormatRegistry copy() {
        OutputFormatRegistry copy = new OutputFormatRegistry(false);
        copy.formatters.putAll(formatters);
        for (Map.Entry<Class<?>, ConcurrentHashMap<String, Object>> entry : options.entrySet()) {
            copy.options.put(entry.getKey(), new ConcurrentHashMap<String, Object>(entry.getValue()));
        }
        copy.formatCallback = formatCallback;
        return copy;
    }

    public String formatValue(Object value) {
        return formatValue(value, null, null);
    }

    /**
     * 按值、占位表达式与表达式类型渲染字符串。
     *
     * <p>先执行已注册的默认类型格式化，再执行可选的最终输出回调。
     *
     * @param value 当前值
     * @param expression 当前模板占位符中的表达式文本
     * @param expressionKind 当前表达式类型
     * @return 格式化结果；返回 {@code null} 时上层模板实现通常会按空字符串处理
     */
    public String formatValue(Object value, String expression, OutputExpressionKind expressionKind) {
        RegisteredFormatter registeredFormatter = value == null ? null : resolveFormatter(value.getClass());
        OutputFormatContext context = null;
        String formattedValue;
        if (registeredFormatter == null) {
            formattedValue = value == null ? null : String.valueOf(value);
        } else {
            Map<String, Object> typeOptions = options.get(registeredFormatter.registeredType);
            context = new OutputFormatContext(
                    registeredFormatter.registeredType,
                    typeOptions == null ? Collections.<String, Object>emptyMap() : new HashMap<String, Object>(typeOptions),
                    expression,
                    expressionKind);
            formattedValue = registeredFormatter.formatter.format(value, context);
        }

        OutputFormatCallback callback = formatCallback;
        if (callback == null) {
            return formattedValue;
        }
        OutputFormatCallbackContext callbackContext = new OutputFormatCallbackContext(
                value,
                expression,
                expressionKind,
                formattedValue,
                context);
        String overriddenValue = callback.format(value, callbackContext);
        return overriddenValue != null ? overriddenValue : formattedValue;
    }

    public synchronized OutputFormatCallback setFormatCallback(OutputFormatCallback callback) {
        OutputFormatCallback previous = formatCallback;
        formatCallback = Objects.requireNonNull(callback, "callback");
        return previous;
    }

    public synchronized OutputFormatCallback clearFormatCallback() {
        OutputFormatCallback previous = formatCallback;
        formatCallback = null;
        return previous;
    }

    public OutputFormatCallback formatCallback() {
        return formatCallback;
    }

    private RegisteredFormatter resolveFormatter(Class<?> runtimeType) {
        Deque<Class<?>> queue = new ArrayDeque<Class<?>>();
        Set<Class<?>> visited = new HashSet<Class<?>>();
        queue.add(runtimeType);
        while (!queue.isEmpty()) {
            Class<?> current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            OutputFormatter formatter = formatters.get(current);
            if (formatter != null) {
                return new RegisteredFormatter(current, formatter);
            }
            Class<?> superclass = current.getSuperclass();
            if (superclass != null) {
                queue.addLast(superclass);
            }
            Class<?>[] interfaces = current.getInterfaces();
            for (Class<?> interfaceType : interfaces) {
                queue.addLast(interfaceType);
            }
        }
        return null;
    }

    private void registerDefaults() {
        DefaultOutputFormats.registerDefaults(this);
    }

    private static Class<?> normalizeType(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("输出类型不能为空");
        }
        return type;
    }

    private static String normalizeOptionName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("输出选项名称不能为空");
        }
        return name.trim();
    }

    private static final class RegisteredFormatter {
        private final Class<?> registeredType;
        private final OutputFormatter formatter;

        private RegisteredFormatter(Class<?> registeredType, OutputFormatter formatter) {
            this.registeredType = registeredType;
            this.formatter = formatter;
        }
    }
}
