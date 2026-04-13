package com.clmcat.commons.calculator;

import com.clmcat.commons.calculator.ExpressionRuntimeSupport.RuntimeValue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * 显式栈版本的布尔表达式求值器。
 *
 * <p>它专门解决下面这类深层布尔嵌套带来的递归风险：
 * <pre>
 * a == b || (c == d || (x == y && (m == n || (...))))
 * </pre>
 *
 * <p>实现上每一层布尔分组都对应一个 {@link Frame}，并放入显式栈：
 * <pre>
 * stack top -> 当前正在解析的布尔分组
 * </pre>
 *
 * <p>同时保留短路语义：
 * <pre>
 * true  || anything -> anything 不求值
 * false && anything -> anything 不求值
 * </pre>
 */
final class IterativeBooleanExpressionEngine {

    private static final String AND_OPERATOR = "&&";
    private static final String OR_OPERATOR = "||";
    private static final String[] COMPARISON_OPERATORS = {">=", "<=", "==", "!=", ">", "<"};

    private IterativeBooleanExpressionEngine() {
    }

    static boolean evaluate(String text, Map<String, Object> variables) {
        return new Evaluator(text, variables).evaluate();
    }

    private static final class Evaluator {
        private final String text;
        private final Map<String, Object> variables;
        private final Deque<Frame> frames = new ArrayDeque<>();
        private int position;

        private Evaluator(String text, Map<String, Object> variables) {
            this.text = text;
            this.variables = variables;
        }

        private boolean evaluate() {
            frames.push(new Frame());
            while (true) {
                Frame frame = frames.peek();
                skipWhitespace();

                if (frame.expectsOperand()) {
                    if (isEnd()) {
                        if (frames.size() == 1) {
                            throw new IllegalArgumentException("比较表达式格式错误");
                        }
                        throw new IllegalArgumentException("括号不匹配");
                    }

                    if (frame.shouldSkipAndOperand()) {
                        position = skipSingleOperand(position);
                        frame.completeSkippedAndOperand();
                        continue;
                    }

                    if (peek('(') && isBooleanGroupStart(position)) {
                        position++;
                        frames.push(new Frame());
                        continue;
                    }

                    int boundary = findNextBooleanBoundary(position);
                    String operandText = text.substring(position, boundary).trim();
                    if (operandText.isEmpty()) {
                        throw new IllegalArgumentException("比较表达式格式错误");
                    }
                    frame.acceptOperand(evaluateBooleanAtom(operandText));
                    position = boundary;
                    continue;
                }

                if (isEnd()) {
                    if (frames.size() != 1) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                    return frame.finish();
                }

                if (peek(')')) {
                    boolean value = frame.finish();
                    position++;
                    frames.pop();
                    if (frames.isEmpty()) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                    frames.peek().acceptOperand(value);
                    continue;
                }

                if (match(OR_OPERATOR)) {
                    if (frame.resultSoFarIsTrue()) {
                        position = skipCurrentFrameRemainder(position);
                        frame.forceResult(true);
                        continue;
                    }
                    frame.beginOrOperand();
                    continue;
                }

                if (match(AND_OPERATOR)) {
                    frame.beginAndOperand();
                    continue;
                }

                throw new IllegalArgumentException("非法字符: " + currentChar());
            }
        }

        /**
         * 一个布尔原子只允许是下面两类之一：
         * <pre>
         * 1. comparison  : a + b > c
         * 2. truthy atom : enabled / items / file.exists()
         * </pre>
         */
        private boolean evaluateBooleanAtom(String text) {
            ComparisonParts comparison = splitTopLevelComparison(text);
            if (comparison != null) {
                RuntimeValue leftValue = IterativeExpressionEngine.evaluateValue(comparison.leftExpression(), variables);
                RuntimeValue rightValue = IterativeExpressionEngine.evaluateValue(comparison.rightExpression(), variables);
                return ExpressionRuntimeSupport.compare(leftValue, comparison.operator(), rightValue);
            }
            return ExpressionRuntimeSupport.toStandaloneBoolean(IterativeExpressionEngine.evaluateValue(text, variables));
        }

        /**
         * 判断当前的 '(' 是布尔分组，还是值表达式内部的普通括号。
         *
         * <pre>
         * (a > b || c > d)   -> 布尔分组
         * (a + b) > c        -> 不是布尔分组，它只是左侧值表达式的一部分
         * </pre>
         */
        private boolean isBooleanGroupStart(int start) {
            int matchingParen = findMatchingParen(start);
            int next = skipWhitespace(matchingParen + 1);
            return next >= text.length()
                    || text.startsWith(AND_OPERATOR, next)
                    || text.startsWith(OR_OPERATOR, next)
                    || text.charAt(next) == ')';
        }

        private int findMatchingParen(int start) {
            int level = 0;
            for (int index = start; index < text.length(); index++) {
                char current = text.charAt(index);
                if (current == '(') {
                    level++;
                } else if (current == ')') {
                    level--;
                    if (level == 0) {
                        return index;
                    }
                    if (level < 0) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                }
            }
            throw new IllegalArgumentException("括号不匹配");
        }

        /**
         * 找到“当前布尔层”的下一个边界。边界只有三类：
         * <pre>
         * &&
         * ||
         * )
         * </pre>
         *
         * <p>内部值表达式的括号不会被误判为边界，因为会被 level 计数屏蔽掉。
         */
        private int findNextBooleanBoundary(int start) {
            int level = 0;
            for (int index = start; index < text.length(); index++) {
                char current = text.charAt(index);
                if (current == '(') {
                    level++;
                } else if (current == ')') {
                    if (level == 0) {
                        return index;
                    }
                    level--;
                    if (level < 0) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                }

                if (level == 0) {
                    if (text.startsWith(AND_OPERATOR, index) || text.startsWith(OR_OPERATOR, index)) {
                        return index;
                    }
                }
            }
            if (level != 0) {
                throw new IllegalArgumentException("括号不匹配");
            }
            return text.length();
        }

