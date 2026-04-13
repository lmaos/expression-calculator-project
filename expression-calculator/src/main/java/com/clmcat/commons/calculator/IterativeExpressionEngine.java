package com.clmcat.commons.calculator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * 显式栈版本的值表达式求值器。
 *
 * <p>它负责处理：
 * <pre>
 * 1. 数字 / null / true / false / 变量
 * 2. 一元运算、二元运算、比较运算
 * 3. 分组括号
 * 4. 方法调用与链式方法调用
 * </pre>
 *
 * <p>核心思路仍然是“双栈求值”，但运算符本身不再写死在枚举里，
 * 而是统一从 {@link OperatorRegistry} 查询。
 */
final class IterativeExpressionEngine {

    // 左括号哨兵，标记当前括号层的起点。
    private static final Operator LEFT_PAREN = Operator.unary("(", Integer.MIN_VALUE, Associativity.LEFT, operand -> operand);

    private IterativeExpressionEngine() {
    }

    static String evaluateForCalculation(String text, Map<String, Object> variables) {
        return ExpressionRuntimeSupport.formatForCalculation(evaluateValue(text, variables));
    }

    static RuntimeValue evaluateValue(String text, Map<String, Object> variables) {
        return new Evaluator(ExpressionRuntimeSupport.requireText(text), variables).evaluate();
    }

    private static final class Evaluator {
        private final String text;
        private final Map<String, Object> variables;
        private final OperatorRegistry operatorRegistry = OperatorRegistry.getInstance();
        private final Deque<RuntimeValue> values = new ArrayDeque<>();
        private final Deque<Operator> operators = new ArrayDeque<>();
        private int position;

        private Evaluator(String text, Map<String, Object> variables) {
            this.text = text;
            this.variables = variables;
        }

        /**
         * 主循环按“当前是否期待一个操作数”在两个状态之间切换：
         * <pre>
         * expectOperand = true   -> 只能读值、左括号、一元运算符
         * expectOperand = false  -> 只能读方法调用、二元运算符、右括号
         * </pre>
         */
        private RuntimeValue evaluate() {
            boolean expectOperand = true;
            while (true) {
                skipWhitespace();
                if (isEnd()) {
                    break;
                }

                if (expectOperand) {
                    Operator unaryOperator = readUnaryOperator();
                    if (unaryOperator != null) {
                        pushOperator(unaryOperator);
                        continue;
                    }
                    if (match('(')) {
                        operators.push(LEFT_PAREN);
                        continue;
                    }

                    values.push(parsePrimaryValue());
                    expectOperand = false;
                    continue;
                }

                if (peek('.')) {
                    applyMethodCall();
                    continue;
                }
                if (match(')')) {
                    reduceUntilLeftParen();
                    continue;
                }

                Operator operator = readBinaryOperator();
                if (operator != null) {
                    pushOperator(operator);
                    expectOperand = true;
                    continue;
                }
                throw new IllegalArgumentException("非法字符: " + currentChar());
            }

            if (expectOperand) {
                throw new IllegalArgumentException("表达式格式错误");
            }
            reduceRemainingOperators();
            if (values.size() != 1) {
                throw new IllegalArgumentException("表达式格式错误");
            }
            return values.pop();
        }

        private RuntimeValue parsePrimaryValue() {
            if (isEnd()) {
                throw new IllegalArgumentException("表达式格式错误");
            }
            char current = currentChar();
            if (current == '"') {
                return RuntimeValue.literal(parseStringLiteral());
            }
            if (current == '\'') {
                return RuntimeValue.literal(parseCharacterLiteral());
            }
            if (Character.isDigit(current) || current == '.') {
                return RuntimeValue.literal(parseNumberLiteral());
            }
            if (ExpressionTextSupport.isIdentifierStart(current)) {
                String identifier = parseIdentifier();
                return switch (identifier) {
                    case "true" -> RuntimeValue.literal(Boolean.TRUE);
                    case "false" -> RuntimeValue.literal(Boolean.FALSE);
                    case "null" -> RuntimeValue.literal(null);
                    default -> parseVariable(identifier);
                };
            }
            throw new IllegalArgumentException("非法字符: " + current);
        }

        private RuntimeValue parseVariable(String identifier) {
            if (variables == null || !variables.containsKey(identifier)) {
                return RuntimeValue.missingVariable(identifier);
            }
            return RuntimeValue.variable(variables.get(identifier));
        }

