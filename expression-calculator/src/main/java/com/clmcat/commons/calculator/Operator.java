package com.clmcat.commons.calculator;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class Operator {

    private final String symbol;
    private final int precedence;
    private final boolean unary;
    private final Associativity associativity;
    private final BiFunction<RuntimeValue, RuntimeValue, RuntimeValue> binaryFunction;
    private final Function<RuntimeValue, RuntimeValue> unaryFunction;

    private Operator(String symbol, int precedence, boolean unary, Associativity associativity,
            BiFunction<RuntimeValue, RuntimeValue, RuntimeValue> binaryFunction,
            Function<RuntimeValue, RuntimeValue> unaryFunction) {
        this.symbol = validateSymbol(symbol);
        this.precedence = precedence;
        this.unary = unary;
        this.associativity = Objects.requireNonNull(associativity, "associativity");
        this.binaryFunction = binaryFunction;
        this.unaryFunction = unaryFunction;
    }

    public static Operator binary(String symbol, int precedence, Associativity associativity,
            BiFunction<RuntimeValue, RuntimeValue, RuntimeValue> function) {
        return new Operator(symbol, precedence, false, associativity, Objects.requireNonNull(function, "function"), null);
    }

    public static Operator unary(String symbol, int precedence, Associativity associativity,
            Function<RuntimeValue, RuntimeValue> function) {
        return new Operator(symbol, precedence, true, associativity, null, Objects.requireNonNull(function, "function"));
    }

    public String symbol() {
        return symbol;
    }

    public int precedence() {
        return precedence;
    }

    public boolean isUnary() {
        return unary;
    }

    public Associativity associativity() {
        return associativity;
    }

    public boolean isRightAssociative() {
        return associativity == Associativity.RIGHT;
    }

    public RuntimeValue apply(RuntimeValue left, RuntimeValue right) {
        if (unary) {
            throw new IllegalStateException("一元运算符不支持二元调用: " + symbol);
        }
        return Objects.requireNonNull(binaryFunction.apply(left, right), "operator result");
    }

    public RuntimeValue apply(RuntimeValue operand) {
        if (!unary) {
            throw new IllegalStateException("二元运算符不支持一元调用: " + symbol);
        }
        return Objects.requireNonNull(unaryFunction.apply(operand), "operator result");
    }

    boolean matches(String text, int start) {
        return start >= 0 && start <= text.length() && text.startsWith(symbol, start);
    }

    private static String validateSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("运算符符号不能为空");
        }
        return symbol;
    }
}
