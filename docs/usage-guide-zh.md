# 表达式计算器使用说明（中文）

## 1. 概述
`expression-calculator` 提供统一接口用于带变量、方法调用的算术与逻辑表达式求值，适用于业务规则、配置、数据校验等场景。

### 主要特性
- 统一接口：`ExpressionCalculator`
- 两种实现：
  - `RecursiveExpressionCalculator`：递归，结构直观，适合教学和简单表达式
  - `IterativeExpressionCalculator`：显式栈，适合深层嵌套和生产环境
- 支持算术、比较、逻辑运算及方法调用
- 防御恶意或过深表达式

## 2. 环境要求
- Java 17 及以上
- Maven 3.8 及以上

## 3. 快速开始

### 3.1 添加依赖
Maven `pom.xml`：
```xml
<dependency>
  <groupId>com.clmcat.commons</groupId>
  <artifactId>expression-calculator</artifactId>
  <version>YOUR_VERSION</version>
</dependency>
```

### 3.2 基本用法
```java
ExpressionCalculator calc = new IterativeExpressionCalculator();
Map<String, Object> vars = Map.of("a", 1, "b", 2, "c", 3);
String result = calc.calculation("a + b * (c + 2)", vars); // "11"
boolean ok = calc.compareCalculation("a + b > 2 && c == 3", vars); // true
```

### 3.3 设置表达式层级限制
```java
ExpressionCalculator calc = new IterativeExpressionCalculator(100); // 最大层级100
```
超限抛出：`表达式层级超过限制: 100`

## 4. 支持的表达式类型

### 4.1 算术表达式（`calculation`）
- 运算符：`+ - * /`
- 括号：`()`
- 一元正负号：`+x -x`
- 字符串字面量 `"text"`、字符字面量 `'A'`
- 变量引用
- 方法调用（如 `str.length()`）

#### 示例
```java
Map<String, Object> vars = Map.of("price", 12.5, "discount", 2.5);
String result = calc.calculation("price + discount", vars); // "15"
```

- `+` 遇到非数值字符串/字符时按拼接处理。
- 若两侧仍都能识别为数字，则保持数值加法语义。

### 4.2 比较与逻辑表达式（`compareCalculation`）
- 比较：`== != > < >= <=`
- 逻辑：`&& ||`
- 括号
- 字符串/字符字面量参与比较
- 缺失变量在与 `null` 做 `==` / `!=` 比较时按 `null` 参与判断
- 变量直接参与真值判断（见下）
- 默认运算符之外，可通过 `OperatorRegistry` 扩展新的普通运算符（如 `%`、`^`）

说明：

- `&&` 和 `||` 仍保留短路实现，不通过普通运算符注册表扩展
- 注册示例见 `expression-calculator\README_EXTENSIBILITY.md`

#### 示例
```java
Map<String, Object> vars = Map.of("enabled", true, "count", 5);
boolean ok = calc.compareCalculation("enabled && count > 0", vars); // true
```

## 5. 支持的变量类型（布尔条件）
在 `compareCalculation` 中，以下类型可直接作为条件：
| 类型         | 规则                |
|--------------|---------------------|
| null         | false               |
| Boolean      | 自身值              |
| File         | file.exists()       |
| Collection   | 非空为 true         |
| Map          | 非空为 true         |
| 数组         | 长度>0为 true       |

数字/字符串/字符需配合比较运算符使用（如 `a > 0`）。
缺失变量若不参与 `null` 等值比较，仍会报 `变量不存在`。

## 6. 表达式中的方法调用
- 支持无参、有参、链式方法调用
- 变量中的数字会转为 `BigDecimal` 参与方法匹配
- 直接字面量保留原类型
- 方法参数支持字符串/字符字面量

#### 示例
```java
vars.put("str", "Hello World");
calc.calculation("str.length()", vars); // "11"
```

```java
calc.calculation("\"a,b\".replace(\",\", \";\")", vars); // "a;b"
```

## 7. 计算边界与防御能力
- 可配置最大表达式层级（构造器参数）
- 防御括号不匹配、非法数字、缺失变量、除零、非法方法调用等
- 引号内的 `,`、`&&`、`||`、括号不会被误判为结构符号
- 迭代实现可防止深度递归导致栈溢出

## 8. 异常情况
| 场景             | 示例/异常信息                  |
|------------------|-------------------------------|
| 空表达式         | `表达式不能为空`               |
| 层级超限         | `表达式层级超过限制: N`        |
| 括号不匹配       | `括号不匹配`                   |
| 非法数字         | `数字格式错误`                 |
| 缺失变量         | `变量不存在: x`                |
| 除零             | `除数不能为0`                  |
| 方法对象为null   | `方法调用失败: 对象为null`     |
| 方法类型不匹配   | `方法调用失败，参数类型不匹配` |

## 9. 适用范围
- 适用于带变量、方法调用、深层嵌套的算术与逻辑表达式
- 不适合完整脚本或非Java类型

## 10. 进阶示例

### 10.1 链式方法调用
```java
vars.put("file", new File("/tmp/test.txt"));
calc.calculation("file.getName().substring(0, 4).length()", vars);
```

### 10.2 防御场景
- 深度嵌套：`(1 + (2 + (3 + ... )))`
- 深层布尔：`a == b || (c == d || (...))`
- 所有此类场景均有层级限制与清晰异常提示。

---
