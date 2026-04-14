# 表达式计算器使用说明（中文）

## 1. 概述
`expression-calculator` 提供统一接口用于带变量、公开字段/方法访问的算术与逻辑表达式求值，适用于业务规则、配置、数据校验等场景。

### 主要特性
- 统一接口：`ExpressionCalculator`
- 两种实现：
  - `RecursiveExpressionCalculator`：递归，结构直观，适合教学和简单表达式
  - `IterativeExpressionCalculator`：显式栈，适合深层嵌套和生产环境
- 支持算术、比较、逻辑运算、位运算、公开字段/方法访问、下标访问、类型转换
- 支持 `evaluate(...)` 原始值求值与 `ExpressionFormat` 模板格式化
- 防御恶意或过深表达式

## 2. 环境要求
- Java 1.8 及以上
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
Map<String, Object> vars = new HashMap<String, Object>();
vars.put("a", 1);
vars.put("b", 2);
vars.put("c", 3);

String result = calc.calculation("a + b * (c + 2)", vars); // "11"
boolean ok = calc.compareCalculation("a + b > 2 && c == 3", vars); // true
Object raw = calc.evaluate("a + b", vars); // Integer(3)
```

### 3.3 设置表达式层级限制
```java
ExpressionCalculator calc = new IterativeExpressionCalculator(100); // 最大层级100
```
超限抛出：`表达式层级超过限制: 100`

### 3.4 模板格式化
```java
ExpressionFormat formatter = new DefaultExpressionFormat(calc);
vars.put("items", new String[] {"a", "b", "c"});

String text = formatter.format("1 + 2 = ${1 + 2}", vars);  // 1 + 2 = 3
String escaped = formatter.format("\\${a + b}", vars);     // ${a + b}
String custom = formatter.format("#{a + b}", "#{?}", vars);// 3
String fieldText = formatter.format("len=${items.length}", vars); // len=3
```

说明：`ExpressionFormat.format(...)` 总是返回 `String`，即使占位表达式的原始值是数字、布尔值或对象。

## 4. 支持的表达式类型

### 4.1 算术表达式（`calculation`）
- 运算符：`+ - * / % **`
- 位运算：`~ << >> >>> <<< & | ^`
- 括号：`()`
- 一元正负号：`+x -x`
- 双引号字符串 `"text"`
- 单引号字面量：单字符按 `Character` 处理，多字符按字符串处理，例如 `'A'`、`'name_'`
- 变量引用
- 公开字段/方法访问（如 `array.length`、`str.length()`）

#### 示例
```java
Map<String, Object> vars = new HashMap<String, Object>();
vars.put("price", 12.5);
vars.put("discount", 2.5);
String result = calc.calculation("price + discount", vars); // "15"
```

- `+` 遇到非数值字符串/字符时按拼接处理。
- 若两侧仍都能识别为数字，则保持数值加法语义。
- `**` 表示幂运算，`^` 表示位异或。
- 位运算只接受整数输入，按 64 位整数语义计算；`<<<` 是 `<<` 的对称别名

### 4.2 比较与逻辑表达式（`compareCalculation`）
- 比较：`== != > < >= <=`
- 逻辑：`! && ||`
- 括号
- 字符串/字符字面量参与比较
- 缺失变量在与 `null` 做 `==` / `!=` 比较时按 `null` 参与判断
- 变量直接参与真值判断（见下）
- 比较两侧可以继续包含 `%`、`**` 与位运算子表达式
- 默认内置已包含 `%`、`**`、`!`、`~`、`<<`、`>>`、`>>>`、`<<<`、`&`、`|`、`^`

说明：

- `&&` 和 `||` 仍保留短路实现，不通过普通运算符注册表扩展
- `!` 是内置一元运算符，可对单独条件或返回布尔值的方法结果取反，例如 `!file`、`!file.exists()`
- 注册示例见 `expression-calculator\README_EXTENSIBILITY.md`

#### 示例
```java
Map<String, Object> vars = new HashMap<String, Object>();
vars.put("enabled", true);
vars.put("count", 5);
boolean ok = calc.compareCalculation("enabled && count > 0", vars); // true
boolean bitwiseOk = calc.compareCalculation("(10 ^ 12) == 6", vars); // true
```

### 4.3 原始值求值（`evaluate`）
`evaluate` 返回原始对象，不做字符串格式化：

```java
Object intValue = calc.evaluate("1 + 2", vars);     // Integer(3)
Object longValue = calc.evaluate("1 + 2L", vars);   // Long(3)
Object decimal = calc.evaluate("1 + 2.0", vars);    // BigDecimal(3)
Object flag = calc.evaluate("enabled || count > 0", vars); // Boolean.TRUE
```

### 4.4 下标访问与类型转换
```java
Map<String, Object> inventory = new HashMap<String, Object>();
inventory.put("count", 3);
vars.put("inventory", inventory);
vars.put("numbers", Arrays.asList(10, 20, 30));
vars.put("index", 1);

