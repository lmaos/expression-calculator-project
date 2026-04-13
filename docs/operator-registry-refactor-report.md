# 运算符注册机制重构报告

## 1. 目标与边界

本轮任务的目标是把运算符的符号、优先级、结合性和求值逻辑从两个表达式引擎内部抽离出来，收敛到统一的注册表中，同时保证：

1. 默认行为不变，现有测试继续通过。
2. 新增 `%`、`^`、位运算符等运算符时，不再需要改动引擎内部逻辑。
3. 重构完成并验证通过后，删掉旧的硬编码与重复拆分逻辑。


## 2. 可行性分析

### 2.1 对现有测试的兼容性

兼容风险主要来自三个点：

1. 默认运算符的优先级或结合性被改坏。
2. 比较表达式与短路逻辑的边界被改坏。
3. 既有异常文本发生变化，触发现有精确断言。

本次通过以下方式控制风险：

- 在 `OperatorRegistry` 启动时预注册当前全部默认运算符。
- 默认求值逻辑继续直接复用 `ExpressionRuntimeSupport`，不重写算术/比较语义。
- `&&` / `||` 继续保留专门实现，不混入普通运算符注册表。
- 新增了单字符 `&` / `|` 与 `&&` / `||` 的匹配隔离，避免位运算误吞逻辑短路前缀。

### 2.2 性能影响

从硬编码 `switch` / 枚举切到注册表后，新增的开销主要有两类：

1. 注册阶段的 `ConcurrentHashMap` 维护。
2. 求值阶段的运算符快照匹配与函数式调用。

实际影响较小，原因是：

- 注册阶段只发生在初始化或显式扩展时，不在每次表达式求值路径上。
- 运行时使用的是按符号长度排序后的不可变快照列表，默认运算符数量很小。
- 相比表达式求值本身，函数式分发的额外成本可以忽略。
- 迭代引擎仍然保持显式栈结构；深层嵌套场景的核心性能特征没有退化。

### 2.3 数值与位运算语义说明

- 注册表 API 只依赖 JDK 自带的 `Function` / `BiFunction`。
- 对幂运算（`**`），优先走 `BigDecimal.pow(int)`；对于非整数或不适合 `pow(int)` 的指数，回退到 `Math.pow(double, double)`。
- 位运算按 **64 位整数** 语义执行，非整数输入会显式报错。
- `**` 在本项目中表示幂运算，`^` 用于位异或。
- `<<<` 作为 DSL 中的“无符号左移”别名保留，但在 64 位定长位模式下其效果与 `<<` 一致。

### 2.4 递归引擎改造思路

原始递归引擎依赖固定层级：`comparison -> additive -> multiplicative -> unary -> primary`。这对动态注册很不友好，因为每新增一个优先级都要补新的 `parseXxx()` 层。

本轮改成了通用优先级解析器 `parseValueExpression(minimumPrecedence)`：

1. 前缀位置读取一元运算符或 `primary`。
2. 中缀位置从注册表里取当前可匹配的二元运算符。
3. 按优先级与结合性递归解析右侧表达式。

这样不再需要让 `parseAdditive()` 手工维护“所有优先级为 3 的运算符”列表；注册表本身就是唯一事实来源。

### 2.5 多个运算符共享符号时的处理

`-` 同时是一元和二元运算符，这是本轮必须解决的冲突点。处理方式如下：

- `OperatorRegistry` 将一元和二元运算符存放在**两套独立映射/快照**里。
- 解析器根据上下文选择匹配来源：
  - 期待操作数时，只匹配一元运算符。
  - 期待运算符时，只匹配二元运算符。
- 同类运算符内部按“**符号长度优先**”匹配，保证 `>>>`、`<<<`、`>=` 优先于较短前缀。
- `&&` / `||` 被禁止通过普通注册表注册，避免与短路控制流冲突。

## 3. 实际实施内容

### 3.1 新增统一扩展模型

新增：

- `Associativity`
- `RuntimeValue`
- `Operator`
- `OperatorRegistry`

其中：

- `RuntimeValue` 从内部实现细节提升为可复用类型，供自定义运算符直接返回结果。
- `OperatorRegistry` 统一管理默认运算符与扩展运算符。

