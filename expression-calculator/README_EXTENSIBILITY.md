# Operator Extensibility

## Overview

The calculator now resolves unary and binary operators through `OperatorRegistry` instead of hardcoded engine-local enums.

Default registrations are:

| Precedence | Operators | Associativity |
| --- | --- | --- |
| 10 | unary `+` `-` `~` | RIGHT |
| 9 | `^` | RIGHT |
| 8 | `*` `/` `%` | LEFT |
| 7 | `+` `-` | LEFT |
| 6 | `<<` `>>` `>>>` `<<<` | LEFT |
| 5 | `>` `<` `>=` `<=` | LEFT |
| 4 | `&` | LEFT |
| 3 | `xor` | LEFT |
| 2 | `|` | LEFT |
| 1 | `==` `!=` | LEFT |

`&&` and `||` still keep their dedicated short-circuit implementation and cannot be registered as normal operators. The built-in `^` operator means **power**, not bitwise xor; the default xor keyword is `xor`.

## Register a Custom Operator

Use the singleton registry during application startup:

```java
OperatorRegistry registry = OperatorRegistry.getInstance();

registry.registerBinary("**", 9, Associativity.RIGHT, (left, right) -> {
    BigDecimal base = left.toBigDecimal();
    BigDecimal exponent = right.toBigDecimal();
    BigDecimal normalized = exponent.stripTrailingZeros();
    if (normalized.scale() <= 0 && normalized.compareTo(BigDecimal.ZERO) >= 0) {
        return RuntimeValue.computed(base.pow(normalized.intValueExact()));
    }
    return RuntimeValue.computed(BigDecimal.valueOf(Math.pow(base.doubleValue(), exponent.doubleValue())));
});
```

After registration:

```java
ExpressionCalculator calculator = new IterativeExpressionCalculator();
calculator.calculation("2 + 3 * 4 ^ 2", Map.of());  // "50"
calculator.calculation("10 % 3", Map.of());         // "1"
calculator.calculation("2 ** 3 ** 2", Map.of());    // "512"
```

## Notes

1. The registry is global to the JVM process. Register custom operators once during startup instead of mutating the registry for each request.
2. Unary and binary operators use separate namespaces, so `-` can safely exist as both unary and binary.
3. Matching is longest-symbol-first, so multi-character operators such as `>>>`, `<<<`, `>=`, or `**` are checked before shorter prefixes.
4. If a custom operator returns `BigDecimal`, `calculation(...)` will normalize trailing zeros when formatting the final string. For example, a `BigDecimal("2.0")` result is returned as `"2"`.
5. Built-in bitwise operators use 64-bit integer semantics. `<<<` is provided as a DSL alias of `<<` for symmetry, and `xor` is the default bitwise xor keyword.