Object listValue = calc.evaluate("numbers[index]", vars);             // 20
Object mapValue = calc.evaluate("inventory['count']", vars);          // 3
Object castValue = calc.evaluate("(String)(inventory['count'] + 2)", vars); // "5"
```

### 4.5 自定义类型转换器
```java
ConverterRegistry registry = ConverterRegistry.getInstance();
registry.register("wrapped", value -> value == null ? null : "[" + value + "]");

Object wrapped = calc.evaluate("(wrapped)123", vars); // "[123]"
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
| 其他直接变量值 | 非 null 为 true     |

直接变量会按上表参与真值判断，因此非 null 的数字/字符串/字符变量也会被视为 true。字面量和计算结果仍建议显式比较（如 `a > 0`，而不是 `a + b`）。
缺失变量若不参与 `null` 等值比较，仍会报 `变量不存在`。

## 6. 表达式中的公开字段与方法调用
- 支持公开实例字段、数组 `length`、无参/有参方法与链式调用
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

```java
public static final class Holder {
    public final String name;

    public Holder(String name) {
        this.name = name;
    }
}

vars.put("holder", new Holder("Copilot"));
vars.put("items", new String[] {"a", "b", "c"});

calc.evaluate("holder.name", vars);         // "Copilot"
calc.evaluate("items.length", vars);        // Integer(3)
calc.calculation("holder.name.length()", vars); // "7"
```

```java
calc.calculation("10 & 12", vars);      // "8"
calc.calculation("2 ** 3", vars);       // "8"
calc.calculation("3 <<< 2", vars);      // "12"
calc.compareCalculation("(10 ^ 12) == 6", vars); // true
```

## 7. 计算边界与防御能力
- 可配置最大表达式层级（构造器参数）
- 防御括号不匹配、非法数字、缺失变量、除零、非法成员访问/方法调用等
- 引号内的 `,`、`&&`、`||`、括号不会被误判为结构符号
- 单个 `&`、`|` 不会误吞 `&&`、`||`
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
| 非整数位运算     | `位运算只支持整数: x`          |
| 字段不存在       | `字段访问失败，不存在公开字段` |
| 字段对象为null   | `字段访问失败: 对象为空`       |
| 方法对象为null   | `方法调用失败: 对象为null`     |
| 方法类型不匹配   | `方法调用失败，参数类型不匹配` |

## 9. 适用范围
- 适用于带变量、公开字段/方法访问、深层嵌套的算术与逻辑表达式
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

### 10.3 模板与动态 key
当前 `ExpressionFormat` 不支持占位符嵌套，推荐把动态 key 写在表达式内部：

```java
Map<String, Object> dynamicMap = new HashMap<String, Object>();
dynamicMap.put("name_1", "Copilot");
vars.put("dynamicMap", dynamicMap);
vars.put("index", 1);

String text = formatter.format("${dynamicMap['name_' + index]}", vars); // Copilot
```

---
