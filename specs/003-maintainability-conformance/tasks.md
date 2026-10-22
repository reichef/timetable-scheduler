# Tasks: Maintainability Conformance of the Existing Code

**Input**: Design documents from `specs/003-maintainability-conformance/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md),
[contracts/layout-check.md](contracts/layout-check.md), [quickstart.md](quickstart.md)

**Tests**: Mandatory. Constitution v2.1.0 Principle I (test-driven development) requires meaningful tests written
before the implementation; the spec requires tests for every untested non-trivial function (FR-007). Rule tests are
written first and must fail before the rules are implemented.

**Organization**: Tasks are grouped by user story. The behaviour of the application must not change (FR-008): after
every task `./gradlew build` is green and no existing test assertion is changed.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)

## Path Conventions

- `RULES` = `tooling/layout-rules/src/main/kotlin/timetablescheduler/tooling/layout`,
  `RULES_T` = `tooling/layout-rules/src/test/kotlin/timetablescheduler/tooling/layout`
- `WEB_T` = `adapters/web/src/test/kotlin/timetablescheduler/adapters/web`
- `FEATURE` = `specs/003-maintainability-conformance`
- "Module" means its `src/main`, `src/test` and (for `application`) `src/testFixtures` sources plus its
  `build.gradle.kts`.

**Commit convention (research R7)**: per module one **mechanical** commit (only `layoutFormat` output) and one
**manual** commit (remaining findings, review fixes, new tests), so the formatting diff can be skimmed in review.

---

## Phase 1: Setup

**Purpose**: Tool versions, configuration and the rule module skeleton

- [X] T001 Commit Constitution v2.1.0: remove the Sync Impact Report comment from `.specify/memory/constitution.md` and commit it together with `FEATURE/` (spec, plan, research, data model, contract, quickstart, checklist, this file) — commit e93dde2
- [X] T002 Verify and pin tool versions in `gradle/libs.versions.toml`: the current ktlint release (`ktlint-cli` for the build, `ktlint-rule-engine-core` / `ktlint-cli-ruleset-core` APIs for the custom rules, `ktlint-test` for rule tests) and a Kover version that supports Kotlin 2.4 and Gradle 9 (research R1, R6); record in this task the versions, the exact suppression syntax ktlint supports (expected `@Suppress("ktlint:standard:<rule>")`) and whether ktlint parses all 265 project files without a parse error (fall back to JaCoCo per R6 if no Kover version fits) — 2026-10-22: ktlint 1.8.0 (cli, rule-engine-core, cli-ruleset-core, test, ruleset-standard for compatibility tests; CLI jar checksum verified), Kover 0.9.11 (compatibility confirmed in T031); suppression syntax `@Suppress("ktlint:standard:<rule>")` (ktlint 1.x has no `ktlint-disable` comments); ktlint parses all project files without a parse error
- [X] T003 [P] Create `.editorconfig` at the repository root (research R3): `root = true`; for `[*.{kt,kts}]` `indent_style = space`, `indent_size = 4`, `charset = utf-8`, `insert_final_newline = true`, `ktlint_code_style = ktlint_official`, `ktlint_experimental = disabled`; a section `[**/src/test/**/*.kt]` with `ktlint_standard_function-naming = disabled` and the comment "Test functions are named as sentences in backticks that describe the tested behaviour (spec 003 clarification, Constitution I)"; no other standard rule disabled; record this suppression as the first `ktlint-suppression` finding when `FEATURE/review-findings.md` is created (T011) — without the `function-naming` section: the rule reports nothing for the backtick test names (see review-findings.md "Starting point"), so no suppression is needed
- [X] T004 [P] Create the module `tooling/layout-rules` (`tooling/layout-rules/build.gradle.kts`, Kotlin JVM 21, `compileOnly` ktlint rule-engine API, `testImplementation` ktlint test API) and include it in `settings.gradle.kts`; no application module may depend on it (clean architecture, plan Structure Decision)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The check itself — project rules, ktlint wiring and the starting count. The gate is **not yet** wired
into `check` (research R7).

**⚠️ CRITICAL**: No reformatting of existing code can begin until this phase is complete

### Tests for the project rules (write first, must fail)

- [X] T005 [P] Tests in `RULES_T/ControlFlowBodyRuleTest.kt` for `layout:control-flow-body` (data-model rule catalogue): reported — `if (rows != 1) return false`, `else doIt()`, `for (x in xs) use(x)`, `while (c) step()`, `do step() while (c)`, a multi-line body without braces, a braced body starting on the same line (`if (c) { a() }`); not reported — `val label = if (dropped) droppedText else lessonText`, `return if (a) b else c`, `call(if (a) b else c)`, a single-expression `when` branch, `?: return`, `?: continue`, `?: break`, `?: throw IllegalStateException()`; auto-correct turns `if (c) return false` into a braced block with the body on its own line indented one level deeper, and the corrected code passes the standard `indent` and `if-else-bracing` rules
- [X] T006 [P] Tests in `RULES_T/BlankLineBetweenStepsRuleTest.kt` for `layout:blank-line-between-steps`: a function body with 6 statements and no blank line is reported; 5 statements without a blank line are not; 12 statements split 5 + blank line + 7 are reported (run of 7); 5 + 2 + 5 separated by blank lines are not; 6 + 6 are reported twice (each run is longer than 5); lambda bodies are checked like functions; expression-bodied functions and class bodies are not checked; the rule offers no auto-correct

### Implementation of the project rules

- [X] T007 Implement `RULES/ControlFlowBodyRule.kt` (rule id `layout:control-flow-body`, with auto-correct) to pass T005; the logic that decides "used as a value" vs. "action" carries an explanatory comment (Constitution I)
- [X] T008 Implement `RULES/BlankLineBetweenStepsRule.kt` (rule id `layout:blank-line-between-steps`, threshold `MAXIMUM_STEPS_WITHOUT_BLANK_LINE = 5`, no auto-correct) to pass T006, with an explanatory comment on how runs of statements are counted
- [X] T009 Implement `RULES/LayoutRuleSetProvider.kt` (rule-set id `layout`, both rules) and register it in `tooling/layout-rules/src/main/resources/META-INF/services/` for the ktlint version of T002

### ktlint wiring

- [X] T010 Add the tasks `layoutCheck` and `layoutFormat` to the root `build.gradle.kts` per [contracts/layout-check.md](contracts/layout-check.md): a `ktlint` configuration with the ktlint CLI of T002, `JavaExec` running it with the jar of `:tooling:layout-rules` as additional rule set, over all project `*.kt`/`*.kts` files except `**/build/**` and the Gradle wrapper; `layoutFormat` adds `--format`; an optional `-PlayoutPath=<directory>` limits both to one module (for the per-module commits); the report lists `<path>:<line>:<column>: <rule id> — <message>` and ends with `Layout check: <n> violation(s) in <m> file(s)` or `Layout check: no violations`; messages contain no source text (Constitution III); **not** yet a dependency of `check`; document `-PlayoutPath` in `contracts/layout-check.md` — ktlint runs as the self-contained (`shadowed`) CLI variant; the project rule jar is passed with `--ruleset` (ktlint does not load rule sets from its classpath); the report is ktlint's plain format `<path>:<line>:<column>: <message> (<rule id>)` (contract updated); exit value 1 without a reported violation fails as "ktlint could not run"
- [X] T011 Run `./gradlew layoutCheck` and create `FEATURE/review-findings.md` (format in [data-model.md](data-model.md)) with a header section "Starting point" listing the number of violations per rule id and the total (SC-001); then run the trial cases of quickstart scenarios 2–5a and 7 against a scratch file and record that each is reported or not as expected (SC-004 for the local check), deleting the scratch file afterwards — 8,482 violations in 252 files; all trial cases reported as expected (review-findings.md)

**Checkpoint**: The check works and the starting point is recorded — reformatting can begin

---

## Phase 3: User Story 1 - Read and review code laid out for humans (Priority: P1) 🎯 MVP

**Goal**: Every code file of the project passes all standard ktlint rules and the two project rules (FR-001 – FR-004).

**Independent Test**: `./gradlew layoutCheck` reports `Layout check: no violations` (quickstart scenario 1); open any
file and see blank-line separated steps and bodies on their own line (quickstart scenario 14).

Per module: first the mechanical commit with `./gradlew layoutFormat -PlayoutPath=<module>` and nothing else, then the
manual commit for the findings without auto-fix — `standard:max-line-length` (wrap arguments, extract well-named
variables, split string templates; never change texts or behaviour), `layout:blank-line-between-steps` (separate the
real steps: load, check, compute, store/answer) and naming findings of the standard rules. A rule that cannot be
satisfied is suppressed for that statement or file only, with a comment giving the reason, and recorded in
`FEATURE/review-findings.md` as `ktlint-suppression` (data model "Suppression"). `./gradlew build` green after each
commit.

- [X] T012 [US1] Domain — mechanical commit: `layoutFormat -PlayoutPath=domain` (a0fd215)
- [X] T013 [US1] Domain — manual commit: remaining findings in `domain/` until `layoutCheck -PlayoutPath=domain` reports no violations (31590be)
- [X] T014 [US1] Application — mechanical commit: `layoutFormat -PlayoutPath=application` (main, test fixtures, tests) (417a3a0)
- [X] T015 [US1] Application — manual commit: remaining findings in `application/` (63fb08e)
- [X] T016 [US1] Persistence — mechanical commit: `layoutFormat -PlayoutPath=adapters/persistence` (migrations are SQL and not touched) (643f260)
- [X] T017 [US1] Persistence — manual commit: remaining findings in `adapters/persistence/` (d7a27e1; the rule refinement for column mappings is 4254aa0, see review-findings F-001)
- [X] T018 [US1] Solver — mechanical commit: `layoutFormat -PlayoutPath=adapters/solver` (da6de44)
- [X] T019 [US1] Solver — manual commit: remaining findings in `adapters/solver/` (constraint streams: keep the chained calls readable; wrap per call, not per argument where `chain-method-continuation` allows) (585bc07)
- [X] T020 [US1] Web — mechanical commit: `layoutFormat -PlayoutPath=adapters/web` (33e964b)
- [X] T021 [US1] Web — manual commit: remaining findings in `adapters/web/` (texts in `GermanTexts.kt`/`EnglishTexts.kt` and `ConstraintTextCatalogs.kt` are wrapped by splitting the Kotlin expression, never by changing the displayed text; UI texts tests must stay unchanged) (b4edfd9; displayed texts unchanged, 120 web tests green)
- [X] T022 [US1] Bootstrap — mechanical commit: `layoutFormat -PlayoutPath=bootstrap` (70478d2)
- [X] T023 [US1] Bootstrap — manual commit: remaining findings in `bootstrap/` (incl. the test school data in `bootstrap/src/test/.../testschool/`) (6b0e1b7)
- [X] T024 [US1] Build scripts and tooling: `layoutFormat` and remaining findings in the root `build.gradle.kts`, `settings.gradle.kts` and `tooling/layout-rules/` (the rule module must pass its own rules) (bcb9f68)
- [X] T025 [US1] Run `./gradlew layoutCheck` over the whole project: `Layout check: no violations`; record the result in `FEATURE/review-findings.md` (SC-001) — done: `Layout check: no violations` (2026-10-22), no ktlint suppression needed

**Checkpoint**: The whole code base passes the check; behaviour unchanged (build green)

---

## Phase 4: User Story 2 - Rules stay enforced for future changes (Priority: P2)

**Goal**: Any violation fails the build locally and in CI (FR-005), including the browser script; the check is
documented (FR-010).

**Independent Test**: quickstart scenarios 2–9 — each deliberate violation fails `./gradlew build` and the CI workflow
*Build*.

### Tests for User Story 2 (write first, must fail)

- [ ] T026 [US2] Write `WEB_T/BrowserScriptLayoutTest.kt` (research R4): reads `adapters/web/src/main/resources/static/language-switch.js` and fails with line number and rule id where a body of `if`/`else`/`for`/`while` is not a braced block starting on its own line, or a function body has a run of more than 5 statements without a blank line; include unit cases for the checking logic on small inline scripts (reported and not reported); the test must fail on the current script if it violates the rules

### Implementation for User Story 2

- [ ] T027 [US2] Bring `adapters/web/src/main/resources/static/language-switch.js` in line with the two rules (behaviour unchanged: the unsaved-changes warning and the language switch still work — `UnsavedChangesHintTest` and the language tests stay green) to pass T026
- [ ] T028 [US2] Wire `layoutCheck` into `check` in the root `build.gradle.kts`, so `./gradlew build` and the CI workflow `.github/workflows/build.yml` (unchanged, runs `./gradlew build`) fail on any violation
- [ ] T029 [US2] Document the check in `README.md` (FR-010): what it checks (ktlint standard rules in the default code style, the two project rules, the browser-script test), `./gradlew layoutCheck`, `./gradlew layoutFormat`, `-PlayoutPath`, how to fix the findings without auto-fix and how to suppress a rule with a reason
- [ ] T030 [US2] Run quickstart scenarios 2–8 locally against `./gradlew build` (each fails with the expected rule id; scenario 6: `layoutFormat` fixes 2, 4 and 5a; scenario 7 passes) and undo them; record the results in this task (SC-004)

**Checkpoint**: Violations can no longer be merged

---

## Phase 5: User Story 3 - Clean-code and test findings are resolved (Priority: P3)

**Goal**: Names, function size and explanatory comments conform to the clean-code part of Principle I (FR-006);
every non-trivial function has a meaningful test (FR-007).

**Independent Test**: `FEATURE/review-findings.md` has no `open` finding (quickstart scenario 13; SC-005, SC-007).

### Finding untested functions

- [ ] T031 [US3] Apply Kover (version of T002) in the root `build.gradle.kts` for all application modules, generate the report with `./gradlew koverHtmlReport` (or JaCoCo per R6), and record in `FEATURE/review-findings.md` every non-trivial production function not executed by any test as a `missing-test` finding (module, file and function; trivial getters, data-class members and generated code excluded with a one-line reason per group)

### Review and fixes per module (findings recorded in `FEATURE/review-findings.md`)

Each task: review the module for general-purpose names, functions doing several things (guide: longer than about 30
lines or more than one level of abstraction), complex-but-compact functions without an explanatory comment
(research R5) and whether the blank lines inside functions separate real steps (FR-002 — the rule only checks that
long runs are broken up); record each finding; fix it (rename, split into small single-purpose functions, add the comment) or
justify it; for each `missing-test` finding of the module write a meaningful test that pins down the current
behaviour (a test revealing a defect: record it, mark the test as pending with the finding id, fix the defect in a
separate change — spec assumption). Functions extracted while splitting are covered test-first by the behaviour
tests of the function they came from (run them before and after the split); a non-trivial extracted function that
becomes public or is used from several places gets its own test written before the extraction (Constitution I,
test-driven development). Behaviour and log content unchanged (FR-008, FR-009); `./gradlew build` green.

- [ ] T032 [P] [US3] Domain: review, fixes and missing tests in `domain/`
- [ ] T033 [P] [US3] Application: review, fixes and missing tests in `application/` (incl. `MasterDataUseCases.kt` wiring and the use-case classes)
- [ ] T034 [P] [US3] Persistence: review, fixes and missing tests in `adapters/persistence/` (repository tests use the existing Testcontainers base)
- [ ] T035 [P] [US3] Solver: review, fixes and missing tests in `adapters/solver/` (constraint provider and problem mapping keep their explanatory comments)
- [ ] T036 [P] [US3] Web: review, fixes and missing tests in `adapters/web/` (route tests via the existing test support)
- [ ] T037 [P] [US3] Bootstrap and tooling: review, fixes and missing tests in `bootstrap/` and `tooling/layout-rules/`
- [ ] T038 [US3] Remove Kover from the build again (it was a finding aid, research R6; Principle II: as few dependencies as possible) and confirm in `FEATURE/review-findings.md` that no finding is `open`

**Checkpoint**: All three parts of Principle I hold for the existing code

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T039 Run `./gradlew build` (record the number of tests before and after: at least the old number plus the new tests, SC-002) and `./gradlew :bootstrap:testSchoolTest` (both scenarios pass as before); measure the duration of a full `./gradlew build --no-build-cache` with and without `layoutCheck` (three runs each, median) and record that the check adds at most about 30 s (plan, Performance Goals)
- [ ] T040 Run the quickstarts of features 001 and 002 (`specs/001-scheduling-master-data/quickstart.md`, `specs/002-timetable-generation/quickstart.md`) against `:bootstrap:run` with a fresh database and record that the results equal the earlier runs (SC-003)
- [ ] T041 Check SC-006: choose 20 functions with at least 10 lines at random and record in `FEATURE/review-findings.md` that each shows its steps as blank-line separated blocks
- [ ] T042 Review the whole change against the constitution gates (Principle I all three parts, III no confidential data in logs or build output, quality gates) and fix findings
- [ ] T043 Push the branch, open the pull request `003-maintainability-conformance` → `main` and confirm that the workflow *Build* (now including the layout check) and *CodeQL* succeed; push one deliberate violation on a scratch commit, confirm *Build* turns red, and revert it (quickstart scenario 9)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → **US1** → **US2** → **US3** → **Polish**
- T001 first (commits the constitution and the design documents); T003 and T004 in parallel after T002.

### User Story Dependencies

- **US1 (P1)**: after Foundational. MVP — the code base is readable and passes the check.
- **US2 (P2)**: after US1; wiring the gate earlier would break the build (research R7). T026/T027 (browser script) could run in parallel with US1.
- **US3 (P3)**: after US1, because its fixes touch the same files; its module tasks T032–T037 are independent of each other.

### Within Each Phase

- Rule tests (T005, T006) before the rules (T007 – T009); ktlint wiring (T010) after the rules.
- Per module in US1: mechanical commit before manual commit; modules from the inner circle outwards.
- In US3: T031 before T032–T037; T038 last.

### Parallel Opportunities

- T003, T004; T005, T006.
- US2's T026/T027 with any US1 task.
- US3's T032–T037 (different modules).

---

## Parallel Example: Foundational

```text
Task: "T005 Tests for layout:control-flow-body in RULES_T/ControlFlowBodyRuleTest.kt"
Task: "T006 Tests for layout:blank-line-between-steps in RULES_T/BlankLineBetweenStepsRuleTest.kt"
```

## Parallel Example: User Story 3

```text
Task: "T032 Domain review, fixes and missing tests"
Task: "T034 Persistence review, fixes and missing tests"
Task: "T036 Web review, fixes and missing tests"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Setup and Foundational: the check exists and the starting point is recorded.
2. US1: reformat module by module (mechanical, then manual commits).
3. **Stop and validate**: `layoutCheck` reports no violations, `./gradlew build` green.

### Incremental Delivery

1. US1 → readable code base.
2. US2 → the gate prevents regressions.
3. US3 → names, function size, comments and tests.
4. Polish → regression runs (quickstarts, test school), constitution review, pull request.

---

## Notes

- No behaviour changes; no existing test assertion is changed or weakened (FR-008, SC-002).
- Log statements keep ids, statuses and counts only (FR-009); the log confidentiality tests stay unchanged.
- Generated files (Gradle wrapper) and the specification documents of features 001 and 002 are not changed.
