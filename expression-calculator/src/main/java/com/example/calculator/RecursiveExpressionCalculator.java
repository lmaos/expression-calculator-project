package com.example.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

/**
 * 递归下降版本实现。
 *
 *
 * <pre>
 * expression -> term -> factor
 * </pre>
 *
 * <p>优点是结构直观、代码容易对应语法规则；
 * 缺点是如果括号嵌套极深，会随着递归层数增加而消耗 JVM 调用栈。
 */
public class RecursiveExpressionCalculator implements ExpressionCalculator {

    /**
     * 计算纯算术表达式，返回字符串结果。
     *
     * <p>示例：
     * <pre>
     * a + b * (c + 2)
     * </pre>
     *
     * <p>这里内部会做两件事：
     * 1. 先把变量 a / b / c 从 varMap 中取出来
     * 2. 再按照运算符优先级完成计算
     */
    @Override
    public String calculation(String text, Map<String, Object> varMap) {
        BigDecimal result = new ArithmeticParser(requireText(text), varMap).parse();
        result = result.stripTrailingZeros();
        return result.compareTo(BigDecimal.ZERO) == 0 ? "0" : result.toPlainString();
    }

    /**
     * 计算比较表达式，返回 true / false。
     *
     * <p>示例：
     * <pre>
     * a + b * (c + 2) > 10
     * (a + b) == c
     * </pre>
     *
     * <p>处理思路是：
     * 1. 先在最外层找到比较运算符（不能找到括号里面去）
     * 2. 左右两边分别按算术表达式求值
     * 3. 最后再做比较
     */
    @Override
    public boolean compareCalculation(String text, Map<String, Object> varMap) {
        return new BooleanParser(requireText(text), varMap).parse();
    }

