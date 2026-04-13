# 表达式计算器优化、扩展与验证报告

## 1. 本轮分析方法

本轮不是直接重写解析器，而是先做可验证的分层分析，再按低风险顺序落地：

1. **基线验证**：先执行现有 `mvn test`，确认当前实现和测试集稳定，再决定改动边界。
2. **重复路径梳理**：对递归版、迭代版、布尔引擎逐个比对，锁定重复最明显且扩展成本最高的部分：
   - 数字字面量解析
   - 标识符解析
   - 比较运算符识别
   - 方法参数拆分与括号匹配
   - 布尔边界扫描
3. **扩展影响分析**：判断如果加入字符串/字符字面量，哪些扫描逻辑会失效。结论是：只改 `parsePrimary` 不够，`&&`/`||` 边界识别、逗号拆分、括号匹配都必须具备“忽略引号内部内容”的能力。
4. **增量重构决策**：优先抽取共享文本解析工具，而不是直接重写成统一基类解析器，原因是当前测试对行为和异常文本已有约束，增量改造更容易保持兼容。
5. **分阶段验证**：按“全量功能 -> 边界/防御 -> 最终回归”三轮执行测试，避免只验证新增 happy path。

## 2. 本轮实际落地内容

### 2.1 提取共享解析支持

新增 `ExpressionTextSupport`，统一承载：

- 标识符解析
- 数字字面量解析
- 字符串/字符字面量解析
- 比较运算符定义
- 引号感知的括号匹配
- 引号感知的方法参数拆分
- 引号感知的扫描跳过

这样递归版与迭代版不再各自维护一套数字/标识符解析细节，后续若继续增加新字面量或扫描规则，改动点明显收敛。

### 2.2 扩展字符串与字符字面量

两个值表达式引擎现在都支持：

- 双引号字符串：`"hello"`
- 单引号字符：`'A'`
- 转义：`\n`、`\t`、`\r`、`\\`、`\"`、`\'`

同时支持这些字面量出现在：

- 直接计算结果中
- 比较表达式中
- 方法调用参数中
- 带有逗号、括号、`&&`、`||` 的文本片段中

### 2.3 增强引号感知扫描

为了让新增字面量真正可用，本轮同步增强了：

- `IterativeExpressionEngine` 的参数拆分与右括号定位
- `IterativeBooleanExpressionEngine` 的布尔边界扫描
- 顶层比较表达式拆分
- 布尔短路时的剩余片段跳过

现在引号内部的 `,`、`(`、`)`、`&&`、`||` 不会再被误认为结构符号。

### 2.4 局部优化与冗余清理

- `BeanUtils.findPublicMethods` 改为 `computeIfAbsent`，避免并发场景下重复构建方法缓存。
- 删除未被使用的 `stripRedundantOuterParentheses`，减少维护噪音。
- `ExpressionRuntimeSupport.add` 增加字符串式拼接支持：当操作数包含非数值字符串/字符时，`+` 走拼接；两侧都能识别为数字时仍保持数值加法语义。
- `compareCalculation` 继续保持原有约束：字符串和字符不能直接作为最终布尔结果，必须配合比较运算符使用。

### 2.5 补充修复：缺失变量与 null 比较

在后续测试中发现：

- `notVar == null` 预期应为 `true`
- `price != null` 预期应为 `true`

失败原因是两个实现都会在“读取变量”阶段直接抛出 `变量不存在`，比较逻辑根本没有机会执行。

本次修复采用了更细粒度的策略：

- 缺失变量被包装为内部“缺失变量值”
- 仅在 `==` / `!=` 的 null 等值比较中，将缺失变量按 `null` 参与判断
- 在算术、方法调用、大小比较、独立布尔判断等其它路径中，仍保持 `变量不存在` 异常

这样既满足 `notVar == null` / `notVar != null` 这类判断，又不会把缺失变量悄悄放宽成普通值。

## 3. 主要修改文件

- `expression-calculator\src\main\java\com\clmcat\commons\calculator\ExpressionTextSupport.java`
- `expression-calculator\src\main\java\com\clmcat\commons\calculator\IterativeExpressionEngine.java`
- `expression-calculator\src\main\java\com\clmcat\commons\calculator\RecursiveExpressionEngine.java`
- `expression-calculator\src\main\java\com\clmcat\commons\calculator\IterativeBooleanExpressionEngine.java`
- `expression-calculator\src\main\java\com\clmcat\commons\calculator\ExpressionRuntimeSupport.java`
- `expression-calculator\src\main\java\com\clmcat\commons\calculator\BeanUtils.java`

新增测试：

- `expression-calculator\src\test\java\com\clmcat\commons\calculator\ExpressionCalculatorLiteralSupportTest.java`
- `expression-calculator\src\test\java\com\clmcat\commons\calculator\ExpressionCalculatorLiteralBoundaryTest.java`

## 4. 三阶段测试执行结果

### 第 1 步：现有单元测试 + 新功能单元测试

命令：

```powershell
mvn test
```

结果：

- 共 `144` 项测试
- `0` failure / `0` error / `0` skipped

### 第 2 步：边界测试、安全使用测试

命令：

```powershell
mvn "-Dtest=ExpressionCalculatorTest,ExpressionCalculatorBoundaryRegressionTest,ExpressionCalculatorDepthLimitAndBooleanDefenseTest,ExpressionCalculatorGeneratedEdgeCaseTest,ExpressionCalculatorLiteralBoundaryTest,IterativeExpressionCalculatorDeepNestingTest,IterativeExpressionCalculatorSpecialTypesTest" test
```

结果：

- 共 `132` 项测试
- `0` failure / `0` error / `0` skipped

### 第 3 步：思考潜在问题后的最终回归

本轮额外关注的潜在问题：

- 引号中的 `&&` / `||` 是否会误触发布尔拆分
- 引号中的逗号是否会误拆方法参数
- 转义引号是否会导致扫描提前结束
- 新增字符字面量后，是否破坏 compareCalculation 的真值规则
- 字符串拼接能力是否会影响原有“数字字符串 + 数字”的数值求和语义
- 缺失变量是否只在 `null` 等值比较中被放宽，而不会影响其它错误路径

命令：

```powershell
mvn test
```

结果：

- 共 `144` 项测试
- `0` failure / `0` error / `0` skipped

## 6. 提交与变更记录

- 保护性提交：`a618a94` `Refactor parser support and add literal coverage`
- 本次继续修复了缺失变量的 `null` 比较语义，并补充了对应测试与文档

## 5. 后续可继续扩展的方向

基于这次抽出的共享解析支持，后续如果要继续增强，可以优先考虑：

1. 增加更多字面量类型或转义规则
2. 扩展新的运算符（如 `%`）
3. 继续收敛递归版/迭代版在扫描层的重复逻辑
4. 进一步把运算符定义抽成统一注册表
