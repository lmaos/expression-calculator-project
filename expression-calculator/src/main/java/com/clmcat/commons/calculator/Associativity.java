package com.clmcat.commons.calculator;

/**
 * 运算符结合性，决定同优先级运算符在归约时向左还是向右。
 */
public enum Associativity {
    /** 左结合：同优先级运算符优先处理左侧表达式。 */
    LEFT,
    /** 右结合：同优先级运算符继续向右吸收，例如幂运算。 */
    RIGHT
}
