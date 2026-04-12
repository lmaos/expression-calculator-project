# Expression Calculator 使用说明

## 1. 项目概述

`expression-calculator` 提供统一接口 `ExpressionCalculator`，用于：

1. **calculation**：计算算术表达式，返回字符串结果
2. **compareCalculation**：计算比较/逻辑表达式，返回布尔结果

项目当前提供两个实现：

| 实现类 | 特点 | 适用场景 |
| --- | --- | --- |
| `RecursiveExpressionCalculator` | 递归下降实现，结构直观，便于教学与阅读 | 普通表达式、语法理解 |
| `IterativeExpressionCalculator` | 显式栈实现，针对深层算术与深层布尔嵌套更稳健 | 生产场景、深层输入、防恶意嵌套 |

## 2. 环境要求

- JDK 17
- Maven 3.8+

运行测试：

```bash
mvn test
```

## 3. 快速开始

### 3.1 默认构造

默认不限制表达式层级：

```java
ExpressionCalculator recursive = new RecursiveExpressionCalculator();
ExpressionCalculator iterative = new IterativeExpressionCalculator();
```

### 3.2 指定层级限制

当表达式层级超过限制时，会直接抛出异常：

```java
ExpressionCalculator calculator = new IterativeExpressionCalculator(100);
ExpressionCalculator recursive = new RecursiveExpressionCalculator(100);
```

异常信息：

```text
表达式层级超过限制: 100
```

## 4. 算术表达式 calculation

支持：

- `+ - * /`
- 括号 `()`
- 一元正负号 `+x -x`
- 变量引用
- 方法调用

### 示例

```java
Map<String, Object> variables = new HashMap<>();
variables.put("a", 1);
variables.put("b", 2);
variables.put("c", 3);

ExpressionCalculator calculator = new IterativeExpressionCalculator();

String result = calculator.calculation("a + b * (c + 2)", variables);
// result = "11"
```

### 数字处理规则

- 表达式计算时，数字统一按 `BigDecimal` 语义处理
- 返回值会做规范化，例如：
  - `5.0 -> "5"`
  - `0.000 -> "0"`

## 5. 比较表达式 compareCalculation

支持：

- 比较运算：`== != > < >= <=`
- 逻辑运算：`&& ||`
- 布尔分组：`()`
- 变量、文件、集合、布尔值直接参与真值判断

### 示例

```java
Map<String, Object> variables = new HashMap<>();
variables.put("x", 5);
variables.put("y", 10);
variables.put("a", 10);
variables.put("isTrue", true);

ExpressionCalculator calculator = new IterativeExpressionCalculator();

boolean r1 = calculator.compareCalculation("x + y > a + 1", variables);
// true

boolean r2 = calculator.compareCalculation("x > 0 && (y < 20 || a == 10)", variables);
// true

boolean r3 = calculator.compareCalculation("isTrue == true && x > 0", variables);
// true
```

## 6. 变量真值规则

在 `compareCalculation` 中，以下类型可以直接作为条件使用：

| 值类型 | 规则 |
| --- | --- |
| `null` | `false` |
| `Boolean` | 使用其自身值 |
| `File` | `file.exists()` |
| `Collection` | 非空为 `true` |
| `Map` | 非空为 `true` |
| 数组 | 长度大于 0 为 `true` |

说明：

- 数字、数字字符串不能直接作为最终布尔结果
- 例如 `compareCalculation("a + b", variables)` 会报错，因为缺少比较运算符

## 7. 方法调用

支持：

1. 无参方法调用
2. 有参方法调用
3. 链式方法调用

### 无参方法

```java
variables.put("str", "Hello World");
calculator.calculation("str.length()", variables);
// "11"
```

### 有参方法

```java
variables.put("num1", new BigDecimal("123"));
variables.put("num2", 55);

calculator.calculation("num1.add(num2)", variables);
// "178"
```

### 链式方法

```java
calculator.calculation("file.getName().substring(0, 4).length()", variables);
```

## 8. 方法参数匹配规则

这是本项目的重要约束：

1. **直接字面量参数**保留原始类型
2. **来自变量表的数字参数**会优先按 `BigDecimal` 参与方法匹配

例如：

```java
variables.put("num1", new BigDecimal("123"));
variables.put("num2", 55);

calculator.calculation("num1.add(num2)", variables);
// 合法，num2 作为变量数字，会转换为 BigDecimal("55")
```

```java
calculator.calculation("num1.add(55)", variables);
// 非法，55 是直接字面量，类型按 int 参与匹配
```

## 9. 防御性能力

当前实现已经覆盖以下防御场景：

- 超深包裹括号：`((((...))))`
- 超深正常算术嵌套：`(1 + (2 + (3 + ... )))`
- 超深布尔嵌套：`a == b || (c == d || (...))`
- 层级超限时快速失败
- 非法数字格式
- 括号不匹配
- 缺失变量
- 除零
- 非法方法调用

其中 `IterativeExpressionCalculator` 对深层输入的防御能力更强。

## 10. 常见异常

| 场景 | 示例异常 |
| --- | --- |
| 空表达式 | `表达式不能为空` |
| 深度超限 | `表达式层级超过限制: N` |
| 括号错误 | `括号不匹配` |
| 数字非法 | `数字格式错误` |
| 缺失变量 | `变量不存在: x` |
| 除零 | `除数不能为0` |
| 方法对象为空 | `方法调用失败: 对象为空, 方法: xxx` |
| 方法类型不匹配 | `方法调用失败，参数类型不匹配: Xxx.method` |

## 11. 选择建议

- 如果重点是**阅读、教学、理解语法**：使用 `RecursiveExpressionCalculator`
- 如果重点是**稳定性、深层输入、防恶意嵌套**：使用 `IterativeExpressionCalculator`

