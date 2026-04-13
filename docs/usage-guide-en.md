# Expression Calculator Usage Guide (English)

## 1. Overview
The `expression-calculator` library provides a unified interface for evaluating arithmetic and logical expressions with variables and method calls. It is suitable for business rule evaluation, configuration, and data validation scenarios.

### Main Features
- Unified interface: `ExpressionCalculator`
- Two implementations:
  - `RecursiveExpressionCalculator`: Recursive, readable, ideal for learning and simple expressions
  - `IterativeExpressionCalculator`: Stack-based, robust for deep nesting and production
- Supports arithmetic, comparison, logical operations, and method calls
- Defensive against malicious or overly deep expressions

## 2. Environment Requirements
- Java 17 or higher
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
Map<String, Object> vars = Map.of("a", 1, "b", 2, "c", 3);
String result = calc.calculation("a + b * (c + 2)", vars); // "11"
boolean ok = calc.compareCalculation("a + b > 2 && c == 3", vars); // true
```

### 3.3 Set Expression Depth Limit
```java
ExpressionCalculator calc = new IterativeExpressionCalculator(100); // max depth 100
```
Exceeding the limit throws: `Expression depth limit exceeded: 100`

## 4. Supported Expression Types

### 4.1 Arithmetic Expressions (`calculation`)
- Operators: `+ - * /`
- Parentheses: `()`
- Unary plus/minus: `+x -x`
- String literals `"text"` and character literals `'A'`
- Variable references
- Method calls (e.g., `str.length()`)

#### Example
```java
Map<String, Object> vars = Map.of("price", 12.5, "discount", 2.5);
String result = calc.calculation("price + discount", vars); // "15"
```

- `+` performs string concatenation when a non-numeric string/character is involved.
- If both operands can still be interpreted as numbers, addition keeps numeric semantics.

### 4.2 Comparison & Logical Expressions (`compareCalculation`)
- Comparisons: `== != > < >= <=`
- Logical: `&& ||`
- Parentheses
- String and character literals in comparisons
- Missing variables are treated as `null` only for `== null` / `!= null` checks
- Direct variable truthiness (see below)

#### Example
```java
Map<String, Object> vars = Map.of("enabled", true, "count", 5);
boolean ok = calc.compareCalculation("enabled && count > 0", vars); // true
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

Numbers, strings, and characters must be used with comparison operators (e.g., `a > 0`).
Missing variables still raise a variable-not-found error outside of null equality checks.

## 6. Method Calls in Expressions
- Supports no-arg, arg, and chained method calls.
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

## 7. Calculation Boundaries & Defensive Features
- Configurable max expression depth (constructor parameter)
- Handles unmatched parentheses, invalid numbers, missing variables, divide by zero, illegal method calls
- Delimiters inside quoted text (`,`, `&&`, `||`, parentheses) are ignored by structural scanners
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
| Null method target      | `Method call failed: target is null`   |
| Method type mismatch    | `Method call failed: type mismatch`    |

## 9. Application Scope
- Suitable for arithmetic and logical expressions with variables, method calls, and deep nesting
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

---
