# Operator Extensibility

## Overview

The calculator resolves unary and binary operators through `OperatorRegistry` instead of hardcoded engine-local enums.

Default registrations are:

| Precedence | Operators | Associativity |
| --- | --- | --- |
| 10 | unary `+` `-` `~` | RIGHT |
| 9 | `**` | RIGHT |
| 8 | `*` `/` `%` | LEFT |
| 7 | `+` `-` | LEFT |
| 6 | `<<` `>>` `>>>` `<<<` | LEFT |
| 5 | `>` `<` `>=` `<=` | LEFT |
| 4 | `&` | LEFT |
| 3 | `^` | LEFT |
| 2 | `|` | LEFT |
| 1 | `==` `!=` | LEFT |

`&&` and `||` still keep their dedicated short-circuit implementation and cannot be registered as normal operators.

### Symbol notes

- `**` means power.
- `^` means bitwise xor.
- `<<<` is a DSL alias of `<<`.
- The built-in default set does not expose a separate `xor` keyword.

## Register a Custom Operator

Use the singleton registry during application startup:

```java
OperatorRegistry registry = OperatorRegistry.getInstance();

registry.registerBinary("pow", 9, Associativity.RIGHT, (left, right) -> {
    BigDecimal base = left.toBigDecimal();
    BigDecimal exponent = right.toBigDecimal();
    BigDecimal normalized = exponent.stripTrailingZeros();
    if (normalized.scale() <= 0 && normalized.compareTo(BigDecimal.ZERO) >= 0) {
        return RuntimeValue.computed(base.pow(normalized.intValueExact()));
    }
    double result = Math.pow(base.doubleValue(), exponent.doubleValue());
    if (Double.isNaN(result) || Double.isInfinite(result)) {
        throw new IllegalArgumentException("幂运算结果无效");
    }
    return RuntimeValue.computed(BigDecimal.valueOf(result));
});
```

After registration:

```java
ExpressionCalculator calculator = new IterativeExpressionCalculator();
calculator.calculation("2 ** 3 ** 2", Map.of());  // "512"
calculator.calculation("2 pow 3", Map.of());      // "8"
calculator.calculation("10 ^ 12", Map.of());      // "6"
```

## When to Use Custom Operators

- Add a compatibility alias if you need to preserve old syntax in a migration.
- Add a domain-specific operator if your expression language needs business-specific symbols.
- Use `resetToDefaults()` in tests when you need to restore the built-in operator set.

## Notes

- `**` is right-associative, so `2 ** 3 ** 2` parses as `2 ** (3 ** 2)`.
- `^` is reserved for xor, so it should not be used as a power alias unless you register it manually yourself.
- `<<<` is intentionally kept as a left-shift alias to preserve DSL symmetry.
