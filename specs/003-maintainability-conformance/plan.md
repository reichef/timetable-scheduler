# Implementation Plan: Maintainability Conformance of the Existing Code

**Branch**: `003-maintainability-conformance` | **Date**: 2026-10-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-maintainability-conformance/spec.md`

## Summary

Bring the code of features 001 and 002 in line with Constitution v2.1.0, Principle I "Maintainability", without
changing behaviour. An automated layout check — ktlint with all non-experimental standard rules (the constitution's
formatting standard) plus two project-owned rules that turn the constitution's layout examples (bodies on their own
line in braces, blank lines between steps) into checked rules — finds the violations, fixes most of them
automatically and, once the code is clean, fails the build locally and in CI. Names, function
size and explanatory comments are reviewed module by module with recorded findings; functions without a meaningful
test are found with a one-off coverage report and get tests that pin down their current behaviour.

## Technical Context

**Language/Version**: Kotlin 2.4 on JVM 21 (unchanged); the custom rules are Kotlin as well

**Primary Dependencies**: ktlint (command-line tool and rule engine API, used only by the build), Kover (coverage
report, used once as a finding aid) — see [research.md](research.md) R1, R6; exact versions verified in the setup
task

**Storage**: N/A (no data changes, no migrations)

**Testing**: kotlin.test / JUnit 5 as before; new unit tests for the custom rules; all existing tests unchanged in
what they assert; quickstart scenarios of features 001 and 002 and the test-school scenarios as regression check

**Target Platform**: unchanged (JVM server application); the check runs wherever `./gradlew build` runs, including
the GitHub Actions workflow *Build*

**Project Type**: Web application (server-rendered, single deployable), Gradle multi-module

**Performance Goals**: the layout check adds at most about 30 s to a full `./gradlew build` (265 files)

**Constraints**: no behaviour change (FR-008); no confidential data in logs (FR-009, Constitution III); no package
manager or JavaScript tooling (Constitution, browser scripts); generated files (Gradle wrapper) excluded

**Scale/Scope**: 265 Kotlin files (about 26,000 lines; 150 production, 107 test, build scripts) and one browser
script; at the start about 190 control-flow bodies and 78 `else` branches on the same line, plus several hundred
ktlint standard findings (mainly line length and trailing commas; exact count recorded on the first run)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Gate | Assessment |
|------------------|------------|
| I. Maintainability — clean code | This feature implements it for existing code; the new rule module follows it too (small rules, speaking names, comments on the matching logic) ✅ |
| I. Maintainability — formatting standard (v2.1.0) | ktlint with all non-experimental standard rules in the default code style, checked by the build; suppressions only per file or statement with a reason ✅ |
| I. Maintainability — layout examples | Enforced as additional project rules (spec clarification 2026-10-22); stricter than the constitution, which allows that ✅ |
| I. Maintainability — TDD | Rules are written test-first; missing tests for existing functions are added (FR-007) ✅ |
| II. Tool and library use | ktlint instead of an own parser; no library for trivial things (the JavaScript check is a few lines of test code) ✅ |
| III. Confidentiality | Only layout and names change; log statements keep ids/statuses/counts; log confidentiality tests unchanged ✅ |
| IV. Pareto-optimal scheduling integrity | Scheduling behaviour unchanged; hard-constraint and Pareto tests unchanged and green ✅ |
| Kotlin/Gradle only | Rules are Kotlin, run by Gradle; no Node/npm ✅ |
| Clean architecture | The rule module is build tooling outside the application circles; no production module depends on it ✅ |
| Quality gates | Review verifies the layout and tests (constitution workflow); separate branch ✅ |

No violations; Complexity Tracking stays empty.

**Re-check after Phase 1** (updated 2026-10-22 for Constitution v2.1.0): the design adds one Gradle module used
only by the build and one test in `adapters/web`, runs every non-experimental ktlint standard rule and records each
suppression with its reason; all gates still pass.

## Project Structure

### Documentation (this feature)

```text
specs/003-maintainability-conformance/
├── plan.md              # This file
├── research.md          # Phase 0: tool and rule decisions (R1–R7)
├── data-model.md        # Phase 1: rule catalogue and the finding record
├── quickstart.md        # Phase 1: how to run and validate the check
├── contracts/
│   └── layout-check.md  # Phase 1: Gradle tasks and report format of the check
├── review-findings.md   # Created during implementation (FR-006, FR-007)
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
tooling/
└── layout-rules/                      # NEW: ktlint rule set (build tooling only)
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/timetablescheduler/tooling/layout/
        │   ├── LayoutRuleSetProvider.kt
        │   ├── ControlFlowBodyRule.kt          # FR-001, FR-003
        │   └── BlankLineBetweenStepsRule.kt    # FR-002
        ├── main/resources/META-INF/services/   # rule set registration for ktlint
        └── test/kotlin/timetablescheduler/tooling/layout/
            ├── ControlFlowBodyRuleTest.kt
            └── BlankLineBetweenStepsRuleTest.kt

.editorconfig                          # NEW: default code style settings, experimental rules off (R3)
build.gradle.kts                       # CHANGED: layoutCheck / layoutFormat tasks, wired into `check`
settings.gradle.kts                    # CHANGED: include tooling:layout-rules
gradle/libs.versions.toml              # CHANGED: ktlint, Kover versions

domain/, application/, adapters/*/, bootstrap/   # CHANGED: layout, names, split functions, new tests
adapters/web/src/test/kotlin/.../BrowserScriptLayoutTest.kt   # NEW: layout of language-switch.js (R4)
README.md                              # CHANGED: how to run and fix the check (FR-010)
```

**Structure Decision**: the existing Gradle multi-module layout stays; the custom rules live in a separate module
under `tooling/` so that no application module depends on build tooling (clean architecture) and the rules can be
tested like any other code.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations.
