package com.example.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * 非递归版本实现。
 *
 * <pre>
 * 非递归版：依赖 values 栈 + operators 栈 自己管理状态
 * </pre>
 *
 * <p>这样即使出现十万层括号，也只是栈容器里多了十万个 "(" 标记，
 * 不会因为方法递归过深而触发 {@link StackOverflowError}。
 */
public class IterativeExpressionCalculator implements ExpressionCalculator {

    private static final String[] COMPARISON_OPERATORS = {">=", "<=", "==", "!=", ">", "<"};
    private static final String AND_OPERATOR = "&&";
    private static final String OR_OPERATOR = "||";

    @Override
    public String calculation(String text, Map<String, Object> varMap) {
        BigDecimal result = evaluateArithmetic(requireText(text), varMap);
        result = result.stripTrailingZeros();
        return result.compareTo(BigDecimal.ZERO) == 0 ? "0" : result.toPlainString();
    }

    @Override
    public boolean compareCalculation(String text, Map<String, Object> varMap) {
        return evaluateBoolean(requireText(text), varMap);
    }

    private static String requireText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        return text;
    }

    /**
     * 非递归布尔表达式求值。
     *
     * <p>这里继续沿用“显式栈”思路：
     * <pre>
     * booleanValues    : 保存比较结果 true / false
     * booleanOperators : 保存 && / || / (
     * </pre>
     *
     * <p>一个布尔“原子”不是单个数字，而是一个完整的比较表达式，例如：
     * <pre>
     * a + b > c
     * price / discount == 5
     * </pre>
     */
    private boolean evaluateBoolean(String text, Map<String, Object> varMap) {
        Deque<Boolean> values = new ArrayDeque<>();
        Deque<BooleanOperator> operators = new ArrayDeque<>();
        int position = 0;
        boolean expectOperand = true;

        while (position < text.length()) {
            position = skipWhitespace(text, position);
            if (position >= text.length()) {
                break;
            }

            if (expectOperand) {
                if (text.charAt(position) == '(' && isBooleanGroupStart(text, position)) {
                    operators.push(BooleanOperator.LEFT_PAREN);
                    position++;
                    continue;
                }

                int boundary = findNextBooleanBoundary(text, position);
                String comparisonText = text.substring(position, boundary).trim();
                if (comparisonText.isEmpty()) {
                    throw new IllegalArgumentException("比较表达式格式错误");
                }

                values.push(evaluateComparison(comparisonText, varMap));
                position = boundary;
                expectOperand = false;
                continue;
            }

            if (text.startsWith(AND_OPERATOR, position)) {
                reduceBooleanByPrecedence(values, operators, BooleanOperator.AND);
                operators.push(BooleanOperator.AND);
                position += AND_OPERATOR.length();
                expectOperand = true;
                continue;
            }
            if (text.startsWith(OR_OPERATOR, position)) {
                reduceBooleanByPrecedence(values, operators, BooleanOperator.OR);
                operators.push(BooleanOperator.OR);
                position += OR_OPERATOR.length();
                expectOperand = true;
                continue;
            }
            if (text.charAt(position) == ')') {
                reduceUntilBooleanLeftParen(values, operators);
                position++;
                expectOperand = false;
                continue;
            }
            throw new IllegalArgumentException("非法字符: " + text.charAt(position));
        }

        if (expectOperand) {
            throw new IllegalArgumentException("比较表达式格式错误");
        }

        while (!operators.isEmpty()) {
            BooleanOperator operator = operators.pop();
            if (operator == BooleanOperator.LEFT_PAREN) {
                throw new IllegalArgumentException("括号不匹配");
            }
            applyBooleanOperator(values, operator);
        }

        if (values.size() != 1) {
            throw new IllegalArgumentException("比较表达式格式错误");
        }
        return values.pop();
    }

    private boolean evaluateComparison(String text, Map<String, Object> varMap) {
        ComparisonParts comparisonParts = splitComparison(text);
        BigDecimal left = evaluateArithmetic(comparisonParts.leftExpression(), varMap);
        BigDecimal right = evaluateArithmetic(comparisonParts.rightExpression(), varMap);
        int compareResult = left.compareTo(right);

        return switch (comparisonParts.operator()) {
            case ">" -> compareResult > 0;
            case "<" -> compareResult < 0;
            case ">=" -> compareResult >= 0;
            case "<=" -> compareResult <= 0;
            case "==" -> compareResult == 0;
            case "!=" -> compareResult != 0;
            default -> throw new IllegalArgumentException("不支持的比较运算符: " + comparisonParts.operator());
        };
    }

    /**
     * 先在最外层切出比较表达式左右两边。
     *
     * <pre>
     * a + b * (c + 2) > 10
     * └──── left ─────┘   └right┘
     * </pre>
     */
    private ComparisonParts splitComparison(String text) {
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
        throw new IllegalArgumentException("比较表达式缺少比较运算符");
    }

    /**
     * 判断当前位置的 '(' 是不是“布尔分组括号”。
     *
     * <pre>
     * (a > b || c < d) && e == f   -> 是布尔分组
     * (a + b) > c                  -> 不是，是比较表达式左侧的算术括号
     * </pre>
     */
    private boolean isBooleanGroupStart(String text, int start) {
        int matchIndex = findMatchingParen(text, start);
        int next = skipWhitespace(text, matchIndex + 1);
        return next >= text.length()
                || text.startsWith(AND_OPERATOR, next)
                || text.startsWith(OR_OPERATOR, next)
                || text.charAt(next) == ')';
    }

    private int findMatchingParen(String text, int start) {
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
     * 寻找下一个布尔层面的边界。
     *
     * <p>边界有三种：
     * <pre>
     * 1. &&
     * 2. ||
     * 3. 当前布尔分组对应的右括号 )
     * </pre>
     *
     * <p>这里只在布尔层级 level == 0 时才认为遇到了边界，
     * 因此算术括号不会误切表达式。
     */
    private int findNextBooleanBoundary(String text, int start) {
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

            if (level == 0 && (text.startsWith(AND_OPERATOR, index) || text.startsWith(OR_OPERATOR, index))) {
                return index;
            }
        }

        if (level != 0) {
            throw new IllegalArgumentException("括号不匹配");
        }
        return text.length();
    }

    /**
     * 非递归算术求值。
     *
     * <p>使用两个显式栈：
     * <pre>
     * values    : 操作数栈，保存数字
     * operators : 运算符栈，保存 + - * / unary +/- 和 (
     * </pre>
     */
    private BigDecimal evaluateArithmetic(String text, Map<String, Object> varMap) {
        Deque<BigDecimal> values = new ArrayDeque<>();
        Deque<Operator> operators = new ArrayDeque<>();
        int position = 0;
        boolean expectOperand = true;

        while (position < text.length()) {
            char current = text.charAt(position);
            if (Character.isWhitespace(current)) {
                position++;
                continue;
            }

            if (expectOperand) {
                if (current == '(') {
                    operators.push(Operator.LEFT_PAREN);
                    position++;
                    continue;
                }
                if (current == '+') {
                    operators.push(Operator.UNARY_PLUS);
                    position++;
                    continue;
                }
                if (current == '-') {
                    operators.push(Operator.UNARY_MINUS);
                    position++;
                    continue;
                }
                if (Character.isDigit(current) || current == '.') {
                    position = parseNumber(text, position, values);
                    applyPendingUnary(values, operators);
                    expectOperand = false;
                    continue;
                }
                if (Character.isLetter(current) || current == '_') {
                    position = parseVariable(text, position, varMap, values);
                    applyPendingUnary(values, operators);
                    expectOperand = false;
                    continue;
                }
                throw new IllegalArgumentException("非法字符: " + current);
            }

            if (current == ')') {
                reduceUntilLeftParen(values, operators);
                position++;
                applyPendingUnary(values, operators);
                expectOperand = false;
                continue;
            }

            Operator binaryOperator = Operator.fromBinarySymbol(current);
            if (binaryOperator == null) {
                throw new IllegalArgumentException("非法字符: " + current);
            }

            reduceByPrecedence(values, operators, binaryOperator);
            operators.push(binaryOperator);
            position++;
            expectOperand = true;
        }

        if (expectOperand) {
            throw new IllegalArgumentException("表达式格式错误");
        }

        while (!operators.isEmpty()) {
            Operator operator = operators.pop();
            if (operator == Operator.LEFT_PAREN) {
                throw new IllegalArgumentException("括号不匹配");
            }
            applyOperator(values, operator);
        }

        if (values.size() != 1) {
            throw new IllegalArgumentException("表达式格式错误");
        }
        return values.pop();
    }

    private void reduceByPrecedence(Deque<BigDecimal> values, Deque<Operator> operators, Operator incomingOperator) {
        while (!operators.isEmpty()) {
            Operator top = operators.peek();
            if (top == Operator.LEFT_PAREN || top.precedence < incomingOperator.precedence) {
                return;
            }
            applyOperator(values, operators.pop());
        }
    }

    private void reduceUntilLeftParen(Deque<BigDecimal> values, Deque<Operator> operators) {
        while (!operators.isEmpty() && operators.peek() != Operator.LEFT_PAREN) {
            applyOperator(values, operators.pop());
        }
        if (operators.isEmpty()) {
            throw new IllegalArgumentException("括号不匹配");
        }
        operators.pop();
    }

    private void applyPendingUnary(Deque<BigDecimal> values, Deque<Operator> operators) {
        while (!operators.isEmpty() && operators.peek().unary) {
            applyOperator(values, operators.pop());
        }
    }

    private void applyOperator(Deque<BigDecimal> values, Operator operator) {
        if (operator.unary) {
            BigDecimal value = requireOperand(values);
            values.push(operator == Operator.UNARY_MINUS ? value.negate() : value);
            return;
        }

        BigDecimal right = requireOperand(values);
        BigDecimal left = requireOperand(values);
        switch (operator) {
            case ADD -> values.push(left.add(right));
            case SUBTRACT -> values.push(left.subtract(right));
            case MULTIPLY -> values.push(left.multiply(right));
            case DIVIDE -> {
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    throw new ArithmeticException("除数不能为0");
                }
                values.push(left.divide(right, MathContext.DECIMAL128));
            }
            default -> throw new IllegalArgumentException("不支持的运算符: " + operator.symbol);
        }
    }

    private BigDecimal requireOperand(Deque<BigDecimal> values) {
        BigDecimal value = values.pollFirst();
        if (value == null) {
            throw new IllegalArgumentException("表达式格式错误");
        }
        return value;
    }

    private void reduceBooleanByPrecedence(
            Deque<Boolean> values,
            Deque<BooleanOperator> operators,
            BooleanOperator incomingOperator) {
        while (!operators.isEmpty()) {
            BooleanOperator top = operators.peek();
            if (top == BooleanOperator.LEFT_PAREN || top.precedence < incomingOperator.precedence) {
                return;
            }
            applyBooleanOperator(values, operators.pop());
        }
    }

    private void reduceUntilBooleanLeftParen(Deque<Boolean> values, Deque<BooleanOperator> operators) {
        while (!operators.isEmpty() && operators.peek() != BooleanOperator.LEFT_PAREN) {
            applyBooleanOperator(values, operators.pop());
        }
        if (operators.isEmpty()) {
            throw new IllegalArgumentException("括号不匹配");
        }
        operators.pop();
    }

    private void applyBooleanOperator(Deque<Boolean> values, BooleanOperator operator) {
        boolean right = requireBooleanOperand(values);
        boolean left = requireBooleanOperand(values);
        switch (operator) {
            case AND -> values.push(left && right);
            case OR -> values.push(left || right);
            default -> throw new IllegalArgumentException("不支持的布尔运算符: " + operator.symbol);
        }
    }

    private boolean requireBooleanOperand(Deque<Boolean> values) {
        Boolean value = values.pollFirst();
        if (value == null) {
            throw new IllegalArgumentException("比较表达式格式错误");
        }
        return value;
    }

    private int parseNumber(String text, int start, Deque<BigDecimal> values) {
        int position = start;
        boolean hasDot = false;
        while (position < text.length()) {
            char current = text.charAt(position);
            if (Character.isDigit(current)) {
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

        String number = text.substring(start, position);
        if (".".equals(number)) {
            throw new IllegalArgumentException("数字格式错误");
        }
        values.push(new BigDecimal(number));
        return position;
    }

    private int parseVariable(String text, int start, Map<String, Object> varMap, Deque<BigDecimal> values) {
        int position = start;
        while (position < text.length()) {
            char current = text.charAt(position);
            if (Character.isLetterOrDigit(current) || current == '_') {
                position++;
            } else {
                break;
            }
        }

        String variableName = text.substring(start, position);
        if (varMap == null || !varMap.containsKey(variableName)) {
            throw new IllegalArgumentException("变量不存在: " + variableName);
        }

        Object value = varMap.get(variableName);
        if (value == null) {
            throw new IllegalArgumentException("变量值不能为空: " + variableName);
        }
        if (value instanceof BigDecimal bigDecimal) {
            values.push(bigDecimal);
            return position;
        }
        if (value instanceof Number || value instanceof String) {
            values.push(new BigDecimal(value.toString()));
            return position;
        }
        throw new IllegalArgumentException("变量类型不支持: " + variableName);
    }

    private int skipWhitespace(String text, int position) {
        int current = position;
        while (current < text.length() && Character.isWhitespace(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private record ComparisonParts(String leftExpression, String operator, String rightExpression) {
    }

    private enum Operator {
        ADD("+", 1, false),
        SUBTRACT("-", 1, false),
        MULTIPLY("*", 2, false),
        DIVIDE("/", 2, false),
        UNARY_PLUS("u+", 3, true),
        UNARY_MINUS("u-", 3, true),
        LEFT_PAREN("(", -1, false);

        private final String symbol;
        private final int precedence;
        private final boolean unary;

        Operator(String symbol, int precedence, boolean unary) {
            this.symbol = symbol;
            this.precedence = precedence;
            this.unary = unary;
        }

        private static Operator fromBinarySymbol(char symbol) {
            return switch (symbol) {
                case '+' -> ADD;
                case '-' -> SUBTRACT;
                case '*' -> MULTIPLY;
                case '/' -> DIVIDE;
                default -> null;
            };
        }
    }

    private enum BooleanOperator {
        OR("||", 1),
        AND("&&", 2),
        LEFT_PAREN("(", -1);

        private final String symbol;
        private final int precedence;

        BooleanOperator(String symbol, int precedence) {
            this.symbol = symbol;
            this.precedence = precedence;
        }
    }
}