        private void applyMethodCall() {
            if (values.isEmpty()) {
                throw new IllegalArgumentException("表达式格式错误");
            }
            match('.');
            String methodName = parseIdentifier();
            skipWhitespace();
            if (!match('(')) {
                throw new IllegalArgumentException("非法字符: .");
            }

            int argumentStart = position;
            int argumentEnd = findClosingParenthesis(argumentStart - 1);
            List<RuntimeValue> arguments = parseArguments(argumentStart, argumentEnd);
            position = argumentEnd + 1;

            RuntimeValue receiverValue = values.pop();
            values.push(ExpressionRuntimeSupport.invokeMethod(receiverValue, methodName, arguments));
        }

        private List<RuntimeValue> parseArguments(int startInclusive, int endExclusive) {
            String argumentText = text.substring(startInclusive, endExclusive).trim();
            if (argumentText.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            List<String> parts = ExpressionTextSupport.splitArguments(argumentText);
            List<RuntimeValue> arguments = new ArrayList<>(parts.size());
            for (String part : parts) {
                arguments.add(IterativeExpressionEngine.evaluateValue(part, variables));
            }
            return arguments;
        }

        private int findClosingParenthesis(int openIndex) {
            return ExpressionTextSupport.findMatchingParenthesis(text, openIndex);
        }

        private Object parseNumberLiteral() {
            ExpressionTextSupport.ParsedToken<Object> token = ExpressionTextSupport.parseNumberLiteral(text, position);
            position = token.nextIndex();
            return token.value();
        }

        private String parseStringLiteral() {
            ExpressionTextSupport.ParsedToken<String> token = ExpressionTextSupport.parseStringLiteral(text, position);
            position = token.nextIndex();
            return token.value();
        }

        private Character parseCharacterLiteral() {
            ExpressionTextSupport.ParsedToken<Character> token = ExpressionTextSupport.parseCharacterLiteral(text, position);
            position = token.nextIndex();
            return token.value();
        }

        private String parseIdentifier() {
            skipWhitespace();
            ExpressionTextSupport.ParsedToken<String> token = ExpressionTextSupport.parseIdentifier(text, position);
            position = token.nextIndex();
            return token.value();
        }

        private void pushOperator(Operator incoming) {
            // 先把栈顶更高优先级的运算符规约掉，再压入当前运算符。
            while (!operators.isEmpty() && operators.peek() != LEFT_PAREN
                    && shouldReduceBeforePush(operators.peek(), incoming)) {
                applyOperator(operators.pop());
            }
            operators.push(incoming);
        }

        private boolean shouldReduceBeforePush(Operator stackTop, Operator incoming) {
            if (stackTop.precedence() > incoming.precedence()) {
                return true;
            }
            return stackTop.precedence() == incoming.precedence()
                    && incoming.associativity() == Associativity.LEFT;
        }

        private void reduceUntilLeftParen() {
            while (!operators.isEmpty() && operators.peek() != LEFT_PAREN) {
                applyOperator(operators.pop());
            }
            if (operators.isEmpty() || operators.pop() != LEFT_PAREN) {
                throw new IllegalArgumentException("括号不匹配");
            }
        }

        private void reduceRemainingOperators() {
            while (!operators.isEmpty()) {
                Operator operator = operators.pop();
                if (operator == LEFT_PAREN) {
                    throw new IllegalArgumentException("括号不匹配");
                }
                applyOperator(operator);
            }
        }

        private void applyOperator(Operator operator) {
            // 一元运算和二元运算共用同一条规约路径，只在这里分发参数个数。
            if (operator.isUnary()) {
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("表达式格式错误");
                }
                RuntimeValue operand = values.pop();
                values.push(operator.apply(operand));
                return;
            }

            if (values.size() < 2) {
                throw new IllegalArgumentException("表达式格式错误");
            }
            RuntimeValue right = values.pop();
            RuntimeValue left = values.pop();
            values.push(operator.apply(left, right));
        }

        private Operator readUnaryOperator() {
            Operator operator = operatorRegistry.matchUnaryOperator(text, position);
            if (operator != null) {
                position += operator.symbol().length();
            }
            return operator;
        }

        private Operator readBinaryOperator() {
            Operator operator = operatorRegistry.matchBinaryOperator(text, position);
            if (operator != null) {
                position += operator.symbol().length();
            }
            return operator;
        }

        private void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(currentChar())) {
                position++;
            }
        }

        private boolean match(char expected) {
            if (!isEnd() && currentChar() == expected) {
                position++;
                return true;
            }
            return false;
        }

        private boolean peek(char expected) {
            return !isEnd() && currentChar() == expected;
        }

        private boolean isEnd() {
            return position >= text.length();
        }

        private char currentChar() {
            return text.charAt(position);
        }
    }
}
