package com.clmcat.commons.calculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 递归版表达式引擎。
 *
 * <p>它保留语法层的直观结构，同时把值表达式的优先级解析交给运算符注册表。
 */
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

    // AST 节点：每个节点只负责自己的求值。
    private interface Node {
        RuntimeValue evaluate();
    }

    // 递归下降解析器，负责把文本拆成 AST。
    private static final class Parser {
        private final String text;
        private final Map<String, Object> variables;
        private final OperatorRegistry operatorRegistry = OperatorRegistry.getInstance();
        private int position;

        private Parser(String text, Map<String, Object> variables) {
            this.text = text;
            this.variables = variables;
        }

        private Node parseExpression() {
            return parseOr();
        }

        /**
         * 递归版仍然保留逻辑层的直观结构：
         * <pre>
         * or
         * └── and
         *     └── value-expression
         * </pre>
         *
         * <p>但 value-expression 不再硬编码 additive / multiplicative / comparison，
         * 而是交给通用优先级解析器按注册表驱动。
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
            Node node = parseValueExpression(0);
            while (true) {
                skipWhitespace();
                if (!match("&&")) {
                    return node;
                }
                Node right = parseValueExpression(0);
                node = new LogicalNode(node, "&&", right);
            }
        }

        /**
         * 通用优先级解析器（Pratt 风格）：
         * 1. 前缀位置读取一元运算符或 primary
         * 2. 中缀位置按“当前最小优先级”继续吃掉可接受的二元运算符
         *
         * <p>这样新增 `%`、`**`、`^` 等运算符时，只需要注册，不需要再补新的 parseXxx 层。
         */
        private Node parseValueExpression(int minimumPrecedence) {
            Node left = parsePrefixExpression();
            while (true) {
                skipWhitespace();
                Operator operator = operatorRegistry.matchBinaryOperator(text, position);
                if (operator == null || operator.precedence() < minimumPrecedence) {
                    return left;
                }
                position += operator.symbol().length();
                int nextMinimum = operator.isRightAssociative() ? operator.precedence() : operator.precedence() + 1;
                Node right = parseValueExpression(nextMinimum);
                left = new BinaryOperatorNode(left, operator, right);
            }
        }

        private Node parsePrefixExpression() {
            skipWhitespace();
            Operator operator = operatorRegistry.matchUnaryOperator(text, position);
            if (operator != null) {
                position += operator.symbol().length();
                Node operand = parseValueExpression(operator.precedence());
                return new UnaryNode(operator, operand);
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

    // ----- AST 节点定义 -----
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
        private final Operator operator;
        private final Node operand;

        private UnaryNode(Operator operator, Node operand) {
            this.operator = operator;
            this.operand = operand;
        }

        @Override
        public RuntimeValue evaluate() {
            return operator.apply(operand.evaluate());
        }
    }

    private static final class BinaryOperatorNode implements Node {
        private final Node left;
        private final Operator operator;
        private final Node right;

        private BinaryOperatorNode(Node left, Operator operator, Node right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public RuntimeValue evaluate() {
            return operator.apply(left.evaluate(), right.evaluate());
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