### 3.2 迭代引擎改造

`IterativeExpressionEngine` 已完成以下替换：

- 删除内部 `Operator` 枚举。
- 一元/二元运算符均从注册表匹配。
- 入栈规约逻辑改为读取 `precedence` + `associativity`。
- 运算符执行改为直接调用 `Operator.apply(...)`。

### 3.3 递归引擎改造

`RecursiveExpressionEngine` 已从固定层级解析改为通用优先级解析器：

- 逻辑层 `&&` / `||` 仍单独保留。
- 值表达式层统一按注册表解析任意已注册的一元/二元运算符。
- 新增运算符无需再补新的 `parseComparison` / `parseAdditive` / `parseMultiplicative` 分支。

### 3.4 冗余逻辑清理

在重构完成并通过测试后，本轮同步移除了以下冗余：

1. `IterativeExpressionEngine` 的本地硬编码运算符枚举。
2. `RecursiveExpressionEngine` 中对比较/加减/乘除的分散硬编码识别。
3. `ExpressionTextSupport` 中硬编码比较运算符列表与 `readComparisonOperator(...)`。
4. `IterativeBooleanExpressionEngine` 中额外维护的一份顶层比较拆分逻辑，改为直接复用值表达式引擎。
5. `ExpressionRuntimeSupport.toBigDecimal(...)` 中重复的 `BigDecimal` 分支判断。

### 3.5 默认运算符扩展

本轮继续把原先示例级的 `%`、`**` 升级为默认能力，并新增以下默认符号：

- 幂/取模：`**`、`%`
- 位取反：`~`
- 移位：`<<`、`>>`、`>>>`、`<<<`
- 位与/位或/位异或：`&`、`|`、`^`

其中幂运算使用 `**`，`^` 专用于位异或。

## 4. 新增测试覆盖

新增/更新测试覆盖包括：

- `ExpressionCalculatorTest` 中新增默认幂运算与取模断言，包括 `(2 ** 3) ** 2 == 64`。
- `OperatorExtensionTest` 校验 `%`、`**` 默认化后仍保持可扩展模型和注册防御。
- `ExpressionCalculatorBitwiseOperatorTest` 覆盖 `~`、`<<`、`>>`、`>>>`、`<<<`、`&`、`|`、`^`、优先级与非整数报错。
- 既有布尔逻辑测试继续覆盖 `&&` / `||` 与单个 `&` / `|` 的边界隔离。

## 5. 三阶段验证结果

### 第 1 步：现有单元测试 + 新功能测试

命令：

```powershell
mvn test
```

结果：

- 共 `166` 项测试
- `0` failure / `0` error / `0` skipped

### 第 2 步：边界测试、安全使用测试

命令：

```powershell
mvn "-Dtest=ExpressionCalculatorTest,ExpressionCalculatorBoundaryRegressionTest,ExpressionCalculatorBitwiseOperatorTest,ExpressionCalculatorDepthLimitAndBooleanDefenseTest,ExpressionCalculatorGeneratedEdgeCaseTest,ExpressionCalculatorLiteralBoundaryTest,IterativeExpressionCalculatorDeepNestingTest,IterativeExpressionCalculatorSpecialTypesTest,OperatorExtensionTest" test
```

结果：

- 共 `154` 项测试
- `0` failure / `0` error / `0` skipped

### 第 3 步：最终回归测试

命令：

```powershell
mvn test
```

结果：

- 共 `166` 项测试
- `0` failure / `0` error / `0` skipped

## 6. 关键行为说明

1. 默认内置现已包含 `%`、`**`、`~`、`<<`、`>>`、`>>>`、`<<<`、`&`、`|`、`^`。
2. `**` 表示幂运算；`^` 表示位异或。
3. 位运算只接受整数输入，返回 64 位整数结果；`<<<` 是 `<<` 的 DSL 对称别名。
4. `calculation(...)` 对 `BigDecimal` 结果仍会去掉尾随零，因此幂运算回退到浮点后若得到 `BigDecimal("2.0")`，最终字符串结果仍是 `"2"`。
5. 扩展说明文档已写入：`expression-calculator\README_EXTENSIBILITY.md`。
