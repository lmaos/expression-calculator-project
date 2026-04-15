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
 */
public final class OutputFormatRegistry {

    private static final OutputFormatRegistry INSTANCE = new OutputFormatRegistry(true);

    private final ConcurrentHashMap<Class<?>, OutputFormatter> formatters = new ConcurrentHashMap<Class<?>, OutputFormatter>();
    private final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Object>> options =
            new ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Object>>();

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
        registerDefaults();
    }

    public OutputFormatRegistry copy() {
        OutputFormatRegistry copy = new OutputFormatRegistry(false);
        copy.formatters.putAll(formatters);
        for (Map.Entry<Class<?>, ConcurrentHashMap<String, Object>> entry : options.entrySet()) {
            copy.options.put(entry.getKey(), new ConcurrentHashMap<String, Object>(entry.getValue()));
        }
        return copy;
    }

    public String formatValue(Object value) {
        if (value == null) {
            return null;
        }
        RegisteredFormatter registeredFormatter = resolveFormatter(value.getClass());
        if (registeredFormatter == null) {
            return String.valueOf(value);
        }
        Map<String, Object> typeOptions = options.get(registeredFormatter.registeredType);
        OutputFormatContext context = new OutputFormatContext(
                registeredFormatter.registeredType,
                typeOptions == null ? Collections.<String, Object>emptyMap() : new HashMap<String, Object>(typeOptions));
        return registeredFormatter.formatter.format(value, context);
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
