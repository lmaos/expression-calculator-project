# Expression Calculator Usage Guide (English)

## 1. Overview
The `expression-calculator` library provides a unified interface for evaluating arithmetic and logical expressions with variables and public field/method access. It is suitable for business rule evaluation, configuration, and data validation scenarios.

### Main Features
- Unified interface: `ExpressionCalculator`
- Two implementations:
  - `RecursiveExpressionCalculator`: Recursive, readable, ideal for learning and simple expressions
  - `IterativeExpressionCalculator`: Stack-based, robust for deep nesting and production
- Supports arithmetic, comparison, logical, bitwise operations, public field/method access, indexing, and casts
- Supports raw-value `evaluate(...)` and template formatting through `ExpressionFormat`
- Defensive against malicious or overly deep expressions

## 2. Environment Requirements
- Java 1.8 or higher
- Maven 3.8 or higher

## 3. Quick Start

### 3.1 Add Dependency
Add to your Maven `pom.xml`:
```xml
<dependency>
  <groupId>com.clmcat.commons</groupId>
  <artifactId>expression-calculator</artifactId>
  <version>YOUR_VERSION</version>
</dependency>
```

### 3.2 Basic Usage
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

### 3.3 Set Expression Depth Limit
```java
ExpressionCalculator calc = new IterativeExpressionCalculator(100); // max depth 100
```
Exceeding the limit throws: `Expression depth limit exceeded: 100`

### 3.4 Template Formatting
```java
ExpressionFormat formatter = new DefaultExpressionFormat(calc);
vars.put("items", new String[] {"a", "b", "c"});

String text = formatter.format("1 + 2 = ${1 + 2}", vars);  // 1 + 2 = 3
String escaped = formatter.format("\\${a + b}", vars);     // ${a + b}
String custom = formatter.format("#{a + b}", "#{?}", vars);// 3
String fieldText = formatter.format("len=${items.length}", vars); // len=3
```

`ExpressionFormat.format(...)` always returns a `String`, even when the placeholder evaluates to a number, boolean, or object.

If you need type-specific output, pass an `OutputFormatRegistry` as the 4th argument:

```java
OutputFormatRegistry registry = OutputFormatRegistry.getInstance().copy();
registry.setOption(byte[].class, "mode", "base64");
registry.setOption(File.class, "mode", "content");
registry.setOption(File.class, "charset", "UTF-8");
registry.setOption(Date.class, "pattern", "yyyyMMdd");
registry.setOption(Date.class, "timeZone", "UTC");

String customText = formatter.format(
        "bytes=${payload}|file=${file}|date=${createdAt}",
        "${?}",
        vars,
        registry);
```

Built-in output options:

| Type | Options |
| --- | --- |
| `byte[]` | `mode=text/base64/hex`, `charset` |
| `File` | `mode=path/name/content`, `charset` |
| `Date` | `pattern`, `timeZone` |

The legacy 2/3-argument `format(...)` overloads still use the global default registry. For per-call isolation, prefer `copy()` and pass the cloned registry explicitly.

## 4. Supported Expression Types

### 4.1 Arithmetic Expressions (`calculation`)
- Operators: `+ - * / % **`
- Bitwise operators: `~ << >> >>> <<< & | ^`
- Parentheses: `()`
- Unary plus/minus: `+x -x`
- Double-quoted strings `"text"`
- Single-quoted literals: one character stays `Character`, multi-character text becomes `String`, such as `'A'` and `'name_'`
- Variable references
- Public field/method access (for example `array.length` and `str.length()`)

#### Example
```java
Map<String, Object> vars = new HashMap<String, Object>();
vars.put("price", 12.5);
vars.put("discount", 2.5);
String result = calc.calculation("price + discount", vars); // "15"
```

- `+` performs string concatenation when a non-numeric string/character is involved.
- If both operands can still be interpreted as numbers, addition keeps numeric semantics.
- `**` means power, and `^` means bitwise xor.
- Bitwise operators accept only integral operands and use 64-bit integer semantics. `<<<` is a DSL alias of `<<`.

### 4.2 Comparison & Logical Expressions (`compareCalculation`)
- Comparisons: `== != > < >= <=`
- Logical: `! && ||`
- Parentheses
- String and character literals in comparisons
- Missing variables are treated as `null` only for `== null` / `!= null` checks
- Direct variable truthiness (see below)
- Arithmetic and bitwise subexpressions can appear on either side of comparisons
- Built-in defaults already include `%`, `**`, `!`, `~`, `<<`, `>>`, `>>>`, `<<<`, `&`, `|`, and `^`

Notes:

- `&&` and `||` keep their dedicated short-circuit implementation and are not part of the normal operator registry
- `!` is a built-in unary operator for negating standalone truthiness or boolean-returning method calls such as `!file` and `!file.exists()`
- See `expression-calculator\README_EXTENSIBILITY.md` for registration examples

