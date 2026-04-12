package com.example.calculator;

import java.util.Map;

/**
 * 递归下降版本的表达式计算器。
 *
 * <p>它更适合作为“语法规则如何落到代码里”的教学实现：
 * <pre>
 * expression -> or -> and -> comparison -> additive -> multiplicative -> unary -> primary
 * </pre>
 *
 * <p>优点是结构直观，几乎可以一行一行对照语法阅读。
 * 缺点是表达式层级越深，Java 调用栈也会越深，因此超深嵌套场景应优先使用
 * {@link IterativeExpressionCalculator}。
 */
public class RecursiveExpressionCalculator implements ExpressionCalculator {

    @Override
    public String calculation(String text, Map<String, Object> varMap) {
        return RecursiveExpressionEngine.evaluateForCalculation(text, varMap);
    }

    @Override
    public boolean compareCalculation(String text, Map<String, Object> varMap) {
        return RecursiveExpressionEngine.evaluateForComparison(text, varMap);
    }
}
