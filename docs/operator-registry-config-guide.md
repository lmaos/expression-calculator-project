# OperatorRegistry 配置与扩展说明

## 简介

`OperatorRegistry` 是 expression-calculator 的运算符注册中心，统一管理所有一元/二元运算符的符号、优先级、结合性和求值逻辑。所有表达式引擎均通过注册表获取运算符定义，支持灵活扩展和自定义。

## 默认注册运算符

- 算术：`+` `-` `*` `/` `%` `**`
- 位运算：`~` `<<` `>>` `>>>` `<<<` `&` `|` `^`
- 比较：`==` `!=` `>` `<` `>=` `<=`
- 逻辑：`!`（一元），`&&`/`||`（短路，专用实现，禁止注册）

优先级和结合性见下表：

| 优先级 | 运算符 | 结合性 |
| --- | --- | --- |
| 10 | 一元 `+` `-` `~` `!` | 右 |
| 9 | `**` | 右 |
| 8 | `*` `/` `%` | 左 |
| 7 | `+` `-` | 左 |
| 6 | `<<` `>>` `>>>` `<<<` | 左 |
| 5 | `>` `<` `>=` `<=` | 左 |
| 4 | `&` | 左 |
| 3 | `^` | 左 |
| 2 | `|` | 左 |
| 1 | `==` `!=` | 左 |

## 配置与扩展


### 注册防御
- 禁止注册 `&&`/`||` 作为普通运算符。
- 禁止空白符号。

### 恢复默认
测试或多场景下可用 `resetToDefaults()` 恢复内置运算符。

## 运行时行为
- 所有默认运算符的求值逻辑委托 `ExpressionRuntimeSupport`，如加法、幂、位运算等。
- 支持一元/二元同符号（如 `-`），解析器自动区分。
- 运算符匹配优先长符号，避免 `>>`/`>>>`/`>=` 冲突。

## 代码示范（单元测试覆盖）
下面示例都在 `OperatorExtensionTest` 中有对应断言，文档仅记录这些已被单元测试验证的用法：

- 使用 ExpressionCalculator 求值（示例来自测试）：

```java
Map<String, Object> vars = new HashMap<>();
vars.put("a", 1);
vars.put("b", 2);
vars.put("c", 3);
ExpressionCalculator calculator = new IterativeExpressionCalculator();

String mod = calculator.calculation("10 % 3", vars); // "1"
String pow = calculator.calculation("2 ** 3", vars); // "8"
boolean powAssoc = calculator.compareCalculation("2 ** 3 ** 2 == 512", vars); // true
```

- 注册防御示例（测试验证会抛出 IllegalArgumentException）：

```java
OperatorRegistry registry = OperatorRegistry.getInstance();

// 拒绝注册短路逻辑运算符 &&
try {
    registry.registerBinary("&&", 1, Associativity.LEFT, (left, right) -> RuntimeValue.computed(true));
} catch (IllegalArgumentException e) {
    // e.getMessage() -> "逻辑短路运算符不能通过普通运算符注册: &&"
}

// 拒绝空白运算符符号
try {
    registry.registerBinary(" ", 1, Associativity.LEFT, (left, right) -> RuntimeValue.computed(true));
} catch (IllegalArgumentException e) {
    // e.getMessage() -> "运算符符号不能为空"
}
```

- 恢复默认运算符（测试在 setUp/tearDown 中使用）：

```java
OperatorRegistry.getInstance().resetToDefaults();
```

## 单元测试示例
详见 `OperatorExtensionTest`：
- 验证默认运算符（包括 `%` 和 `**`）的行为与优先级
- 验证注册防御（拒绝 `&&`/`||` 和空白符号）
- 验证异常分支（如除以 0、表达式格式错误）以及 resetToDefaults()

## 参考
- [README_EXTENSIBILITY.md](../expression-calculator/README_EXTENSIBILITY.md)
- [operator-registry-refactor-report.md](../docs/operator-registry-refactor-report.md)
- [ExpressionRuntimeSupport.java](../src/main/java/com/clmcat/commons/calculator/ExpressionRuntimeSupport.java)
- [OperatorExtensionTest.java](../src/test/java/com/clmcat/commons/calculator/OperatorExtensionTest.java)
