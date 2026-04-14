# expression-calculator

`expression-calculator` 是一个面向 Java 的表达式计算库，支持算术、比较、逻辑、位运算、变量、字面量，以及公开字段/方法访问。

## 能力

- 两种实现：`RecursiveExpressionCalculator`、`IterativeExpressionCalculator`
- 支持表达式求值：`calculation(String, Map<String, Object>)`
- 支持布尔求值：`compareCalculation(String, Map<String, Object>)`
- 支持原始值求值：`evaluate(String, Map<String, Object>)`
- 支持模板格式化：`DefaultExpressionFormat` / `ExpressionFormat`
- 支持变量、字符串/字符字面量、括号、公开字段访问和链式方法调用
- 支持 `[]` 下标访问与 `(type)` 类型转换
- 支持默认运算符：`+ - * / % ** ! ~ << >> >>> <<< & | ^`
- 整数算术结果会尽量保留 `Integer` / `Long`，非整数仍使用 `BigDecimal`

## 运算符符号表

| 分类 | 符号 | 说明 |
| --- | --- | --- |
| 算术 | `+` `-` `*` `/` `%` `**` | `**` 表示幂运算 |
| 位运算 | `~` `<<` `>>` `>>>` `<<<` `&` <code>&#124;</code> `^` | `^` 表示位异或，`<<<` 是 `<<` 的对称别名 |
| 比较 | `==` `!=` `>` `<` `>=` `<=` | 比较表达式可与算术/位运算组合 |
| 逻辑 | `!` `&&` `||` | `!` 用于单独条件/布尔结果取反，`&&`/`||` 保留短路语义 |

## 快速开始

```java
Map<String, Object> variables = new HashMap<String, Object>();
variables.put("a", 1);
variables.put("b", 2);
variables.put("c", 3);

ExpressionCalculator calculator = new IterativeExpressionCalculator();
String arithmetic = calculator.calculation("a + b * (c + 2)", variables);
boolean logic = calculator.compareCalculation("a + b > 2 && c == 3", variables);
Object raw = calculator.evaluate("a + b", variables);
```

## 新能力示例

### 原始值求值

```java
Object intValue = calculator.evaluate("1 + 2", variables);      // Integer(3)
Object longValue = calculator.evaluate("1 + 2L", variables);    // Long(3)
Object decimal = calculator.evaluate("1 + 2.0", variables);     // BigDecimal(3)
Object bool = calculator.evaluate("a < b || c == 3", variables);// Boolean.TRUE
```

### 模板格式化

```java
ExpressionFormat formatter = new DefaultExpressionFormat(calculator);
variables.put("numbers", new String[] {"a", "b", "c"});

String text = formatter.format("1 + 2 = ${1 + 2}", variables);  // 1 + 2 = 3
String escaped = formatter.format("\\${a + b}", variables);     // ${a + b}
String custom = formatter.format("#{a + b}", "#{?}", variables);// 3
String fieldText = formatter.format("len=${numbers.length}", variables); // len=3
```

`ExpressionFormat.format(...)` 始终返回 `String`，即使占位表达式的原始值是数字或布尔值。

### 公开字段与方法访问

```java
variables.put("numbers", new String[] {"a", "b", "c"});

Object length = calculator.evaluate("numbers.length", variables); // Integer(3)
String value = calculator.calculation("\"copilot\".length()", variables); // "7"
```

### 下标访问与类型转换

```java
Map<String, Object> inventory = new HashMap<String, Object>();
inventory.put("count", 3);
variables.put("inventory", inventory);
variables.put("numbers", Arrays.asList(10, 20, 30));
variables.put("index", 1);

Object item = calculator.evaluate("numbers[index]", variables);           // 20
Object count = calculator.evaluate("inventory['count']", variables);      // 3
Object casted = calculator.evaluate("(String)(inventory['count'] + 2)", variables); // "5"
```

### 自定义类型转换器

```java
ConverterRegistry converterRegistry = ConverterRegistry.getInstance();
converterRegistry.register("wrapped", value -> value == null ? null : "[" + value + "]");

Object wrapped = calculator.evaluate("(wrapped)123", variables); // "[123]"
```

## 构建与测试

进入模块目录后执行：

```powershell
cd expression-calculator
mvn test
```

## 依赖方式

先把库安装到本地 Maven 仓库：

```powershell
cd expression-calculator
mvn clean install
```

然后在你的 `pom.xml` 中加入：

```xml
<dependency>
  <groupId>com.clmcat.commons</groupId>
  <artifactId>expression-calculator</artifactId>
  <version>1.0.0</version>
</dependency>
```

如果你在多模块工程中使用，也可以直接依赖本地安装后的坐标，无需额外引入第三方依赖。

## 说明

- `**` 是幂运算符，`^` 是位异或运算符
- `!` 可用于 `compareCalculation` 中对单独变量、集合、文件或返回布尔值的方法结果做取反
- 旧的 `xor` 关键字不再作为默认符号
- `<<<` 作为 DSL 中的对称别名保留，语义与 `<<` 一致
- 所有 README 示例都保持 Java 8 兼容，不使用 `Map.of` / `List.of`
- 自定义运算符注册说明见 [README_EXTENSIBILITY.md](expression-calculator/README_EXTENSIBILITY.md)
- 其他参考：
  - [usage-guide-zh.md](docs/usage-guide-zh.md)
  - [usage-guide-en.md](docs/usage-guide-en.md)
