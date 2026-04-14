package com.clmcat.commons.calculator;

import java.util.Map;

/**
 * 表达式计算器统一接口。
 *
 * <p>当前项目提供两种实现：
 * <pre>
 * 1. RecursiveExpressionCalculator  // 递归下降算法
 * 2. IterativeExpressionCalculator  // 非递归算法，支持超深嵌套
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



    /**
     * 计算表达式并返回原始对象值。
     *
     * <p>与 {@link #calculation(String, Map)} {@link #compareCalculation(String, Map)} 的区别：
     * <ul>
     *   <li>不进行结果格式化（数字不会转为字符串，布尔不会转为 "true"/"false"）</li>
     *   <li>对于单个变量，直接返回变量值，若变量不存在则返回 {@code null}</li>
     *   <li>支持算术运算、字符串拼接、比较运算、逻辑运算、下标访问和类型转换等，返回类型可能是 Number、String、Boolean、Collection 等</li>
     *   <li>同时兼顾 计算与计算表达式目前本身的能力的情况，增加变量值获得。</li>
     * </ul>
     *
     * <p>示例：
     * <pre>
     * evaluate("a", map)                // 返回变量 a 的值
     * evaluate("1 + 2", map)            // 返回 Integer 3
     * evaluate("1 + 2L", map)           // 返回 Long 3
     * evaluate("'hello' + 123", map)    // 返回 "hello123"
     * evaluate("1 == 1", map)           // 返回 Boolean.TRUE
     * evaluate("flagA || flagB", map)   // 返回 Boolean.TRUE
     * </pre>
     *
     * @param text 表达式文本
     * @param varMap 变量上下文
     * @return 表达式求值结果，变量不存在时返回 null
     */
    Object evaluate(String text, Map<String, Object> varMap);
}