        private int skipSingleOperand(int start) {
            skipWhitespace();
            return findNextBooleanBoundary(start);
        }

        /**
         * OR 短路后，当前 frame 剩余内容都不必求值。
         *
         * <pre>
         * true || (deep && expensive && dangerous)
         * ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ 直接整体跳过
         * </pre>
         */
        private int skipCurrentFrameRemainder(int start) {
            int level = 0;
            for (int index = start; index < text.length(); index++) {
                char current = text.charAt(index);
                if (current == '(') {
                    level++;
                } else if (current == ')') {
                    if (level == 0) {
                        return index;
                    }
                    level--;
                    if (level < 0) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                }
            }
            if (level != 0) {
                throw new IllegalArgumentException("括号不匹配");
            }
            return text.length();
        }

        private ComparisonParts splitTopLevelComparison(String text) {
            int level = 0;
            for (int index = 0; index < text.length(); index++) {
                char current = text.charAt(index);
                if (current == '(') {
                    level++;
                } else if (current == ')') {
                    level--;
                    if (level < 0) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                }
                if (level != 0) {
                    continue;
                }
                for (String operator : COMPARISON_OPERATORS) {
                    if (!text.startsWith(operator, index)) {
                        continue;
                    }
                    String leftExpression = text.substring(0, index).trim();
                    String rightExpression = text.substring(index + operator.length()).trim();
                    if (leftExpression.isEmpty() || rightExpression.isEmpty()) {
                        throw new IllegalArgumentException("比较表达式格式错误");
                    }
                    return new ComparisonParts(leftExpression, operator, rightExpression);
                }
            }
            if (level != 0) {
                throw new IllegalArgumentException("括号不匹配");
            }
            return null;
        }

        private int skipWhitespace(int index) {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            return index;
        }

        private void skipWhitespace() {
            position = skipWhitespace(position);
        }

        private boolean match(String expected) {
            if (text.startsWith(expected, position)) {
                position += expected.length();
                return true;
            }
            return false;
        }

        private boolean peek(char expected) {
            return !isEnd() && text.charAt(position) == expected;
        }

        private boolean isEnd() {
            return position >= text.length();
        }

        private char currentChar() {
            return text.charAt(position);
        }
    }

    private static final class Frame {
        private Boolean currentAndValue;
        private Boolean accumulatedOrValue;
        private PendingOperator pendingOperator = PendingOperator.NONE;
        private Boolean forcedResult;

        private boolean expectsOperand() {
            return forcedResult == null && (currentAndValue == null || pendingOperator != PendingOperator.NONE);
        }

        private void acceptOperand(boolean value) {
            if (currentAndValue == null) {
                currentAndValue = value;
                pendingOperator = PendingOperator.NONE;
                return;
            }
            if (pendingOperator == PendingOperator.AND) {
                currentAndValue = currentAndValue && value;
                pendingOperator = PendingOperator.NONE;
                return;
            }
            throw new IllegalArgumentException("比较表达式格式错误");
        }

        private void beginAndOperand() {
            if (currentAndValue == null || pendingOperator != PendingOperator.NONE) {
                throw new IllegalArgumentException("比较表达式格式错误");
            }
            pendingOperator = PendingOperator.AND;
        }

        private void beginOrOperand() {
            if (currentAndValue == null || pendingOperator != PendingOperator.NONE) {
                throw new IllegalArgumentException("比较表达式格式错误");
            }
            accumulatedOrValue = accumulatedOrValue == null ? currentAndValue : accumulatedOrValue || currentAndValue;
            currentAndValue = null;
            pendingOperator = PendingOperator.OR;
        }

        private boolean shouldSkipAndOperand() {
            return pendingOperator == PendingOperator.AND && Boolean.FALSE.equals(currentAndValue);
        }

        private void completeSkippedAndOperand() {
            pendingOperator = PendingOperator.NONE;
        }

        private boolean resultSoFarIsTrue() {
            if (currentAndValue == null) {
                throw new IllegalArgumentException("比较表达式格式错误");
            }
            return accumulatedOrValue == null ? currentAndValue : accumulatedOrValue || currentAndValue;
        }

        private void forceResult(boolean result) {
            forcedResult = result;
            pendingOperator = PendingOperator.NONE;
        }

        private boolean finish() {
            if (forcedResult != null) {
                return forcedResult;
            }
            if (currentAndValue == null || pendingOperator == PendingOperator.AND) {
                throw new IllegalArgumentException("比较表达式格式错误");
            }
            return accumulatedOrValue == null ? currentAndValue : accumulatedOrValue || currentAndValue;
        }
    }

    private enum PendingOperator {
        NONE,
        AND,
        OR
    }

    private static final class ComparisonParts {
        private final String leftExpression;
        private final String operator;
        private final String rightExpression;

        private ComparisonParts(String leftExpression, String operator, String rightExpression) {
            this.leftExpression = leftExpression;
            this.operator = operator;
            this.rightExpression = rightExpression;
        }

        private String leftExpression() {
            return leftExpression;
        }

        private String operator() {
            return operator;
        }

        private String rightExpression() {
            return rightExpression;
        }
    }
}
