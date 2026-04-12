package com.example.calculator;

import java.util.Map;

/**
 * 递归下降版本的表达式计算器。
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