#### Example
```java
Map<String, Object> vars = new HashMap<String, Object>();
vars.put("enabled", true);
vars.put("count", 5);
boolean ok = calc.compareCalculation("enabled && count > 0", vars); // true
boolean bitwiseOk = calc.compareCalculation("(10 ^ 12) == 6", vars); // true
```

### 4.3 Raw-Value Evaluation (`evaluate`)
`evaluate` returns the original Java object instead of a formatted string:

```java
Object intValue = calc.evaluate("1 + 2", vars);     // Integer(3)
Object longValue = calc.evaluate("1 + 2L", vars);   // Long(3)
Object decimal = calc.evaluate("1 + 2.0", vars);    // BigDecimal(3)
Object flag = calc.evaluate("enabled || count > 0", vars); // Boolean.TRUE
```

### 4.4 Indexing and Casts
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

### 4.5 Custom Converters
```java
ConverterRegistry registry = ConverterRegistry.getInstance();
registry.register("wrapped", value -> value == null ? null : "[" + value + "]");

Object wrapped = calc.evaluate("(wrapped)123", vars); // "[123]"
```

## 5. Supported Variable Types for Boolean Conditions
In `compareCalculation`, these types can be used directly as conditions:
| Type         | Rule                        |
|--------------|-----------------------------|
| null         | false                       |
| Boolean      | value itself                |
| File         | file.exists()               |
| Collection   | not empty                   |
| Map          | not empty                   |
| Array        | length > 0                  |
| Other direct variable values | non-null => true |

Direct variables use the rules above, so a non-null string/number/character variable is truthy. Literals and computed numeric/string/character expressions still need explicit comparisons (for example, `a > 0`, not `a + b`).
Missing variables still raise a variable-not-found error outside of null equality checks.

## 6. Public Field and Method Access in Expressions
- Supports public instance fields, array `length`, no-arg/arg methods, and chained access.
- Variable numbers are converted to `BigDecimal` for method matching.
- Direct literals retain their type.
- String and character literals are valid method arguments.

#### Example
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
vars.put("return.value", "success");

calc.evaluate("holder.name", vars);              // "Copilot"
calc.evaluate("items.length", vars);             // Integer(3)
calc.calculation("holder.name.length()", vars);  // "7"
calc.evaluate("return.value", vars);             // "success"
```

```java
calc.calculation("10 & 12", vars);      // "8"
calc.calculation("2 ** 3", vars);       // "8"
calc.calculation("3 <<< 2", vars);      // "12"
calc.compareCalculation("(10 ^ 12) == 6", vars); // true
```

## 7. Calculation Boundaries & Defensive Features
- Configurable max expression depth (constructor parameter)
- Handles unmatched parentheses, invalid numbers, missing variables, divide by zero, and illegal field/method access
- Delimiters inside quoted text (`,`, `&&`, `||`, parentheses) are ignored by structural scanners
- Single `&` and `|` no longer steal the leading half of `&&` and `||`
- Iterative implementation is robust against stack overflow from deep nesting

## 8. Exception Cases
| Scenario                | Example/Error Message                  |
|-------------------------|----------------------------------------|
| Blank expression        | `Expression cannot be blank`           |
| Depth exceeded          | `Expression depth limit exceeded: N`   |
| Parenthesis mismatch    | `Parenthesis mismatch`                 |
| Invalid number          | `Invalid number format`                |
| Missing variable        | `Variable not found: x`                |
| Divide by zero          | `Division by zero`                     |
| Non-integral bitwise op | `位运算只支持整数: x`                  |
| Missing public field    | `字段访问失败，不存在公开字段`        |
| Null field target       | `字段访问失败: 对象为空`              |
| Null method target      | `Method call failed: target is null`   |
| Method type mismatch    | `Method call failed: type mismatch`    |

## 9. Application Scope
- Suitable for arithmetic and logical expressions with variables, public field/method access, and deep nesting
- Not suitable for full scripting or non-Java types

## 10. Advanced Examples

### 10.1 Chained Method Calls
```java
vars.put("file", new File("/tmp/test.txt"));
calc.calculation("file.getName().substring(0, 4).length()", vars);
```

### 10.2 Defensive Scenarios
- Deeply nested: `(1 + (2 + (3 + ... )))`
- Deep boolean: `a == b || (c == d || (...))`
- All such cases are handled with depth limits and robust error reporting.

### 10.3 Templates with Dynamic Keys
`ExpressionFormat` does not support nested placeholders. Build dynamic keys inside the expression instead:

```java
Map<String, Object> dynamicMap = new HashMap<String, Object>();
dynamicMap.put("name_1", "Copilot");
vars.put("dynamicMap", dynamicMap);
vars.put("index", 1);

String text = formatter.format("${dynamicMap['name_' + index]}", vars); // Copilot
```

---
