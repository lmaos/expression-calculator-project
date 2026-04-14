package com.clmcat.commons.calculator;

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

    private final int maxDepth;

    public RecursiveExpressionCalculator() {
        this(-1);
    }

    public RecursiveExpressionCalculator(int maxDepth) {
        if (maxDepth < -1) {
            throw new IllegalArgumentException("层级限制不能小于-1");
        }
        this.maxDepth = maxDepth;
    }

    @Override
    public String calculation(String text, Map<String, Object> varMap) {
        String required = ExpressionRuntimeSupport.requireText(text);
        ExpressionRuntimeSupport.ensureWithinDepthLimit(required, maxDepth);
        return RecursiveExpressionEngine.evaluateForCalculation(required, varMap);
    }

    @Override
    public boolean compareCalculation(String text, Map<String, Object> varMap) {
        String required = ExpressionRuntimeSupport.requireText(text);
        ExpressionRuntimeSupport.ensureWithinDepthLimit(required, maxDepth);
        return RecursiveExpressionEngine.evaluateForComparison(required, varMap);
    }

    @Override
    public Object evaluate(String text, Map<String, Object> varMap) {
        String required = ExpressionRuntimeSupport.requireText(text);
        ExpressionRuntimeSupport.ensureWithinDepthLimit(required, maxDepth);
        return ExpressionRuntimeSupport.toEvaluateResult(RecursiveExpressionEngine.evaluateValue(required, varMap));
    }
}
