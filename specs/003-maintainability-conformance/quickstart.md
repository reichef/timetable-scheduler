# Quickstart: Maintainability Conformance

Validation guide for feature 003. The application's behaviour does not change; the scenarios prove the check works
and nothing broke.

## Prerequisites

JDK 21, Docker (for the integration tests and the test school), the `.env` of feature 001.

## Scenarios

| # | Steps | Expected |
|---|-------|----------|
| 1 | `./gradlew layoutCheck` on the finished branch | `Layout check: no violations`, exit 0 (SC-001) |
| 2 | Add `if (true) println("x")` inside any function and run `./gradlew build` | Build fails; the report names the file, line and `layout:control-flow-body` (SC-004) |
| 3 | Add a function with 7 statements and no blank line, run `./gradlew build` | Build fails with `layout:blank-line-between-steps` |
| 4 | Indent a line with 3 spaces, run `./gradlew build` | Build fails with `standard:indent` |
| 5 | Write a line of 150 characters, run `./gradlew build` | Build fails with `standard:max-line-length` |
| 5a | Remove the trailing comma of a multi-line parameter list, run `./gradlew build` | Build fails with `standard:trailing-comma-on-declaration-site` |
| 6 | Run `./gradlew layoutFormat` after scenarios 2, 4 and 5a | The violations are fixed automatically; scenarios 3 and 5 remain and are reported (no auto-fix) |
| 7 | Write `val label = if (dropped) droppedText else lessonText` and `val school = find(id) ?: return` | No violation (constitution exceptions, FR-003) |
| 8 | Write `if (x) { doIt() }` on one line in `language-switch.js` and run the web tests | `BrowserScriptLayoutTest` fails with the line number |
| 9 | Undo scenarios 2–8; push the branch | Workflow *Build* green on GitHub; a pushed violation turns it red |
| 10 | `./gradlew build` | All tests green; number of tests at least the number before the feature plus the new tests (SC-002) |
| 11 | Quickstart of features 001 and 002 against `:bootstrap:run` with a fresh database | Same results as before the feature (SC-003) |
| 12 | `./gradlew :bootstrap:testSchoolTest` | Both test-school scenarios pass as before |
| 13 | Open `review-findings.md` | Every finding is `fixed` or `justified`; none `open` (SC-005, SC-007); every ktlint suppression in the code has a finding with its reason |
| 14 | Choose 20 functions with at least 10 lines at random | Each shows its steps as blank-line separated blocks (SC-006) |
