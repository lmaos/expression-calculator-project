package com.clmcat.commons.calculator;

import com.clmcat.commons.calculator.ExpressionRuntimeSupport.RuntimeValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RecursiveExpressionEngine {

    private RecursiveExpressionEngine() {
    }

    static String evaluateForCalculation(String text, Map<String, Object> variables) {
        RuntimeValue value = evaluateValue(text, variables);
        return ExpressionRuntimeSupport.formatForCalculation(value);
    }

    static boolean evaluateForComparison(String text, Map<String, Object> variables) {
        RuntimeValue value = evaluateValue(text, variables);
        return ExpressionRuntimeSupport.toStandaloneBoolean(value);
    }

    static RuntimeValue evaluateValue(String text, Map<String, Object> variables) {
        Parser parser = new Parser(ExpressionRuntimeSupport.requireText(text), variables);
        Node node = parser.parseExpression();
        parser.skipWhitespace();
        if (!parser.isEnd()) {
            throw new IllegalArgumentException("非法字符: " + parser.currentChar());
        }
        return node.evaluate();
    }

    private interface Node {
        RuntimeValue evaluate();
    }

    private static final class Parser {
        private final String text;
        private final Map<String, Object> variables;
        private int position;

        private Parser(String text, Map<String, Object> variables) {
            this.text = text;
            this.variables = variables;
        }

        private Node parseExpression() {
            return parseOr();
        }

        /**
         * 递归版入口直接按“最低优先级 -> 最高优先级”逐层下钻：
         * <pre>
         * or
         * └── and
         *     └── comparison
         *         └── additive
         *             └── multiplicative
         *                 └── unary
         *                     └── primary
         * </pre>
         *
         * <p>好处是每一层只关注自己那一级运算符，代码和语法几乎一一对应。
         */
        private Node parseOr() {
            Node node = parseAnd();
            while (true) {
                skipWhitespace();
                if (!match("||")) {
                    return node;
                }
                Node right = parseAnd();
                node = new LogicalNode(node, "||", right);
            }
        }

        private Node parseAnd() {
            Node node = parseComparison();
            while (true) {
                skipWhitespace();
                if (!match("&&")) {
                    return node;
                }
                Node right = parseComparison();
                node = new LogicalNode(node, "&&", right);
            }
        }

        private Node parseComparison() {
            Node left = parseAdditive();
            skipWhitespace();
            String operator = readComparisonOperator();
            if (operator == null) {
                return left;
            }
            Node right = parseAdditive();
            return new ComparisonNode(left, operator, right);
        }

        private Node parseAdditive() {
            Node node = parseMultiplicative();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    Node right = parseMultiplicative();
                    node = new ArithmeticNode(node, '+', right);
                } else if (match('-')) {
                    Node right = parseMultiplicative();
                    node = new ArithmeticNode(node, '-', right);
                } else {
                    return node;
                }
            }
        }

        private Node parseMultiplicative() {
            Node node = parseUnary();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    Node right = parseUnary();
                    node = new ArithmeticNode(node, '*', right);
                } else if (match('/')) {
                    Node right = parseUnary();
                    node = new ArithmeticNode(node, '/', right);
                } else {
                    return node;
                }
            }
        }

        private Node parseUnary() {
            skipWhitespace();
            if (match('+')) {
                return new UnaryNode('+', parseUnary());
            }
            if (match('-')) {
                return new UnaryNode('-', parseUnary());
            }
            return parsePrimary();
        }

        private Node parsePrimary() {
            skipWhitespace();
            if (isEnd()) {
                throw new IllegalArgumentException("表达式格式错误");
            }

            char current = currentChar();
            Node node;
            if (current == '(') {
                position++;
                Node inner = parseExpression();
                skipWhitespace();
                if (!match(')')) {
                    throw new IllegalArgumentException("括号不匹配");
                }
                node = inner;
            } else if (current == '"') {
                node = new LiteralNode(parseStringLiteral());
            } else if (current == '\'') {
                node = new LiteralNode(parseCharacterLiteral());
            } else if (Character.isDigit(current) || current == '.') {
                node = new LiteralNode(parseNumberLiteral());
            } else if (ExpressionTextSupport.isIdentifierStart(current)) {
                String identifier = parseIdentifier();
                if ("true".equals(identifier)) {
                    node = new LiteralNode(Boolean.TRUE);
                } else if ("false".equals(identifier)) {
                    node = new LiteralNode(Boolean.FALSE);
                } else if ("null".equals(identifier)) {
                    node = new LiteralNode(null);
                } else {
                    node = new VariableNode(identifier, variables);
                }
            } else {
                throw new IllegalArgumentException("非法字符: " + current);
            }
            return parsePostfix(node);
        }

        private Node parsePostfix(Node node) {
            while (true) {
                skipWhitespace();
                if (!match('.')) {
                    return node;
                }
                String methodName = parseIdentifier();
                skipWhitespace();
                if (!match('(')) {
                    throw new IllegalArgumentException("非法字符: .");
                }
                // 方法调用仍然复用“表达式参数”规则，因此参数本身也可以继续嵌套。
                List<Node> arguments = new ArrayList<>();
                skipWhitespace();
                if (!match(')')) {
                    do {
                        arguments.add(parseExpression());
                        skipWhitespace();
                    } while (match(','));
                    if (!match(')')) {
                        throw new IllegalArgumentException("括号不匹配");
                    }
                }
                node = new MethodCallNode(node, methodName, arguments);
            }
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

        private String readComparisonOperator() {
            String operator = ExpressionTextSupport.readComparisonOperator(text, position);
            if (operator != null) {
                position += operator.length();
            }
            return operator;
        }

        private String parseIdentifier() {
            skipWhitespace();
            ExpressionTextSupport.ParsedToken<String> token = ExpressionTextSupport.parseIdentifier(text, position);
            position = token.nextIndex();
            return token.value();
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

        private void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(currentChar())) {
                position++;
            }
        }

        private boolean isEnd() {
            return position >= text.length();
        }

        private char currentChar() {
            return text.charAt(position);
        }
    }

    private static final class LiteralNode implements Node {
        private final Object value;

        private LiteralNode(Object value) {
            this.value = value;
        }

        @Override
        public RuntimeValue evaluate() {
            return RuntimeValue.literal(value);
        }
    }

    private static final class VariableNode implements Node {
        private final String name;
        private final Map<String, Object> variables;

        private VariableNode(String name, Map<String, Object> variables) {
            this.name = name;
            this.variables = variables;
        }

        @Override
        public RuntimeValue evaluate() {
            if (variables == null || !variables.containsKey(name)) {
                return RuntimeValue.missingVariable(name);
            }
            return RuntimeValue.variable(variables.get(name));
        }
    }

    private static final class UnaryNode implements Node {
        private final char operator;
        private final Node operand;

        private UnaryNode(char operator, Node operand) {
            this.operator = operator;
            this.operand = operand;
        }

        @Override
        public RuntimeValue evaluate() {
            RuntimeValue value = operand.evaluate();
            return operator == '-' ? ExpressionRuntimeSupport.negate(value) : ExpressionRuntimeSupport.positive(value);
        }
    }

    private static final class ArithmeticNode implements Node {
        private final Node left;
        private final char operator;
        private final Node right;

        private ArithmeticNode(Node left, char operator, Node right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public RuntimeValue evaluate() {
            RuntimeValue leftValue = left.evaluate();
            RuntimeValue rightValue = right.evaluate();
            switch (operator) {
                case '+':
                    return ExpressionRuntimeSupport.add(leftValue, rightValue);
                case '-':
                    return ExpressionRuntimeSupport.subtract(leftValue, rightValue);
                case '*':
                    return ExpressionRuntimeSupport.multiply(leftValue, rightValue);
                case '/':
                    return ExpressionRuntimeSupport.divide(leftValue, rightValue);
                default:
                    throw new IllegalArgumentException("非法字符: " + operator);
            }
        }
    }

    private static final class ComparisonNode implements Node {
        private final Node left;
        private final String operator;
        private final Node right;

        private ComparisonNode(Node left, String operator, Node right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public RuntimeValue evaluate() {
            return RuntimeValue.computed(ExpressionRuntimeSupport.compare(left.evaluate(), operator, right.evaluate()));
        }
    }

    private static final class LogicalNode implements Node {
        private final Node left;
        private final String operator;
        private final Node right;

        private LogicalNode(Node left, String operator, Node right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public RuntimeValue evaluate() {
            // 逻辑运算必须保留短路语义，不能先把左右两边都算完。
            boolean leftValue = ExpressionRuntimeSupport.toStandaloneBoolean(left.evaluate());
            if ("&&".equals(operator)) {
                if (!leftValue) {
                    return RuntimeValue.computed(false);
                }
                return RuntimeValue.computed(ExpressionRuntimeSupport.toStandaloneBoolean(right.evaluate()));
            }
            if (leftValue) {
                return RuntimeValue.computed(true);
            }
            return RuntimeValue.computed(ExpressionRuntimeSupport.toStandaloneBoolean(right.evaluate()));
        }
    }

    private static final class MethodCallNode implements Node {
        private final Node receiver;
        private final String methodName;
        private final List<Node> arguments;

        private MethodCallNode(Node receiver, String methodName, List<Node> arguments) {
            this.receiver = receiver;
            this.methodName = methodName;
            this.arguments = arguments;
        }

        @Override
        public RuntimeValue evaluate() {
            RuntimeValue receiverValue = receiver.evaluate();
            List<RuntimeValue> evaluatedArguments = new ArrayList<>(arguments.size());
            for (Node argument : arguments) {
                evaluatedArguments.add(argument.evaluate());
            }
            return ExpressionRuntimeSupport.invokeMethod(receiverValue, methodName, evaluatedArguments);
        }
    }
}
