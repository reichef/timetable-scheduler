# Research: Maintainability Conformance of the Existing Code

Decisions for [plan.md](plan.md). Each one resolves an open point of the Technical Context. Updated 2026-10-22 for
Constitution v2.1.0 (ktlint with all non-experimental standard rules is the formatting standard) and the clarification
that the constitution's layout examples are enforced as project rules as well.

## R1 — Tool for the automated layout check (FR-005)

- **Decision**: **ktlint** — prescribed by Constitution v2.1.0 — run from the Gradle build through a `JavaExec` task
  with the ktlint command-line tool as a dependency of a dedicated configuration, extended by a project-owned custom
  rule set (R2). No third-party Gradle plugin.
- **Rationale**: ktlint parses Kotlin source text and does not depend on the project's compiler version, it is
  actively maintained, it can fix many of its own findings (`--format`) and it supports custom rule sets written in
  Kotlin (Constitution, Technology constraints: Kotlin only). Running the CLI directly keeps the build free of a
  plugin whose release cycle lags behind ktlint.
- **Alternatives considered**:
  - *detekt*: the 2.x line, the only one that supports Kotlin 2.4, is still in alpha (2.0.0-alpha.5, June 2026,
    "expect further breaking changes"); not suitable for a build gate. detekt 1.x does not support the current
    compiler.
  - *Gradle plugins wrapping ktlint* (kotlinter, ktlint-gradle, Spotless): add a further dependency with its own
    version pinning of ktlint, and custom rule sets are harder to wire in.
  - *A hand-written checker without a parser*: regular expressions cannot tell an `if` used as a value from an `if`
    statement reliably; rejected.
- **To verify in the setup task**: the current ktlint version, that it parses all 265 files of the project without
  error, and the exact names of the standard rules used in R3.

## R2 — Project rules for the constitution's layout examples (FR-001 – FR-003, FR-005)

- **Decision**: a small Gradle module `tooling/layout-rules` with two ktlint rules (rule-set id `layout`), each with
  tests written first. They run in the same ktlint invocation as the standard rules (R3), so one report covers all
  findings:
  1. **`layout:control-flow-body`** (FR-001, FR-003): the body of `if`, `else`, `for`, `while` and `do` must be a
     block in braces whose first statement starts on its own line. Not reported: an `if`/`else` that is used as a
     value (assigned, returned, passed as an argument or as the result of a lambda or expression body) and fits on
     one line, `when` branches with a single expression, and Elvis exits (`?: return`). Auto-correct wraps the body
     in braces and moves it to its own line.
  2. **`layout:blank-line-between-steps`** (FR-002): a function or lambda body with **more than 5 statements** must
     contain at least one blank line between statements, and no run of more than 5 consecutive statements may be
     without a blank line. A body of assignments only (fields mapped to columns, e.g. Exposed `insert { it[x] = … }`)
     is one mapping step and is not checked (added during T016: persistence has such mappings with up to 13
     assignments, where blank lines would be arbitrary). No auto-correct: where to separate steps is a judgement, so
     violations are fixed by hand.
- **Rationale**: the clarification of 2026-10-22 makes the constitution's layout examples checked rules. No ktlint
  standard rule encodes them: `if-else-bracing` only demands consistent braces among the branches of one `if`,
  `multiline-if-else` only multi-line bodies, and none knows the constitution's exceptions (values vs. actions,
  Elvis exits) or blank lines between steps. The blank-line rule cannot decide what "logically connected" means; the
  threshold catches the "one continuous block" the spec forbids, and the review (R5) checks that the blank lines
  separate real steps.
- **Compatibility with the standard rules**: the auto-correct of `layout:control-flow-body` produces formatting the
  standard rules accept (braces, wrapped body, indentation); the rule tests include a case formatted by both.
  `layout:blank-line-between-steps` only requires blank lines between statements, which no standard rule forbids
  (the standard rules only forbid consecutive blank lines and blank lines at the start or end of a block).
- **Alternatives considered**: relying on the standard rules only — misses the frequent single-line bodies
  (`if (rows != 1) return false`) and blank lines; a lower threshold of 3 statements — would force blank lines into
  small, cohesive functions.

## R3 — ktlint standard rules (FR-004)

- **Decision**: **all non-experimental rules of the ktlint standard rule set**, in ktlint's default code style
  (`ktlint_official`), as Constitution v2.1.0 requires. `.editorconfig` only states what the default style needs to
  know (Kotlin files, 4-space indentation, UTF-8, final newline); it disables no standard rule. Experimental rules stay
  off (`ktlint_experimental = disabled`).
- **Suppressions**: where a rule cannot be satisfied (e.g. a literal that must stay one line longer than the
  maximum), the rule is suppressed for that statement or file only with `@Suppress("ktlint:standard:<rule>")` (the
  form is confirmed in the setup task) and a comment giving the reason (Constitution I). Each suppression is
  recorded in `review-findings.md` (rule `ktlint-suppression`).
