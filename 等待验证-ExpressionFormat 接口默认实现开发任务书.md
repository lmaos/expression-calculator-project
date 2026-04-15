# ExpressionFormat 接口默认实现开发任务书

## 一、目标

实现 `ExpressionFormat` 接口的默认实现类 `DefaultExpressionFormat`，提供字符串模板格式化能力。用户可将文本中的占位符（如 `${expression}`）替换为表达式求值结果，支持自定义占位符规则和转义。

## 二、接口定义（已确定，不可修改）

```java
public interface ExpressionFormat {
    default String format(String text, Map<String, Object> varMap) {
        return format(text, "${?}", varMap);
    }
    String format(String text, String rule, Map<String, Object> varMap);
}
```

## 三、功能需求详述

### 3.1 核心功能：占位符替换

- 输入文本中匹配 `rule` 定义的模式，提取 `?` 对应位置的表达式。
- 调用 `ExpressionCalculator.evaluate(expression, varMap)` 求值。
- 将求值结果转换为字符串并替换原占位符。
- 支持同一文本中出现多个占位符，全部替换。

**示例：**
```
format("1 + 2 = ${1 + 2}", map) → "1 + 2 = 3"
format("Hello ${name}", {name:"World"}) → "Hello World"
format("${list.size()}"), {list: [1,2,3]}) → "list.size() = 3"
format("${arrays.length}"), {arrays: new int[]{1,2,3}}) → "arrays.length = 3")
format("${'ab' + 'cd'}", map) → "abcd"
format("${1==1}", map) → "true"
format("${list[0]}", {list: ["first", "second"]}) → "first"
format("${arrays[0]}", {arrays: new int[]{10,20}}) → "10"
format("${map['key1']}", {map: Map.of("key1", "value1")}) → "value1"
format("${return.value}", {map: {"return.value", "success"}}) → "success"


```

### 3.2 自定义规则解析

- `rule` 参数必须包含且仅包含一个 `?` 字符。
- 将 `rule` 按第一个 `?` 切分为 `prefix` 和 `suffix`。
- 支持任意字符作为边界，如 `"#{?}"`、`"{{?}}"`、`"@@?@@"`。

**异常处理：**
- 若 `rule == null` 或不包含 `?`，抛出 `IllegalArgumentException`，消息明确。
- 若 `rule` 包含多个 `?`，仅以第一个为分隔，其余视为 `suffix` 的一部分（或严格抛异常，本任务采用抛异常以保证预期清晰）。

### 3.3 结果字符串转换规则

- 若 `evaluate()` 返回 `null`，替换为空字符串 `""`。
- 若返回非 `null`，调用 `String.valueOf(result)` 转换。

### 3.4 转义支持

