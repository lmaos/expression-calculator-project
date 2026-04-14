package com.clmcat.commons.calculator;

/**
 * 类型转换器接口，用于把表达式结果转换为目标类型。
 */
@FunctionalInterface
public interface TypeConverter {

    /**
     * 将输入值转换为目标类型。
     *
     * @param value 原始值
     * @return 转换后的值
     */
    Object convert(Object value);
}
