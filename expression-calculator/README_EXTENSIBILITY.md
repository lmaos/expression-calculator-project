# Operator Extensibility

## Overview

The calculator resolves unary and binary operators through `OperatorRegistry` instead of hardcoded engine-local enums.

Default registrations are:

| Precedence | Operators | Associativity |
| --- | --- | --- |
| 10 | unary `+` `-` `~` `!` | RIGHT |
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
- `!` means logical negation for standalone truthiness / boolean results.
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
Map<String, Object> emptyVariables = Collections.emptyMap();

ExpressionCalculator calculator = new IterativeExpressionCalculator();
calculator.calculation("2 ** 3 ** 2", emptyVariables);  // "512"
calculator.calculation("2 pow 3", emptyVariables);      // "8"
calculator.calculation("10 ^ 12", emptyVariables);      // "6"
```

## When to Use Custom Operators

- Add a compatibility alias if you need to preserve old syntax in a migration.
- Add a domain-specific operator if your expression language needs business-specific symbols.
- Use `resetToDefaults()` in tests when you need to restore the built-in operator set.

## Notes

- `**` is right-associative, so `2 ** 3 ** 2` parses as `2 ** (3 ** 2)`.
- `^` is reserved for xor, so it should not be used as a power alias unless you register it manually yourself.
- `<<<` is intentionally kept as a left-shift alias to preserve DSL symmetry.

## Type Converters

Custom casts are managed by `ConverterRegistry`:

```java
ConverterRegistry registry = ConverterRegistry.getInstance();
registry.register("wrapped", value -> value == null ? null : "[" + value + "]");

ExpressionCalculator calculator = new IterativeExpressionCalculator();
Object result = calculator.evaluate("(wrapped)123", Collections.emptyMap());  // "[123]"
```

Use `resetToDefaults()` in tests after mutating converter registrations, just like operator registrations.

## Template Output Registry

Template output customization is managed by `OutputFormatRegistry`:

```java
OutputFormatRegistry registry = OutputFormatRegistry.getInstance().copy();
registry.setOption(byte[].class, "mode", "base64");
registry.setOption(File.class, "mode", "content");
registry.setOption(File.class, "charset", "UTF-8");
registry.setOption(Date.class, "pattern", "yyyyMMdd");
registry.setOption(Date.class, "timeZone", "UTC");

ExpressionFormat formatter = new DefaultExpressionFormat(new IterativeExpressionCalculator());
String text = formatter.format(
        "bytes=${payload}|file=${file}|date=${createdAt}",
        "${?}",
        variables,
        registry);
```

Built-in defaults:

- `byte[]`: `mode=text/base64/hex`, `charset`
- `File`: `mode=path/name/content`, `charset`
- `Date`: `pattern`, `timeZone`

You can also register your own formatter for any type:

```java
OutputFormatRegistry registry = OutputFormatRegistry.getInstance().copy();
registry.register(BigDecimal.class, (value, context) ->
        context.stringOption("prefix", "") + ((BigDecimal) value).stripTrailingZeros().toPlainString());
registry.setOption(BigDecimal.class, "prefix", "N=");
```

## Operational Advice

- Treat `OperatorRegistry`, `ConverterRegistry`, and the global `OutputFormatRegistry` as application-level singletons when you want shared defaults.
- For per-request or per-template formatting preferences, clone the output registry with `copy()` and pass it into the 4-argument `ExpressionFormat.format(...)`.
- Method lookup is already cached by `BeanUtils`, and cast operators are cached by `CastOperator`, so the main hot path is still parsing and evaluation.
- If you extend the DSL further, prefer reusing `ExpressionTextSupport` for any new delimiter or postfix syntax so quote-aware scanning stays consistent.
