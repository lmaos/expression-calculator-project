# Copilot Instructions for expression-calculator

## Build, Test, and Lint Commands

- **Build & Test (Maven):**
  - Run all tests: `mvn test` in the `expression-calculator` directory.
  - Run a single test class: `mvn -Dtest=ClassName test` (e.g., `mvn -Dtest=ExpressionCalculatorTest test`).
- **Requirements:** JDK 17+, Maven 3.8+
- **Lint:** No explicit linting configured; follow standard Java/Maven conventions.

## High-Level Architecture

- **Core Interface:** `ExpressionCalculator` defines two main methods:
  - `calculation(String, Map<String, Object>)`: Evaluates arithmetic expressions, returns a string result.
  - `compareCalculation(String, Map<String, Object>)`: Evaluates comparison/logical expressions, returns a boolean.
- **Implementations:**
  - `RecursiveExpressionCalculator`: Recursive descent, clear structure, best for teaching/reading.
  - `IterativeExpressionCalculator`: Explicit stack, robust for deep nesting and production use.
- **Engines:**
  - `RecursiveExpressionEngine` and `IterativeExpressionEngine` handle arithmetic parsing/evaluation.
  - `IterativeBooleanExpressionEngine` handles boolean logic with short-circuiting and deep nesting defense.
  - `ExpressionRuntimeSupport` provides type conversion, method invocation, and truthiness rules.
- **Testing:**
  - Extensive JUnit 5 parameterized tests cover arithmetic, boolean, edge cases, and deep nesting.

## Key Conventions

- **Expression Depth Limit:** Both calculators accept an optional max depth in the constructor. Exceeding this throws an exception.
- **Truthiness Rules:** In `compareCalculation`, only specific types (Boolean, File, Collection, Map, Array) are allowed as standalone conditions. Numbers/strings must be used with comparison operators.
- **Method Calls:** Expressions can invoke methods on variables (e.g., `str.length()`, `file.exists()`). Variable numbers are converted to `BigDecimal` for method matching.
- **Error Handling:**
  - Blank expressions, unmatched parentheses, invalid numbers, missing variables, divide by zero, and illegal method calls all throw clear exceptions.
- **Startup Context:** For session/task continuity, see `startup-context.md` and `docs/task-status.md`.

---

This file summarizes build/test commands, architecture, and conventions for Copilot and future AI assistants. Would you like to adjust anything or add coverage for other areas?