- 当占位符的 `prefix` 前紧邻一个反斜杠 `\` 时，该占位符不被解析，而是作为普通文本输出。
- 处理方式：在结果中追加 `prefix`（移除反斜杠），并继续向后扫描。

**示例：**
```
format("\\${1+2}", map) → "${1+2}"
format("a\\${b}", {b:3}) → "a${b}"
```

### 3.5 嵌套占位符处理策略

- **明确声明：不支持占位符嵌套。**
- 若用户输入 `${name_${index}}`，`ExpressionCalculator.evaluate()` 会因表达式含非法字符 `$` 或 `{` 而抛出 `IllegalArgumentException`。
- 实现类无需特殊处理嵌套，只需将内部表达式原样传递给计算器即可。
- 如需动态构建变量名，应使用表达式引擎支持的语法，例如：
    - `map['name_' + index]`
    - 或调用方法 `getValue('name_' + index)`

### 3.6 异常处理与边界情况

| 场景 | 预期行为 |
|------|----------|
| 占位符未闭合（如 `"${1+2"`） | 抛出 `IllegalArgumentException`，提示未找到匹配的后缀 |
| 表达式求值异常（如 `"${1/0}"`） | 异常直接向上抛出（`ArithmeticException`），不做捕获 |
| 表达式语法错误（如 `"${1+}"`） | 抛出 `IllegalArgumentException` |
| 输入文本为 `null` | 可抛出 `NullPointerException` 或 `IllegalArgumentException`（本任务采用后者） |
| 变量上下文 `varMap` 为 `null` | 视为空 Map（或抛异常，二选一，本任务采用视作空 Map） |
| 空文本 `""` | 返回 `""` |
| 文本无占位符 | 返回原文本 |

### 3.7 性能与线程安全

- 使用 `StringBuilder` 拼接结果，避免频繁创建字符串对象。
- 使用 `String.indexOf()` 进行扫描，不使用正则表达式。
- 实现类应为无状态的（仅持有 `ExpressionCalculator` 引用），天然线程安全。

## 四、技术实现要点

### 4.1 类结构

```java
package com.clmcat.commons.calculator;

import java.util.Map;
import java.util.Objects;

public class DefaultExpressionFormat implements ExpressionFormat {

    private final ExpressionCalculator calculator;

    public DefaultExpressionFormat(ExpressionCalculator calculator) {
        this.calculator = Objects.requireNonNull(calculator);
    }

    @Override
    public String format(String text, String rule, Map<String, Object> varMap) {
        // 实现逻辑
    }
}
```

### 4.2 算法伪代码

```
function format(text, rule, varMap):
    if text == null: throw IllegalArgumentException
    if rule == null or !rule.contains("?"): throw IllegalArgumentException
    
    prefix = rule.substring(0, rule.indexOf('?'))
    suffix = rule.substring(rule.indexOf('?') + 1)
    
    result = new StringBuilder()
    pos = 0
    
    while pos < text.length():
        start = text.indexOf(prefix, pos)
        if start == -1:
            result.append(text, pos, text.length())
            break
        
        // 检查转义：start > 0 且前一个字符是 '\'
        if start > 0 && text.charAt(start - 1) == '\\':
            result.append(text, pos, start - 1).append(prefix)
            pos = start + prefix.length()
            continue
        
        result.append(text, pos, start)
        exprStart = start + prefix.length()
        end = text.indexOf(suffix, exprStart)
        if end == -1:
            throw new IllegalArgumentException("未找到匹配的后缀: " + suffix)
        
        expr = text.substring(exprStart, end)
        value = calculator.evaluate(expr, varMap)
        String replacement = value == null ? "" : String.valueOf(value)
        result.append(replacement)
        pos = end + suffix.length()
    
    return result.toString()
```

### 4.3 注意事项

- 使用 `Map<String, Object> varMap` 时若为 `null`，内部转换为 `Collections.emptyMap()` 传递给计算器。
- 表达式首尾可能包含空白字符，建议在求值前调用 `expr.trim()`。

## 五、单元测试计划（JUnit 5）

### 5.1 测试类结构

```java
class DefaultExpressionFormatTest {
    private ExpressionCalculator calculator;
    private ExpressionFormat formatter;
    private Map<String, Object> variables;

    @BeforeEach
    void setUp() {
        calculator = new IterativeExpressionCalculator();
        formatter = new DefaultExpressionFormat(calculator);
        variables = new HashMap<>();
        // 填充测试数据
    }
    // 测试方法...
}
```

### 5.2 测试用例清单

| 分类 | 测试方法 | 输入 | 预期 |
|------|----------|------|------|
| **基础** | `testSimpleExpression` | `"1+2=${1+2}"` | `"1+2=3"` |
| | `testSingleVariable` | `"Hello ${name}"` (name="World") | `"Hello World"` |
| | `testMultiplePlaceholders` | `"${a} + ${b} = ${a+b}"` (a=5,b=3) | `"5 + 3 = 8"` |
| | `testStringConcat` | `"${'ab' + 'cd'}"` | `"abcd"` |
| | `testBooleanResult` | `"${1==1}"` | `"true"` |
| | `testNullValue` | `"${nullable}"` (nullable=null) | `""` |
| | `testMissingVariable` | `"${missing}"` | `""` |
| | `testNoPlaceholder` | `"plain text"` | `"plain text"` |
| | `testEmptyText` | `""` | `""` |
| **自定义规则** | `testCustomRuleHash` | `"#{?}"`, `"#{1+2}"` | `"3"` |
| | `testCustomRuleDoubleBrace` | `"{{?}}"`, `"{{a}}"` (a=99) | `"99"` |
| | `testRuleWithSpecialChars` | `"@@?@@"`, `"@@1+2@@"` | `"3"` |
| **转义** | `testEscapedPlaceholder` | `"\\${1+2}"` | `"${1+2}"` |
| | `testEscapedBeforeText` | `"a\\${b}"` (b=3) | `"a${b}"` |
| | `testMultipleEscapes` | `"\\${a} and \\${b}"` | `"${a} and ${b}"` |
| | `testEscapeAtStart` | `"\\${a}"` (a=1) | `"${a}"` |
| **异常** | `testUnclosedPlaceholder` | `"${1+2"` | `IllegalArgumentException` |
| | `testDivisionByZero` | `"${1/0}"` | `ArithmeticException` |
| | `testInvalidExpression` | `"${1+}"` | `IllegalArgumentException` |
| | `testNullRule` | `rule=null` | `IllegalArgumentException` |
| | `testRuleWithoutQuestionMark` | `rule="{}"` | `IllegalArgumentException` |
| | `testNullText` | `text=null` | `IllegalArgumentException` |
| **嵌套占位符行为** | `testNestedPlaceholderThrows` | `"${name_${index}}"` | 抛出异常（由计算器抛出） |

## 六、AI 自动验收标准（Checklist）

完成编码后，AI 需确保以下所有项目通过 JUnit 5 测试：

| 序号 | 验收项 | 验证方法 |
|------|--------|----------|
| 1 | 基础表达式替换正确 | 测试通过 |
| 2 | 单个变量替换正确 | 测试通过 |
| 3 | 多个占位符全部替换 | 测试通过 |
| 4 | 字符串拼接结果正确 | 测试通过 |
| 5 | 布尔结果转为 "true"/"false" | 测试通过 |
| 6 | null 值替换为空字符串 | 测试通过 |
| 7 | 缺失变量替换为空字符串 | 测试通过 |
| 8 | 无占位符文本原样返回 | 测试通过 |
| 9 | 空字符串输入返回空字符串 | 测试通过 |
| 10 | 自定义规则 `#{?}` 正常工作 | 测试通过 |
| 11 | 自定义规则 `{{?}}` 正常工作 | 测试通过 |
| 12 | 转义 `\${...}` 输出 `${...}` | 测试通过 |
| 13 | 多个转义正确处理 | 测试通过 |
| 14 | 未闭合占位符抛出 `IllegalArgumentException` | 测试通过 |
| 15 | 除零异常正常传播 | 测试通过 |
| 16 | 无效表达式抛出 `IllegalArgumentException` | 测试通过 |
| 17 | 非法 rule 参数抛出 `IllegalArgumentException` | 测试通过 |
| 18 | null text 抛出异常 | 测试通过 |
| 19 | 嵌套占位符场景抛出明确异常 | 测试通过 |
| 20 | 性能：1KB 文本含 50 个占位符在 10ms 内完成 | 性能测试 |

## 七、文档注释要求

- 在类注释中说明：
    - 占位符规则定义及示例。
    - 转义规则。
    - 不支持占位符嵌套，并给出替代方案示例。
- 在方法注释中说明参数约束和异常情况。

## 八、交付物

1. `DefaultExpressionFormat.java` 源文件。
2. `DefaultExpressionFormatTest.java` 测试源文件（JUnit 5）。
3. 确保代码符合 Java 8 标准，无新语法。

---

请严格按照本任务书生成代码，保持与现有项目相同的包结构和代码风格。完成后运行全部单元测试，确保所有验收项通过。