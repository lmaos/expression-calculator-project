# Expression Calculator 使用说明

## 1. 项目概述

`expression-calculator` 提供统一接口 `ExpressionCalculator`，用于带变量、公开字段/方法访问的表达式求值：

1. **calculation**：计算算术表达式，返回字符串结果
2. **compareCalculation**：计算比较/逻辑表达式，返回布尔结果

项目当前提供两个实现：

| 实现类 | 特点 | 适用场景 |
| --- | --- | --- |
| `RecursiveExpressionCalculator` | 递归下降实现，结构直观，便于教学与阅读 | 普通表达式、语法理解 |
| `IterativeExpressionCalculator` | 显式栈实现，针对深层算术与深层布尔嵌套更稳健 | 生产场景、深层输入、防恶意嵌套 |

## 2. 环境要求

- JDK 1.8+
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

- 算术运算：`+ - * / % ^`
- 位运算：`~ << >> >>> <<< & | ^`
- 括号 `()`
- 一元正负号 `+x -x`
- 字符串字面量 `"text"`、字符字面量 `'A'`
- 变量引用
- 公开字段/方法访问

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

### 数字与位运算规则

- 表达式计算时，普通数字运算统一按 `BigDecimal` 语义处理
- `+` 遇到非数值字符串/字符时按拼接处理；两侧都能识别为数字时仍按数值相加
- `**` 表示幂运算，`^` 表示位异或。
- 位运算只接受整数输入，按 **64 位整数** 语义计算
- `<<<` 是 `<<` 的 DSL 对称别名
- 返回值会做规范化，例如：
  - `5.0 -> "5"`
  - `0.000 -> "0"`

## 5. 比较表达式 compareCalculation

支持：

- 比较运算：`== != > < >= <=`
- 逻辑运算：`! && ||`
- 布尔分组：`()`
- 字符串/字符字面量参与比较
- 缺失变量在与 `null` 做 `==` / `!=` 比较时按 `null` 参与判断
- 变量、文件、集合、布尔值直接参与真值判断，并支持 `!` 取反
- 比较两侧可以继续包含 `%`、`**` 与位运算子表达式
- 默认内置已包含 `%`、`**`、`!`、`~`、`<<`、`>>`、`>>>`、`<<<`、`&`、`|`、`^`

说明：

- `&&` 和 `||` 仍保留短路实现，不通过普通运算符注册表扩展
- `!` 是内置一元运算符，可对单独变量、集合、文件或布尔方法结果做取反
- 自定义运算符注册示例见 `expression-calculator\README_EXTENSIBILITY.md`

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

boolean r3 = calculator.compareCalculation("(10 ^ 12) == 6", variables);
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
| 其他直接变量值 | 非 `null` 为 `true` |

说明：

- 直接变量会按上表参与真值判断，因此非 `null` 的数字、字符串、字符变量会被视为 `true`
- 缺失变量若不参与 `null` 等值比较，仍按“变量不存在”报错
- 例如 `compareCalculation("a + b", variables)` 会报错，因为缺少比较运算符

## 7. 公开字段与方法调用

支持：

1. 公开实例字段访问
2. 数组 `length` 访问
3. 无参/有参方法调用
4. 链式调用
5. 字符串/字符字面量参数

### 无参方法

```java
variables.put("str", "Hello World");
calculator.calculation("str.length()", variables);
// "11"
```

```java
public static final class Holder {
    public final String name;

    public Holder(String name) {
        this.name = name;
    }
}

variables.put("holder", new Holder("Copilot"));
variables.put("items", new String[] {"a", "b", "c"});

calculator.evaluate("holder.name", variables);
// "Copilot"

calculator.evaluate("items.length", variables);
// Integer(3)
```

### 有参方法

```java
variables.put("num1", new BigDecimal("123"));
variables.put("num2", 55);

calculator.calculation("num1.add(num2)", variables);
// "178"
```

```java
calculator.calculation("\"a,b\".replace(\",\", \";\")", variables);
// "a;b"
```

### 位运算示例

```java
calculator.calculation("10 & 12", variables);
// "8"

calculator.calculation("2 ^ 3", variables);
// "8"

calculator.calculation("3 <<< 2", variables);
// "12"
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

## 9. 模板输出注册器

`ExpressionFormat` 新增了带输出注册器的重载：

```java
OutputFormatRegistry registry = OutputFormatRegistry.getInstance().copy();
registry.setOption(byte[].class, "mode", "base64");
registry.setOption(File.class, "mode", "content");
registry.setOption(File.class, "charset", "UTF-8");
registry.setOption(Date.class, "pattern", "yyyyMMdd");
registry.setOption(Date.class, "timeZone", "UTC");

ExpressionFormat formatter = new DefaultExpressionFormat(calculator);
String text = formatter.format(
        "bytes=${payload}|file=${file}|date=${createdAt}",
        "${?}",
        variables,
        registry);
```

内置类型配置：

| 类型 | 配置 |
| --- | --- |
| `byte[]` | `mode=text/base64/hex`，`charset` |
| `File` | `mode=path/name/content`，`charset` |
| `Date` | `pattern`，`timeZone` |

旧的 `format(text, varMap)` / `format(text, rule, varMap)` 仍然可用，它们会读取全局默认输出注册器。若只想影响单次调用，请先 `copy()` 再传入。

## 10. 防御性能力

当前实现已经覆盖以下防御场景：

- 超深包裹括号：`((((...))))`
- 超深正常算术嵌套：`(1 + (2 + (3 + ... )))`
- 超深布尔嵌套：`a == b || (c == d || (...))`
- 引号内的 `,`、`&&`、`||`、括号不会被误判为结构符号
- 单个 `&`、`|` 不会再误吞 `&&`、`||`
- 层级超限时快速失败
- 非法数字格式
- 括号不匹配
- 缺失变量
- 除零
- 非法成员访问/方法调用

其中 `IterativeExpressionCalculator` 对深层输入的防御能力更强。

## 11. 常见异常

| 场景 | 示例异常 |
| --- | --- |
| 空表达式 | `表达式不能为空` |
| 深度超限 | `表达式层级超过限制: N` |
| 括号错误 | `括号不匹配` |
| 数字非法 | `数字格式错误` |
| 缺失变量 | `变量不存在: x` |
| 除零 | `除数不能为0` |
| 非整数位运算 | `位运算只支持整数: x` |
| 字段不存在 | `字段访问失败，不存在公开字段: Xxx.field` |
| 字段对象为空 | `字段访问失败: 对象为空, 字段: xxx` |
| 输出配置错误 | `不支持的 byte[] 输出模式` / `不支持的时区` |
| 方法对象为空 | `方法调用失败: 对象为空, 方法: xxx` |
| 方法类型不匹配 | `方法调用失败，参数类型不匹配: Xxx.method` |

## 12. 选择建议

- 如果重点是**阅读、教学、理解语法**：使用 `RecursiveExpressionCalculator`
- 如果重点是**稳定性、深层输入、防恶意嵌套**：使用 `IterativeExpressionCalculator`
