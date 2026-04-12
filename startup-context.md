# Startup Context - expression-calculator

## 用途

这个文件放在当前工作目录，供**下次会话启动时优先读取**，快速回忆当前任务状态。

## 当前项目快照

- 工作目录：`D:\zxy\ws\ai\copilot\chat-v1`
- 目标项目：`D:\zxy\ws\ai\copilot\coding-v1\expression-calculator`
- 分支：`feature/comparable-compare`
- 当前 HEAD：`1beebf184431f6950ab0bdc7fdd38884948f6d69`
- 最近关键里程碑：`3155716`、`aa01af6`

## 已完成里程碑

1. 重建递归版与迭代版表达式计算器，并统一语义。
2. 支持变量、方法调用、链式调用、真值规则与比较逻辑。
3. 增加教学型注释。
4. 迭代版算术求值改为显式栈。
5. 迭代版布尔求值改为显式栈。
6. 两个实现均支持可选层级限制构造器。
7. 已补充深层嵌套、深层布尔防御、边界与层级限制测试。

## 关键文件

- `D:\zxy\ws\ai\copilot\coding-v1\expression-calculator\src\main\java\com\example\calculator\RecursiveExpressionCalculator.java`
- `D:\zxy\ws\ai\copilot\coding-v1\expression-calculator\src\main\java\com\example\calculator\RecursiveExpressionEngine.java`
- `D:\zxy\ws\ai\copilot\coding-v1\expression-calculator\src\main\java\com\example\calculator\IterativeExpressionCalculator.java`
- `D:\zxy\ws\ai\copilot\coding-v1\expression-calculator\src\main\java\com\example\calculator\IterativeExpressionEngine.java`
- `D:\zxy\ws\ai\copilot\coding-v1\expression-calculator\src\main\java\com\example\calculator\IterativeBooleanExpressionEngine.java`
- `D:\zxy\ws\ai\copilot\coding-v1\expression-calculator\src\main\java\com\example\calculator\ExpressionRuntimeSupport.java`

## 下次启动建议读取顺序

1. 当前文件：`startup-context.md`
2. `docs\task-status.md`
3. `docs\usage-guide.md`

## 维护约定

- 以后每次你提醒“记录当前任务/版本/日志”时，更新这个文件。
- 如果关键提交、分支、目标项目或已完成里程碑发生变化，也同步更新这里。