- **`function-naming` in tests**: disabled for all test sources (`**/src/test/**`, which includes the test-school
  tooling under `bootstrap/src/test/**`) in `.editorconfig`, with the reason as a comment: test functions are named as sentences in
  backticks that describe the tested behaviour (spec clarification; Constitution v2.1.0 allows disabling for a defined
  group of files). Test fixtures (`src/testFixtures`) are not tests and keep the rule. Recorded once in
  `review-findings.md`.
- **Expected volume**: several hundred findings, mainly `max-line-length` (the default style allows 140 characters;
  the code has many longer lines), `trailing-comma-on-call-site` / `trailing-comma-on-declaration-site`, wrapping
  rules (`function-signature`, `class-signature`, `chain-method-continuation`, `multiline-expression-wrapping`) and
  `blank-line-before-declaration`. Most have an auto-fix (`ktlint --format`); `max-line-length` does not and is fixed
  by hand (wrapping arguments, extracting variables, splitting string templates). The exact count is recorded when
  the check first runs (SC-001).
- **Rules to watch**: `property-naming` (constants), `filename` and `no-wildcard-imports`; findings there are fixed
  or suppressed with a reason.
- **Rationale**: the product owner accepted the volume; using the complete standard set avoids a project-specific
  selection that would need maintaining.
- **Alternatives considered**: a selection of rules (the previous plan) — superseded by Constitution v2.1.0; the
  `intellij_idea` code style — not the default the constitution names.

## R4 — The browser script (FR-001)

- **Decision**: a test in `adapters/web` reads `language-switch.js` and checks the same two rules
  (bodies of `if`/`else`/`for`/`while` on their own line in braces; blank lines between steps of functions with more
  than 5 statements). A failing test fails the build.
- **Rationale**: the project has one small script; adding a JavaScript linter would need a package manager, which
  the constitution excludes for browser scripts.
- **Alternatives considered**: ESLint — needs Node and npm; excluding the script — the spec includes it.

## R5 — Clean-code review (FR-006)

- **Decision**: a review module by module (domain, application, persistence, solver, web, bootstrap, build scripts)
  against the clean-code part of Principle I: general-purpose names, functions doing several things (as a guide:
  longer than about 30 lines or with more than one level of abstraction), complex-but-compact functions without an
  explanatory comment. Findings are recorded in `review-findings.md` (format in [data-model.md](data-model.md)) and
  each one is fixed or justified.
- **Rationale**: naming and cohesion need judgement; a recorded list makes SC-005 checkable.

## R6 — Finding untested functions (FR-007)

- **Decision**: use the coverage report of **Kover** (JetBrains' Kotlin coverage plugin for Gradle) once, to list
  functions not executed by any test; review the list for non-trivial functions and add a meaningful test for each.
  The coverage report is a finding aid only, not a gate and not a coverage target (Constitution I: "we do not aim
  for a code-coverage figure").
- **Rationale**: 150 production files cannot be checked for untested functions by hand reliably; Kover is the
  Kotlin-native tool and reads Kotlin functions correctly (inline functions, default arguments).
- **To verify**: the Kover version that supports Kotlin 2.4 and Gradle 9; if none does, the list is built from the
  JaCoCo report instead.
- **Alternatives considered**: a coverage threshold in the build — contradicts the constitution's "no coverage
  figure".

## R7 — Order of work and behaviour safety (FR-008, FR-009)

- **Decision**:
  1. First the check: ktlint wiring, the project rules with their tests, the browser-script test and the coverage
     list — with the gate **not yet** wired into `check`. Record the starting count of findings per rule (SC-001).
  2. Then per module, inner circle first (domain → application → adapters → bootstrap → build scripts → tooling):
     a. **mechanical commit**: `layoutFormat` (ktlint auto-fixes plus the `layout:control-flow-body` auto-fix), with
        no other change, so the review can skim it;
     b. **manual commit**: the remaining findings (line length, blank lines between steps), the clean-code findings
        of the review and new tests for untested functions.
     After each commit: `./gradlew build` green, no test assertion changed.
  3. Wire the gate into `check` (so `./gradlew build` and the CI pipeline fail on violations) once the code base is
     clean.
  4. Finally the full quickstart runs of features 001 and 002 and the test-school scenarios.
- **Rationale**: wiring the gate first would break the build for the whole duration of the work; separating
  mechanical from manual changes keeps a several-hundred-line formatting diff reviewable and localizes a behaviour
  change if one slips in.
- **Confidentiality**: renamed log statements keep logging ids, statuses and counts only; the existing log
  confidentiality tests stay unchanged and must pass.
