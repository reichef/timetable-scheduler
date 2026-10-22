# Contract: Layout Check

The interface of the layout check for developers, AI assistants and the CI pipeline (FR-005, FR-010).

## Gradle tasks

| Task | Behaviour | Exit |
|------|-----------|------|
| `./gradlew layoutCheck` | Runs ktlint once over all project Kotlin files (production, test, test fixtures, `*.gradle.kts`, `tooling/`) with the standard rules and the `layout` rule set of [data-model.md](../data-model.md); excludes `build/` directories and the Gradle wrapper | `0` without violations; non-zero with violations |
| `./gradlew layoutFormat` | Runs ktlint with `--format`: applies every auto-fix of the standard rules and of `layout:control-flow-body`, then reports the remaining violations like `layoutCheck` | `0` if nothing remains; non-zero otherwise |
| `-PlayoutPath=<directory>` | Optional for both tasks: limits the run to the Kotlin files below `<directory>` (relative to the repository root), e.g. `-PlayoutPath=domain` for the per-module commits | — |
| `./gradlew check` / `./gradlew build` | Depend on `layoutCheck` (after the code base is clean, research R7) and run the browser-script test with the web module's tests | fail on any violation |

The CI workflow *Build* runs `./gradlew build` unchanged and therefore fails on violations.

## Report format

One line per violation on the console, sorted by file and line:

```text
<relative path>:<line>:<column>: <message> (<rule id>)
```

This is ktlint's `plain` report, used unchanged. `<rule id>` is `standard:<rule>` for ktlint's standard rules (e.g. `standard:max-line-length`,
`standard:trailing-comma-on-call-site`) and `layout:<rule>` for the project rules.

Example:

```text
adapters/persistence/src/main/kotlin/timetablescheduler/adapters/persistence/ExposedTransactionRunner.kt:13:42: Body of 'if' must be a block in braces starting on its own line (layout:control-flow-body)
```

At the end: `Layout check: <n> violation(s) in <m> file(s)` or `Layout check: no violations`.

Messages name the rule and the place only; they never contain source text beyond the keyword (no names or data from
the code, Constitution III applies to build output as well).

## Browser script

`adapters/web` test `BrowserScriptLayoutTest` fails with the line number and rule id when
`src/main/resources/static/language-switch.js` breaks `layout:control-flow-body` or
`layout:blank-line-between-steps`.
