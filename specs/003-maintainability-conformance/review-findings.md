# Review Findings: Maintainability Conformance

Record of feature 003 (format in [data-model.md](data-model.md)). Every finding ends as `fixed` or `justified`.

## Starting point (T011, 2026-10-22)

`./gradlew layoutCheck` over the whole code base before any change: **8,482 violations in 252 files** (ktlint 1.8.0,
code style `ktlint_official`, experimental rules off, plus the project rules).

| Rule | Violations |
|------|-----------:|
| standard:argument-list-wrapping | 2,776 |
| standard:function-signature | 1,804 |
| standard:chain-method-continuation | 762 |
| standard:class-signature | 468 |
| standard:multiline-expression-wrapping | 446 |
| standard:max-line-length | 367 |
| standard:function-literal | 301 |
| standard:blank-line-before-declaration | 294 |
| layout:control-flow-body | 198 |
| layout:blank-line-between-steps | 159 |
| standard:parameter-list-wrapping | 158 |
| standard:binary-expression-wrapping | 152 |
| standard:no-empty-first-line-in-class-body | 143 |
| standard:blank-line-between-when-conditions | 136 |
| standard:when-entry-bracing | 117 |
| standard:wrapping | 66 |
| standard:import-ordering | 29 |
| standard:multiline-if-else | 28 |
| standard:if-else-wrapping | 28 |
| standard:statement-wrapping | 27 |
| standard:property-wrapping | 17 |
| standard:trailing-comma-on-declaration-site | 2 |
| standard:if-else-bracing | 2 |
| standard:spacing-between-declarations-with-comments | 1 |
| standard:filename | 1 |

ktlint parsed all project files without a parse error. `standard:function-naming` reports **no** finding: ktlint 1.8.0
accepts the test names written as sentences in backticks, so the planned suppression for test sources was not added
(a rule is only disabled where it cannot be satisfied).

### Trial cases (quickstart 2–5a, 7; SC-004 for the local check)

Scratch files under `tooling/scratch/` (deleted afterwards), checked with `-PlayoutPath`:

| Case | Expected | Reported |
|------|----------|----------|
| `if (rows != 1) return false` | `layout:control-flow-body` | ✓ |
| 7 statements without a blank line | `layout:blank-line-between-steps` | ✓ |
| line indented with 3 spaces | `standard:indent` | ✓ |
| line of 150 characters | `standard:max-line-length` | ✓ |
| multi-line parameter list without trailing comma | `standard:trailing-comma-on-declaration-site` | ✓ |
| `val label = if (dropped) … else …`, `find() ?: return label` | no violation | ✓ no violation |

## Result of user story 1 (T025, 2026-10-22)

`./gradlew layoutCheck` over the whole code base: **`Layout check: no violations`**. Every module got one mechanical
commit (`layoutFormat`) and one manual commit; all module tests stayed green after each step (domain 176,
application 150, persistence 55, solver 17, web 120, bootstrap 26, layout rules 17). No ktlint rule had to be
suppressed.

## Findings

| Id | Module | Location | Rule | Description | Resolution | Status |
|----|--------|----------|------|-------------|------------|--------|
| F-001 | tooling/layout-rules | `BlankLineBetweenStepsRule` | layout:blank-line-between-steps | Column mappings (`insert { it[x] = … }`, up to 13 assignments) were reported, although a mapping is one step and blank lines inside it would separate nothing | Rule refined test-first: a body of assignments only counts as one mapping step (4254aa0; research.md R2, data-model.md) | fixed |
| F-002 | web, solver, bootstrap | `TradeOffChart.render`, `ConstraintTextCatalogs`, `GenerationPerformanceTest`, `TestSchoolScenarioTest.printMetrics` | standard:max-line-length | Auto-format broke long string templates inside `${…}` | Parts of the text named first (edge coordinates, teacher, classes, `report(…)`), each template on one line; output unchanged | fixed |
| F-003 | build | `build.gradle.kts` (T002 code) | — | `configurations.creating` delegate deprecated since Gradle 9.6 (compiler warning) | `configurations.create(…)` | fixed |
