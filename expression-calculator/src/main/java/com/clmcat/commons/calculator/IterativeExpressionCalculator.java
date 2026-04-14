package com.clmcat.commons.calculator;

import java.util.Map;

/**
 * 迭代版表达式计算器。
 *
 * <p>本实现把两类最容易被深层输入放大的路径都改成了显式栈：
 * <pre>
 * 1. 值表达式     -> IterativeExpressionEngine
 * 2. 布尔比较表达式 -> IterativeBooleanExpressionEngine
 * </pre>
 *
 * <p>因此无论攻击输入是：
 * <pre>
 * (1 + (2 + (3 + ...)))
 * a == b || (c == d || (x == y && (...)))
 * </pre>
 *
 * <p>都不会再因为 Java 方法递归过深而直接栈溢出。
 */
public class IterativeExpressionCalculator implements ExpressionCalculator {

    private final int maxDepth;

    public IterativeExpressionCalculator() {
        this(-1);
    }

    public IterativeExpressionCalculator(int maxDepth) {
        if (maxDepth < -1) {
            throw new IllegalArgumentException("层级限制不能小于-1");
        }
        this.maxDepth = maxDepth;
    }

    @Override
    public String calculation(String text, Map<String, Object> varMap) {
        String required = ExpressionRuntimeSupport.requireText(text);
        ExpressionRuntimeSupport.ensureWithinDepthLimit(required, maxDepth);
        return IterativeExpressionEngine.evaluateForCalculation(required, varMap);
    }

    @Override
    public boolean compareCalculation(String text, Map<String, Object> varMap) {
        String required = ExpressionRuntimeSupport.requireText(text);
        ExpressionRuntimeSupport.ensureWithinDepthLimit(required, maxDepth);
        return IterativeBooleanExpressionEngine.evaluate(required, varMap);
    }

    @Override
    public Object evaluate(String text, Map<String, Object> varMap) {
        String required = ExpressionRuntimeSupport.requireText(text);
        ExpressionRuntimeSupport.ensureWithinDepthLimit(required, maxDepth);
        return ExpressionRuntimeSupport.toEvaluateResult(IterativeExpressionEngine.evaluateAny(required, varMap));
    }
}
