# expression-calculator

`expression-calculator` 是一个面向 Java 的表达式计算库，支持算术、比较、逻辑、位运算、变量、字面量和方法调用。

## 能力

- 两种实现：`RecursiveExpressionCalculator`、`IterativeExpressionCalculator`
- 支持表达式求值：`calculation(String, Map<String, Object>)`
- 支持布尔求值：`compareCalculation(String, Map<String, Object>)`
- 支持变量、字符串/字符字面量、括号和链式方法调用
- 支持默认运算符：`+ - * / % ** ~ << >> >>> <<< & | ^`

## 运算符符号表

| 分类 | 符号 | 说明 |
| --- | --- | --- |
| 算术 | `+` `-` `*` `/` `%` `**` | `**` 表示幂运算 |
| 位运算 | `~` `<<` `>>` `>>>` `<<<` `&` <code>&#124;</code> `^` | `^` 表示位异或，`<<<` 是 `<<` 的对称别名 |
| 比较 | `==` `!=` `>` `<` `>=` `<=` | 比较表达式可与算术/位运算组合 |
| 逻辑 | `&&` `||` | 保留短路语义，不通过普通运算符注册表扩展 |

## 快速开始

```java
ExpressionCalculator calculator = new IterativeExpressionCalculator();
Map<String, Object> variables = Map.of("a", 1, "b", 2, "c", 3);

String arithmetic = calculator.calculation("a + b * (c + 2)", variables);
boolean logic = calculator.compareCalculation("a + b > 2 && c == 3", variables);
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
- 旧的 `xor` 关键字不再作为默认符号
- `<<<` 作为 DSL 中的对称别名保留，语义与 `<<` 一致
- 自定义运算符注册说明见 [README_EXTENSIBILITY.md](expression-calculator/README_EXTENSIBILITY.md)
- 其他参考: docs/usage-guide.md

[usage-guide-zh.md](docs/usage-guide-zh.md)

[usage-guide-en.md](docs/usage-guide-en.md)