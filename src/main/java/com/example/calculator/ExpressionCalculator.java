package com.example.calculator;

import java.util.Map;

/**
 * 表达式计算器统一接口。
 *
 * <p>当前项目提供两种实现：
 * <pre>
 * 1. RecursiveExpressionCalculator  // 保留原来的递归下降算法
 * 2. IterativeExpressionCalculator  // 新增的非递归算法，支持超深嵌套
 * </pre>
 */
public interface ExpressionCalculator {

    /**
     * 计算算术表达式，例如：
     * <pre>
     * a + b * (c + 2)
     * </pre>
     */
    String calculation(String text, Map<String, Object> varMap);

    /**
     * 计算比较表达式，例如：
     * <pre>
     * a + b * (c + 2) > 10
     * </pre>
     */
    boolean compareCalculation(String text, Map<String, Object> varMap);
}
