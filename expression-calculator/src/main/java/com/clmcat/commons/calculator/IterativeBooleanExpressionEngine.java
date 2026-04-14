package com.clmcat.commons.calculator;

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
         * 一个布尔原子交给值表达式引擎统一求值，再套用现有真值规则。
         *
         * <p>这样比较运算符、扩展运算符和方法调用只保留一套解析/求值路径，
         * 不需要在布尔引擎里再维护一份顶层比较拆分逻辑。
         */
        private boolean evaluateBooleanAtom(String atomText) {
            return ExpressionRuntimeSupport.toStandaloneBoolean(IterativeExpressionEngine.evaluateValue(atomText, variables));
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
            int matchingParen = ExpressionTextSupport.findMatchingParenthesis(text, start);
            int next = skipWhitespace(matchingParen + 1);
            return next >= text.length()
                    || text.startsWith(AND_OPERATOR, next)
                    || text.startsWith(OR_OPERATOR, next)
                    || text.charAt(next) == ')';
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
            int parenthesisLevel = 0;
            int bracketLevel = 0;
            for (int index = start; index < text.length(); index++) {
                char current = text.charAt(index);
                if (ExpressionTextSupport.isQuote(current)) {
                    index = ExpressionTextSupport.skipQuotedLiteral(text, index);
                    continue;
                }
                if (current == '(') {
                    parenthesisLevel++;
                } else if (current == ')') {
                    if (parenthesisLevel == 0) {
                        return index;
                    }
                    parenthesisLevel--;
                    if (parenthesisLevel < 0) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                } else if (current == '[') {
                    bracketLevel++;
                } else if (current == ']') {
                    bracketLevel--;
                    if (bracketLevel < 0) {
                        throw new IllegalArgumentException("方括号不匹配");
                    }
                }

                if (parenthesisLevel == 0 && bracketLevel == 0
                        && (text.startsWith(AND_OPERATOR, index) || text.startsWith(OR_OPERATOR, index))) {
                    return index;
                }
            }
            if (parenthesisLevel != 0) {
                throw new IllegalArgumentException("括号不匹配");
            }
            if (bracketLevel != 0) {
                throw new IllegalArgumentException("方括号不匹配");
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
            int parenthesisLevel = 0;
            int bracketLevel = 0;
            for (int index = start; index < text.length(); index++) {
                char current = text.charAt(index);
                if (ExpressionTextSupport.isQuote(current)) {
                    index = ExpressionTextSupport.skipQuotedLiteral(text, index);
                    continue;
                }
                if (current == '(') {
                    parenthesisLevel++;
                } else if (current == ')') {
                    if (parenthesisLevel == 0) {
                        return index;
                    }
                    parenthesisLevel--;
                    if (parenthesisLevel < 0) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                } else if (current == '[') {
                    bracketLevel++;
                } else if (current == ']') {
                    bracketLevel--;
                    if (bracketLevel < 0) {
                        throw new IllegalArgumentException("方括号不匹配");
                    }
                }
            }
            if (parenthesisLevel != 0) {
                throw new IllegalArgumentException("括号不匹配");
            }
            if (bracketLevel != 0) {
                throw new IllegalArgumentException("方括号不匹配");
            }
            return text.length();
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
        // 每个 Frame 都表示一个布尔分组的局部状态机。
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

    // 当前 Frame 期望完成的运算符状态。
    private enum PendingOperator {
        NONE,
        AND,
        OR
    }
}
