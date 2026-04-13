package com.clmcat.commons.calculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ExpressionTextSupport {

    private ExpressionTextSupport() {
    }

    static boolean isIdentifierStart(char current) {
        return Character.isLetter(current) || current == '_';
    }

    static boolean isIdentifierPart(char current) {
        return Character.isLetterOrDigit(current) || current == '_';
    }

    static boolean isQuote(char current) {
        return current == '"' || current == '\'';
    }

    static ParsedToken<String> parseIdentifier(String text, int start) {
        if (start >= text.length()) {
            throw new IllegalArgumentException("表达式格式错误");
        }
        char current = text.charAt(start);
        if (!isIdentifierStart(current)) {
            throw new IllegalArgumentException("非法字符: " + current);
        }
        int index = start + 1;
        while (index < text.length() && isIdentifierPart(text.charAt(index))) {
            index++;
        }
        return new ParsedToken<>(text.substring(start, index), index);
    }

    static ParsedToken<Object> parseNumberLiteral(String text, int start) {
        int index = start;
        boolean hasDigit = false;
        boolean hasDot = false;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (Character.isDigit(current)) {
                hasDigit = true;
                index++;
            } else if (current == '.') {
                if (hasDot) {
                    throw new IllegalArgumentException("数字格式错误");
                }
                hasDot = true;
                index++;
            } else {
                break;
            }
        }
        if (!hasDigit) {
            throw new IllegalArgumentException("数字格式错误");
        }

        String numberText = text.substring(start, index);
        if (".".equals(numberText)) {
            throw new IllegalArgumentException("数字格式错误");
        }

        if (index < text.length()) {
            char suffix = text.charAt(index);
            switch (suffix) {
                case 'L':
                case 'l':
                    return new ParsedToken<>(Long.valueOf(numberText), index + 1);
                case 'F':
                case 'f':
                    return new ParsedToken<>(Float.valueOf(numberText), index + 1);
                case 'D':
                case 'd':
                    return new ParsedToken<>(Double.valueOf(numberText), index + 1);
                case 'M':
                case 'm':
                    return new ParsedToken<>(new BigDecimal(numberText), index + 1);
                default:
                    break;
            }
        }

        Object value;
        if (hasDot) {
            value = Double.valueOf(numberText);
        } else {
            try {
                value = Integer.valueOf(numberText);
            } catch (NumberFormatException exception) {
                value = Long.valueOf(numberText);
            }
        }
        return new ParsedToken<>(value, index);
    }

    static ParsedToken<String> parseStringLiteral(String text, int start) {
        if (start >= text.length() || text.charAt(start) != '"') {
            throw new IllegalArgumentException("字符串字面量格式错误");
        }
        StringBuilder builder = new StringBuilder();
        int index = start + 1;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '"') {
                return new ParsedToken<>(builder.toString(), index + 1);
            }
            if (current == '\\') {
                if (index + 1 >= text.length()) {
                    throw new IllegalArgumentException("字符串字面量格式错误");
                }
                builder.append(parseEscape(text.charAt(index + 1)));
                index += 2;
                continue;
            }
            builder.append(current);
            index++;
        }
        throw new IllegalArgumentException("字符串字面量格式错误");
    }

    static ParsedToken<Character> parseCharacterLiteral(String text, int start) {
        if (start >= text.length() || text.charAt(start) != '\'') {
            throw new IllegalArgumentException("字符字面量格式错误");
        }
        int index = start + 1;
        if (index >= text.length()) {
            throw new IllegalArgumentException("字符字面量格式错误");
        }

        char value;
        char current = text.charAt(index);
        if (current == '\\') {
            if (index + 1 >= text.length()) {
                throw new IllegalArgumentException("字符字面量格式错误");
            }
            value = parseEscape(text.charAt(index + 1));
            index += 2;
        } else {
            value = current;
            index++;
        }

        if (index >= text.length() || text.charAt(index) != '\'') {
            throw new IllegalArgumentException("字符字面量格式错误");
        }
        return new ParsedToken<>(value, index + 1);
    }

    static int skipQuotedLiteral(String text, int start) {
        if (start >= text.length() || !isQuote(text.charAt(start))) {
            throw new IllegalArgumentException("表达式格式错误");
        }
        char quote = text.charAt(start);
        int index = start + 1;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\\') {
                if (index + 1 >= text.length()) {
                    throw new IllegalArgumentException(quote == '"' ? "字符串字面量格式错误" : "字符字面量格式错误");
                }
                index += 2;
                continue;
            }
            if (current == quote) {
                return index;
            }
            index++;
        }
        throw new IllegalArgumentException(quote == '"' ? "字符串字面量格式错误" : "字符字面量格式错误");
    }

    static int findMatchingParenthesis(String text, int openIndex) {
        int level = 0;
        for (int index = openIndex; index < text.length(); index++) {
            char current = text.charAt(index);
            if (isQuote(current)) {
                index = skipQuotedLiteral(text, index);
                continue;
            }
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

    static List<String> splitArguments(String argumentText) {
        if (argumentText.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> arguments = new ArrayList<>();
        int level = 0;
        int start = 0;
        for (int index = 0; index < argumentText.length(); index++) {
            char current = argumentText.charAt(index);
            if (isQuote(current)) {
                index = skipQuotedLiteral(argumentText, index);
                continue;
            }
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

    private static char parseEscape(char escaped) {
        switch (escaped) {
            case 'n':
                return '\n';
            case 't':
                return '\t';
            case 'r':
                return '\r';
            case '\\':
                return '\\';
            case '"':
                return '"';
            case '\'':
                return '\'';
            default:
                throw new IllegalArgumentException("非法转义字符: \\" + escaped);
        }
    }

    static final class ParsedToken<T> {
        private final T value;
        private final int nextIndex;

        private ParsedToken(T value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }

        T value() {
            return value;
        }

        int nextIndex() {
            return nextIndex;
        }
    }
}
