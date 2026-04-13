package com.clmcat.commons.calculator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class OperatorRegistry {

    private static final int PRECEDENCE_EQUALITY = 1;
    private static final int PRECEDENCE_BITWISE_OR = 2;
    private static final int PRECEDENCE_BITWISE_XOR = 3;
    private static final int PRECEDENCE_BITWISE_AND = 4;
    private static final int PRECEDENCE_RELATIONAL = 5;
    private static final int PRECEDENCE_SHIFT = 6;
    private static final int PRECEDENCE_ADDITIVE = 7;
    private static final int PRECEDENCE_MULTIPLICATIVE = 8;
    private static final int PRECEDENCE_POWER = 9;
    private static final int PRECEDENCE_UNARY = 10;

    private static final Comparator<Operator> OPERATOR_ORDER = Comparator
            .comparingInt((Operator operator) -> operator.symbol().length())
            .reversed()
            .thenComparing(Operator::symbol);

    private static final OperatorRegistry INSTANCE = new OperatorRegistry();

    private final ConcurrentHashMap<String, Operator> unaryOperators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Operator> binaryOperators = new ConcurrentHashMap<>();

    private volatile List<Operator> unaryOperatorSnapshot = Collections.emptyList();
    private volatile List<Operator> binaryOperatorSnapshot = Collections.emptyList();

    private OperatorRegistry() {
        registerDefaults();
    }

    public static OperatorRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized Operator registerBinary(String symbol, int precedence, Associativity associativity,
            BiFunction<RuntimeValue, RuntimeValue, RuntimeValue> function) {
        validateBinarySymbol(symbol);
        Operator operator = Operator.binary(symbol, precedence, associativity, function);
        binaryOperators.put(symbol, operator);
        refreshBinarySnapshot();
        return operator;
    }

    public synchronized Operator registerUnary(String symbol, int precedence, Associativity associativity,
            Function<RuntimeValue, RuntimeValue> function) {
        Operator operator = Operator.unary(symbol, precedence, associativity, function);
        unaryOperators.put(symbol, operator);
        refreshUnarySnapshot();
        return operator;
    }

    public synchronized Operator unregisterBinary(String symbol) {
        Operator removed = binaryOperators.remove(symbol);
        refreshBinarySnapshot();
        return removed;
    }

    public synchronized Operator unregisterUnary(String symbol) {
        Operator removed = unaryOperators.remove(symbol);
        refreshUnarySnapshot();
        return removed;
    }

    public List<Operator> getAllBinaryOperators() {
        return binaryOperatorSnapshot;
    }

    public List<Operator> getAllUnaryOperators() {
        return unaryOperatorSnapshot;
    }

    Operator matchBinaryOperator(String text, int start) {
        return match(binaryOperatorSnapshot, text, start);
    }

    Operator matchUnaryOperator(String text, int start) {
        return match(unaryOperatorSnapshot, text, start);
    }

    synchronized void resetToDefaults() {
        unaryOperators.clear();
        binaryOperators.clear();
        registerDefaults();
    }

    private void registerDefaults() {
        registerUnaryInternal("+", PRECEDENCE_UNARY, Associativity.RIGHT, ExpressionRuntimeSupport::positive);
        registerUnaryInternal("-", PRECEDENCE_UNARY, Associativity.RIGHT, ExpressionRuntimeSupport::negate);
        registerUnaryInternal("~", PRECEDENCE_UNARY, Associativity.RIGHT, ExpressionRuntimeSupport::bitwiseNot);

        registerBinaryInternal("*", PRECEDENCE_MULTIPLICATIVE, Associativity.LEFT, ExpressionRuntimeSupport::multiply);
        registerBinaryInternal("/", PRECEDENCE_MULTIPLICATIVE, Associativity.LEFT, ExpressionRuntimeSupport::divide);
        registerBinaryInternal("%", PRECEDENCE_MULTIPLICATIVE, Associativity.LEFT, ExpressionRuntimeSupport::remainder);
        registerBinaryInternal("+", PRECEDENCE_ADDITIVE, Associativity.LEFT, ExpressionRuntimeSupport::add);
        registerBinaryInternal("-", PRECEDENCE_ADDITIVE, Associativity.LEFT, ExpressionRuntimeSupport::subtract);
        registerBinaryInternal("^", PRECEDENCE_POWER, Associativity.RIGHT, ExpressionRuntimeSupport::power);
        registerBinaryInternal("<<", PRECEDENCE_SHIFT, Associativity.LEFT, ExpressionRuntimeSupport::shiftLeft);
        registerBinaryInternal(">>", PRECEDENCE_SHIFT, Associativity.LEFT, ExpressionRuntimeSupport::shiftRight);
        registerBinaryInternal(">>>", PRECEDENCE_SHIFT, Associativity.LEFT, ExpressionRuntimeSupport::unsignedShiftRight);
        registerBinaryInternal("<<<", PRECEDENCE_SHIFT, Associativity.LEFT, ExpressionRuntimeSupport::unsignedShiftLeft);
        registerBinaryInternal("&", PRECEDENCE_BITWISE_AND, Associativity.LEFT, ExpressionRuntimeSupport::bitwiseAnd);
        registerBinaryInternal("xor", PRECEDENCE_BITWISE_XOR, Associativity.LEFT, ExpressionRuntimeSupport::bitwiseXor);
        registerBinaryInternal("|", PRECEDENCE_BITWISE_OR, Associativity.LEFT, ExpressionRuntimeSupport::bitwiseOr);

        registerBinaryInternal(">=", PRECEDENCE_RELATIONAL, Associativity.LEFT, comparison(">="));
        registerBinaryInternal("<=", PRECEDENCE_RELATIONAL, Associativity.LEFT, comparison("<="));
        registerBinaryInternal(">", PRECEDENCE_RELATIONAL, Associativity.LEFT, comparison(">"));
        registerBinaryInternal("<", PRECEDENCE_RELATIONAL, Associativity.LEFT, comparison("<"));
        registerBinaryInternal("==", PRECEDENCE_EQUALITY, Associativity.LEFT, comparison("=="));
        registerBinaryInternal("!=", PRECEDENCE_EQUALITY, Associativity.LEFT, comparison("!="));

        refreshUnarySnapshot();
        refreshBinarySnapshot();
    }

    private void registerBinaryInternal(String symbol, int precedence, Associativity associativity,
            BiFunction<RuntimeValue, RuntimeValue, RuntimeValue> function) {
        binaryOperators.put(symbol, Operator.binary(symbol, precedence, associativity, function));
    }

    private void registerUnaryInternal(String symbol, int precedence, Associativity associativity,
            Function<RuntimeValue, RuntimeValue> function) {
        unaryOperators.put(symbol, Operator.unary(symbol, precedence, associativity, function));
    }

    private static BiFunction<RuntimeValue, RuntimeValue, RuntimeValue> comparison(String symbol) {
        return (left, right) -> RuntimeValue.computed(ExpressionRuntimeSupport.compare(left, symbol, right));
    }

    private void refreshUnarySnapshot() {
        unaryOperatorSnapshot = snapshot(unaryOperators.values());
    }

    private void refreshBinarySnapshot() {
        binaryOperatorSnapshot = snapshot(binaryOperators.values());
    }

    private static List<Operator> snapshot(Collection<Operator> operators) {
        List<Operator> snapshot = new ArrayList<>(operators);
        snapshot.sort(OPERATOR_ORDER);
        return Collections.unmodifiableList(snapshot);
    }

    private static Operator match(List<Operator> operators, String text, int start) {
        for (Operator operator : operators) {
            if (operator.matches(text, start)) {
                return operator;
            }
        }
        return null;
    }

    private static void validateBinarySymbol(String symbol) {
        if ("&&".equals(symbol) || "||".equals(symbol)) {
            throw new IllegalArgumentException("逻辑短路运算符不能通过普通运算符注册: " + symbol);
        }
    }
}