    private static String requireText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        return text;
    }

    private static final class BooleanParser {
        private static final String OR_OPERATOR = "||";
        private static final String AND_OPERATOR = "&&";

        private final String text;
        private final Map<String, Object> varMap;

        private BooleanParser(String text, Map<String, Object> varMap) {
            this.text = text;
            this.varMap = varMap;
        }

        /**
         * 布尔表达式优先级：
         *
         * <pre>
         * 1. 括号
         * 2. 比较：> < >= <= == !=
         * 3. &&
         * 4. ||
         * </pre>
         *
         * <p>因此入口先按最低优先级的 || 拆分。
         */
        private boolean parse() {
            return parseOr(text.trim());
        }

        private boolean parseOr(String expression) {
            int index = findTopLevelOperator(expression, OR_OPERATOR);
            if (index >= 0) {
                String leftExpression = expression.substring(0, index);
                String rightExpression = expression.substring(index + OR_OPERATOR.length());
                return parseAnd(leftExpression.trim()) || parseOr(rightExpression.trim());
            }
            return parseAnd(expression);
        }

        private boolean parseAnd(String expression) {
            int index = findTopLevelOperator(expression, AND_OPERATOR);
            if (index >= 0) {
                String leftExpression = expression.substring(0, index);
                String rightExpression = expression.substring(index + AND_OPERATOR.length());
                return parsePrimary(leftExpression.trim()) && parseAnd(rightExpression.trim());
            }
            return parsePrimary(expression);
        }

        /**
         * primary 表示一个最小布尔单元：
         * <pre>
         * 1. 被最外层括号包住的布尔表达式
         * 2. 一个比较表达式，例如 a + b > c
         * </pre>
         */
        private boolean parsePrimary(String expression) {
            String current = expression.trim();
            if (current.isEmpty()) {
                throw new IllegalArgumentException("比较表达式格式错误");
            }

            boolean unwrapped = false;
            while (isWrappedByOuterParentheses(current)) {
                current = current.substring(1, current.length() - 1).trim();
                unwrapped = true;
                if (current.isEmpty()) {
                    throw new IllegalArgumentException("比较表达式格式错误");
                }
            }

            if (unwrapped) {
                return parseOr(current);
            }
            return new CompareParser(current, varMap).parse();
        }

        private int findTopLevelOperator(String expression, String operator) {
            int level = 0;
            for (int index = 0; index < expression.length(); index++) {
                char current = expression.charAt(index);
                if (current == '(') {
                    level++;
                } else if (current == ')') {
                    level--;
                    if (level < 0) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                }

                if (level == 0 && index <= expression.length() - operator.length() && expression.startsWith(operator, index)) {
                    return index;
                }
            }

            if (level != 0) {
                throw new IllegalArgumentException("括号不匹配");
            }
            return -1;
        }

        /**
         * 判断当前字符串是否被“一对最外层括号”完整包住。
         *
         * <pre>
         * ((a > b))        -> true
         * (a + b) > c      -> false
         * </pre>
         */
        private boolean isWrappedByOuterParentheses(String expression) {
            if (expression.length() < 2 || expression.charAt(0) != '(' || expression.charAt(expression.length() - 1) != ')') {
                return false;
            }

            int level = 0;
            for (int index = 0; index < expression.length(); index++) {
                char current = expression.charAt(index);
                if (current == '(') {
                    level++;
                } else if (current == ')') {
                    level--;
                    if (level < 0) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                    if (level == 0 && index < expression.length() - 1) {
                        return false;
                    }
                }
            }

            if (level != 0) {
                throw new IllegalArgumentException("括号不匹配");
            }
            return true;
        }
    }

    private static final class CompareParser {
        /**
         * 比较运算符按“长的优先”排列。
         *
         * <p>原因：
         * 如果先判断 ">"，那么 ">=" 会被错误拆成 ">" 和 "="。
         * 所以顺序必须是：
         * >=  <=  ==  !=  >  <
         */
        private static final String[] OPERATORS = {">=", "<=", "==", "!=", ">", "<"};

        private final String text;
        private final Map<String, Object> varMap;

        private CompareParser(String text, Map<String, Object> varMap) {
            this.text = text;
            this.varMap = varMap;
        }

        /**
         * 比较表达式解析流程：
         *
         * <pre>
         * 原表达式：a + b * (c + 2) > 10
         *                    ↓
         * 切成左边：a + b * (c + 2)
         *      右边：10
         *                    ↓
         * 左右两边分别走 ArithmeticParser
         *                    ↓
         * 最后执行 left > right
         * </pre>
         */
        private boolean parse() {
            for (String operator : OPERATORS) {
                int index = findOperatorOutsideParentheses(operator);
                if (index < 0) {
                    continue;
                }

                String leftExpression = text.substring(0, index).trim();
                String rightExpression = text.substring(index + operator.length()).trim();
                if (leftExpression.isEmpty() || rightExpression.isEmpty()) {
                    throw new IllegalArgumentException("比较表达式格式错误");
                }

                BigDecimal left = new ArithmeticParser(leftExpression, varMap).parse();
                BigDecimal right = new ArithmeticParser(rightExpression, varMap).parse();
                int compareResult = left.compareTo(right);

                return switch (operator) {
                    case ">" -> compareResult > 0;
                    case "<" -> compareResult < 0;
                    case ">=" -> compareResult >= 0;
                    case "<=" -> compareResult <= 0;
                    case "==" -> compareResult == 0;
                    case "!=" -> compareResult != 0;
                    default -> throw new IllegalArgumentException("不支持的比较运算符: " + operator);
                };
            }
            throw new IllegalArgumentException("比较表达式缺少比较运算符");
        }

        /**
         * 只在“最外层”寻找比较运算符。
         *
         * <p>为什么要这样做？
         * 因为括号里的内容应该先当成一个整体计算，不能提前拆开。
         *
         * <pre>
         * 扫描时维护一个 level：
         * level = 0  表示当前在最外层
         * level = 1  表示当前在一层括号里
         * level = 2  表示当前在两层括号里
         *
         * 读到 '('  -> level + 1
         * 读到 ')'  -> level - 1
         *
         * 只有 level == 0 时，当前运算符才允许作为“主比较符号”。
         * </pre>
         */
        private int findOperatorOutsideParentheses(String operator) {
            int level = 0;
            for (int i = 0; i <= text.length() - operator.length(); i++) {
                char current = text.charAt(i);
                if (current == '(') {
                    level++;
                } else if (current == ')') {
                    level--;
                    if (level < 0) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                }

                if (level == 0 && text.startsWith(operator, i)) {
                    return i;
                }
            }

            if (level != 0) {
                throw new IllegalArgumentException("括号不匹配");
            }
            return -1;
        }
    }

    private static final class ArithmeticParser {
        private final String text;
        private final Map<String, Object> varMap;
        private int position;

        private ArithmeticParser(String text, Map<String, Object> varMap) {
            this.text = text;
            this.varMap = varMap;
        }

        /**
         * 算术表达式总入口。
         *
         * <p>这里先从最高层规则 parseExpression() 开始解析，
         * 最后要求整个字符串都被消费完。
         *
         * <pre>
         * 正确：a + b * 2
         * 错误：a + b * 2 xyz
         * </pre>
         */
        private BigDecimal parse() {
            BigDecimal value = parseExpression();
            skipWhitespace();
            if (position < text.length()) {
                throw new IllegalArgumentException("非法字符: " + text.charAt(position));
            }
            return value;
        }

        /**
         * expression 负责处理加减：
         *
         * <pre>
         * expression = term (('+' | '-') term)*
         * </pre>
         *
         * <p>加减是低优先级，先让更高优先级的 term 算完。
         */
        private BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value = value.add(parseTerm());
                } else if (match('-')) {
                    value = value.subtract(parseTerm());
                } else {
                    return value;
                }
            }
        }

        /**
         * term 负责处理乘除：
         *
         * <pre>
         * term = factor (('*' | '/') factor)*
         * </pre>
         *
         * <p>调用顺序：
         * <pre>
         * expression -> term -> factor
         * </pre>
         *
         * <p>因此天然实现了：
         * <pre>
         * 括号 / 数字 / 变量
         *        ↑
         *      乘除
         *        ↑
         *      加减
         * </pre>
         */
        private BigDecimal parseTerm() {
            BigDecimal value = parseFactor();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value = value.multiply(parseFactor());
                } else if (match('/')) {
                    BigDecimal divisor = parseFactor();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException("除数不能为0");
                    }
                    value = value.divide(divisor, MathContext.DECIMAL128);
                } else {
                    return value;
                }
            }
        }

        /**
         * factor 是最基础的组成单元。
         *
         * <p>它只关心以下几类内容：
         * <pre>
         * 1. 一元正负号：+x / -x
         * 2. 括号表达式：(a + b)
         * 3. 数字：123 / 12.5
         * 4. 变量：a / price / total_1
         * </pre>
         */
        private BigDecimal parseFactor() {
            skipWhitespace();

            // 一元正号：例如 +a，本质上值不变，继续递归解析后面的因子即可。
            if (match('+')) {
                return parseFactor();
            }
            // 一元负号：例如 -a，先拿到 a 的值，再取反。
            if (match('-')) {
                return parseFactor().negate();
            }

            // 括号的含义是“强制把里面内容当成一个整体先算”。
            if (match('(')) {
                BigDecimal value = parseExpression();
                skipWhitespace();
                if (!match(')')) {
                    throw new IllegalArgumentException("缺少右括号 )");
                }
                return value;
            }
            if (position >= text.length()) {
                throw new IllegalArgumentException("表达式格式错误");
            }

            char current = text.charAt(position);
            if (Character.isDigit(current) || current == '.') {
                return parseNumber();
            }
            if (Character.isLetter(current) || current == '_') {
                return parseVariable();
            }
            throw new IllegalArgumentException("非法字符: " + current);
        }

        /**
         * 解析数字字面量，只支持整数和小数。
         */
        private BigDecimal parseNumber() {
            int start = position;
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
            return new BigDecimal(number);
        }

        /**
         * 解析变量，再从 varMap 中取值并转成 BigDecimal。
         */
        private BigDecimal parseVariable() {
            int start = position;
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
                return bigDecimal;
            }
            if (value instanceof Number || value instanceof String) {
                return new BigDecimal(value.toString());
            }
            throw new IllegalArgumentException("变量类型不支持: " + variableName);
        }

        /**
         * 跳过空白字符，让表达式既支持 a+b，也支持 a + b。
         */
        private void skipWhitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        /**
         * 如果当前位置正好是期望字符，就“吃掉”它。
         */
        private boolean match(char expected) {
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }
    }
}
