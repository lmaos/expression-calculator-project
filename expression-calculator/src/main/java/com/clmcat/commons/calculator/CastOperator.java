package com.clmcat.commons.calculator;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态类型转换运算符。
 */
final class CastOperator {

    private static final ConcurrentHashMap<String, Operator> CACHE = new ConcurrentHashMap<>();

    private CastOperator() {
    }

    static Operator create(String typeName) {
        return CACHE.computeIfAbsent(typeName, CastOperator::newOperator);
    }

    private static Operator newOperator(String typeName) {
        return Operator.unary("(" + typeName + ")",
                OperatorRegistry.unaryPrecedence(),
                Associativity.RIGHT,
                value -> RuntimeValue.computed(
                        ConverterRegistry.getInstance().convert(typeName, value.isMissingVariable() ? null : value.raw())));
    }
}
