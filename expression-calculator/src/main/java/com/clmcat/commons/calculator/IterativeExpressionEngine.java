package com.clmcat.commons.calculator;

import com.clmcat.commons.calculator.ExpressionRuntimeSupport.RuntimeValue;
import java.math.BigDecimal;
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
 * 2. 一元 +/-、四则运算、比较运算
 * 3. 分组括号
 * 4. 方法调用与链式方法调用
 * </pre>
 *
 * <p>核心思路是“双栈求值”：
 * <pre>
 * values    : 保存已经算出的值
 * operators : 保存还没执行的运算符
 *
 * 例如：-(a + b) * c
 *
 * 扫描到 c 之前，大致会形成：
 * values    = [a, b, (a+b), c]
 * operators = [NEG, *]
 * </pre>
 *
 * <p>这样括号层级再深，也只是两个容器里的元素更多，不会继续占用 Java 调用栈。
 */
final class IterativeExpressionEngine {

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
         * expectOperand = true   -> 只能读值、左括号、一元 +/-
         * expectOperand = false  -> 只能读方法调用、二元运算符、右括号
         * </pre>
         *
         * <p>这种状态切换可以稳定拦截很多恶意输入，例如：
         * <pre>
         * a + * b
         * a )
         * ( + )
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
                    if (match('+')) {
                        pushOperator(Operator.UNARY_PLUS);
                        continue;
                    }
                    if (match('-')) {
                        pushOperator(Operator.UNARY_MINUS);
                        continue;
                    }
                    if (match('(')) {
                        operators.push(Operator.LEFT_PAREN);
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

        /**
         * primary 只把“一个最小值单元”读出来：
         * <pre>
         * 123
         * true
         * variableName
         * </pre>
         *
         * <p>方法调用不在这里做，而是在主循环里把它当成 postfix（后缀操作）处理。
         * 这样 `(obj).trim().length()` 这类链式调用会自然落在“前一个值之上”。
         */
        private RuntimeValue parsePrimaryValue() {
            if (isEnd()) {
                throw new IllegalArgumentException("表达式格式错误");
            }
            char current = currentChar();
            if (Character.isDigit(current) || current == '.') {
                return RuntimeValue.literal(parseNumberLiteral());
            }
            if (Character.isLetter(current) || current == '_') {
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
                throw new IllegalArgumentException("变量不存在: " + identifier);
            }
            return RuntimeValue.variable(variables.get(identifier));
        }

        /**
         * 后缀方法调用流程：
         * <pre>
         * values top = receiver
         * receiver.method(arg1, arg2)
         *          |---- 解析参数 ----|
         *          |---- 反射调用 ----|
         * 结果再压回 values
         * </pre>
         */
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
            List<String> parts = splitArguments(argumentText);
            List<RuntimeValue> arguments = new ArrayList<>(parts.size());
            for (String part : parts) {
                arguments.add(IterativeExpressionEngine.evaluateValue(part, variables));
            }
            return arguments;
        }

        /**
         * 只在“当前方法调用这一层”拆分逗号：
         * <pre>
         * add(1, max(2, 3), x + y)
         *      ^ 这里可切
         *         ^^^^^^^^ 这里不能切，因为它在内部括号里
         * </pre>
         */
        private List<String> splitArguments(String argumentText) {
            List<String> arguments = new ArrayList<>();
            int level = 0;
            int start = 0;
            for (int index = 0; index < argumentText.length(); index++) {
                char current = argumentText.charAt(index);
                if (current == '(') {
                    level++;
                } else if (current == ')') {
                    level--;
                    if (level < 0) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                } else if (current == ',' && level == 0) {
                    arguments.add(argumentText.substring(start, index).trim());
                    start = index + 1;
                }
            }
            if (level != 0) {
                throw new IllegalArgumentException("括号不匹配");
            }
            arguments.add(argumentText.substring(start).trim());
            return arguments;
        }

        private int findClosingParenthesis(int openIndex) {
            int level = 0;
            for (int index = openIndex; index < text.length(); index++) {
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

        private Object parseNumberLiteral() {
            int start = position;
            boolean hasDigit = false;
            boolean hasDot = false;
            while (!isEnd()) {
                char current = currentChar();
                if (Character.isDigit(current)) {
                    hasDigit = true;
                    position++;
                } else if (current == '.') {
                    if (hasDot) {
                        throw new IllegalArgumentException("数字格式错误");
                    }
                    hasDot = true;
                    position++;
                } else {
                    break;
                }
            }
            if (!hasDigit) {
                throw new IllegalArgumentException("数字格式错误");
            }

            String numberText = text.substring(start, position);
            if (".".equals(numberText)) {
                throw new IllegalArgumentException("数字格式错误");
            }

            if (!isEnd()) {
                char suffix = currentChar();
                switch (suffix) {
                    case 'L':
                    case 'l':
                        position++;
                        return Long.valueOf(numberText);
                    case 'F':
                    case 'f':
                        position++;
                        return Float.valueOf(numberText);
                    case 'D':
                    case 'd':
                        position++;
                        return Double.valueOf(numberText);
                    case 'M':
                    case 'm':
                        position++;
                        return new BigDecimal(numberText);
                    default:
                        break;
                }
            }

            if (hasDot) {
                return Double.valueOf(numberText);
            }
            try {
                return Integer.valueOf(numberText);
            } catch (NumberFormatException exception) {
                return Long.valueOf(numberText);
            }
        }

        private String parseIdentifier() {
            skipWhitespace();
            if (isEnd()) {
                throw new IllegalArgumentException("表达式格式错误");
            }
            char current = currentChar();
            if (!Character.isLetter(current) && current != '_') {
                throw new IllegalArgumentException("非法字符: " + current);
            }
            int start = position;
            position++;
            while (!isEnd()) {
                current = currentChar();
                if (Character.isLetterOrDigit(current) || current == '_') {
                    position++;
                } else {
                    break;
                }
            }
            return text.substring(start, position);
        }

        /**
         * 统一的“运算符入栈”规则：
         * <pre>
         * 新运算符 incoming
         * 栈顶运算符 top
         *
         * 如果 top 优先级更高，或者同级但 incoming 是左结合，
         * 就先把 top 执行掉，再让 incoming 入栈。
         * </pre>
         */
        private void pushOperator(Operator incoming) {
            while (!operators.isEmpty() && operators.peek() != Operator.LEFT_PAREN
                    && shouldReduceBeforePush(operators.peek(), incoming)) {
                applyOperator(operators.pop());
            }
            operators.push(incoming);
        }

        private boolean shouldReduceBeforePush(Operator stackTop, Operator incoming) {
            if (stackTop.precedence > incoming.precedence) {
                return true;
            }
            return stackTop.precedence == incoming.precedence && !incoming.rightAssociative;
        }

        private void reduceUntilLeftParen() {
            while (!operators.isEmpty() && operators.peek() != Operator.LEFT_PAREN) {
                applyOperator(operators.pop());
            }
            if (operators.isEmpty() || operators.pop() != Operator.LEFT_PAREN) {
                throw new IllegalArgumentException("括号不匹配");
            }
        }

        private void reduceRemainingOperators() {
            while (!operators.isEmpty()) {
                Operator operator = operators.pop();
                if (operator == Operator.LEFT_PAREN) {
                    throw new IllegalArgumentException("括号不匹配");
                }
                applyOperator(operator);
            }
        }

        private void applyOperator(Operator operator) {
            if (operator.unary) {
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("表达式格式错误");
                }
                RuntimeValue operand = values.pop();
                values.push(operator == Operator.UNARY_MINUS
                        ? ExpressionRuntimeSupport.negate(operand)
                        : ExpressionRuntimeSupport.positive(operand));
                return;
            }

            if (values.size() < 2) {
                throw new IllegalArgumentException("表达式格式错误");
            }
            RuntimeValue right = values.pop();
            RuntimeValue left = values.pop();
            RuntimeValue result;
            switch (operator) {
                case MULTIPLY:
                    result = ExpressionRuntimeSupport.multiply(left, right);
                    break;
                case DIVIDE:
                    result = ExpressionRuntimeSupport.divide(left, right);
                    break;
                case ADD:
                    result = ExpressionRuntimeSupport.add(left, right);
                    break;
                case SUBTRACT:
                    result = ExpressionRuntimeSupport.subtract(left, right);
                    break;
                case GREATER:
                    result = RuntimeValue.computed(ExpressionRuntimeSupport.compare(left, ">", right));
                    break;
                case LESS:
                    result = RuntimeValue.computed(ExpressionRuntimeSupport.compare(left, "<", right));
                    break;
                case GREATER_OR_EQUAL:
                    result = RuntimeValue.computed(ExpressionRuntimeSupport.compare(left, ">=", right));
                    break;
                case LESS_OR_EQUAL:
                    result = RuntimeValue.computed(ExpressionRuntimeSupport.compare(left, "<=", right));
                    break;
                case EQUAL:
                    result = RuntimeValue.computed(ExpressionRuntimeSupport.compare(left, "==", right));
                    break;
                case NOT_EQUAL:
                    result = RuntimeValue.computed(ExpressionRuntimeSupport.compare(left, "!=", right));
                    break;
                default:
                    throw new IllegalArgumentException("表达式格式错误");
            }
            values.push(result);
        }

        private Operator readBinaryOperator() {
            for (Operator operator : Operator.BINARY_OPERATORS) {
                if (match(operator.symbol)) {
                    return operator;
                }
            }
            return null;
        }

        private void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(currentChar())) {
                position++;
            }
        }

        private boolean match(String expected) {
            if (text.startsWith(expected, position)) {
                position += expected.length();
                return true;
            }
            return false;
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

    /**
     * 优先级数字越大，绑定能力越强。
     *
     * <pre>
     * 5  unary +/-          右结合
     * 4  * /
     * 3  + -
     * 2  > < >= <= == !=
     * 1  (保留给布尔层，布尔层在 IterativeExpressionCalculator 里处理)
     * </pre>
     */
    private enum Operator {
        LEFT_PAREN("(", -1, false, false),
        UNARY_PLUS("u+", 5, true, true),
        UNARY_MINUS("u-", 5, true, true),
        MULTIPLY("*", 4, false, false),
        DIVIDE("/", 4, false, false),
        ADD("+", 3, false, false),
        SUBTRACT("-", 3, false, false),
        GREATER_OR_EQUAL(">=", 2, false, false),
        LESS_OR_EQUAL("<=", 2, false, false),
        EQUAL("==", 2, false, false),
        NOT_EQUAL("!=", 2, false, false),
        GREATER(">", 2, false, false),
        LESS("<", 2, false, false);

        private static final Operator[] BINARY_OPERATORS = {
                GREATER_OR_EQUAL, LESS_OR_EQUAL, EQUAL, NOT_EQUAL, GREATER, LESS, ADD, SUBTRACT, MULTIPLY, DIVIDE
        };

        private final String symbol;
        private final int precedence;
        private final boolean unary;
        private final boolean rightAssociative;

        Operator(String symbol, int precedence, boolean unary, boolean rightAssociative) {
            this.symbol = symbol;
            this.precedence = precedence;
            this.unary = unary;
            this.rightAssociative = rightAssociative;
        }
    }
}
