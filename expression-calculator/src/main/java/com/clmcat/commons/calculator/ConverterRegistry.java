package com.clmcat.commons.calculator;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 类型转换器注册中心。
 *
 * <p>内置基础类型转换器，也允许调用方按名称注册自定义转换逻辑。
 */
public final class ConverterRegistry {

    private static final ConverterRegistry INSTANCE = new ConverterRegistry();

    private final ConcurrentHashMap<String, TypeConverter> converters = new ConcurrentHashMap<>();

    private ConverterRegistry() {
        registerDefaults();
    }

    public static ConverterRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized TypeConverter register(String name, TypeConverter converter) {
        return converters.put(normalizeName(name), Objects.requireNonNull(converter, "converter"));
    }

    public synchronized TypeConverter unregister(String name) {
        return converters.remove(normalizeName(name));
    }

    public boolean contains(String name) {
        return converters.containsKey(normalizeName(name));
    }

    public Object convert(String name, Object value) {
        TypeConverter converter = converters.get(normalizeName(name));
        if (converter == null) {
            throw new IllegalArgumentException("未注册的类型转换器: " + name);
        }
        return converter.convert(value);
    }

    public synchronized void resetToDefaults() {
        converters.clear();
        registerDefaults();
    }

    private void registerDefaults() {
        DefaultConverters.registerDefaults(this);
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("转换器名称不能为空");
        }
        return name.trim();
    }
}
