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

## Untested functions (T031, 2026-10-23)

Kover 0.9.11 (applied to all modules in the working tree only, never committed, research R6) over `./gradlew
koverXmlReport`: 87 production methods without any executed line. Excluded groups, one reason each:

- generated members (data-class `copy`/`equals`/`toString`, enum `values`), property accessors, synthetic bridges and
  default-argument stubs, value-class constructors of the identifiers — generated or trivial, no own behaviour;
- `toString` overrides that hide secrets (`DatabaseSettings`) or name facts in solver logs — one constant expression;
- in-memory fakes in `application` test fixtures — test code;
- single uncovered error branches (`?: fail(NotFound)`, `VersionConflict`) inside functions whose other paths are
  tested — FR-007 asks for a meaningful test per function, these use the shared helpers tested elsewhere.

The remaining non-trivial functions are the findings F-101 – F-113 below.

## Findings

| Id | Module | Location | Rule | Description | Resolution | Status |
|----|--------|----------|------|-------------|------------|--------|
| F-001 | tooling/layout-rules | `BlankLineBetweenStepsRule` | layout:blank-line-between-steps | Column mappings (`insert { it[x] = … }`, up to 13 assignments) were reported, although a mapping is one step and blank lines inside it would separate nothing | Rule refined test-first: a body of assignments only counts as one mapping step (4254aa0; research.md R2, data-model.md) | fixed |
| F-002 | web, solver, bootstrap | `TradeOffChart.render`, `ConstraintTextCatalogs`, `GenerationPerformanceTest`, `TestSchoolScenarioTest.printMetrics` | standard:max-line-length | Auto-format broke long string templates inside `${…}` | Parts of the text named first (edge coordinates, teacher, classes, `report(…)`), each template on one line; output unchanged | fixed |
| F-003 | build | `build.gradle.kts` (T002 code) | — | `configurations.creating` delegate deprecated since Gradle 9.6 (compiler warning) | `configurations.create(…)` | fixed |
| F-101 | application | `TimeGridUseCases.kt` `UpdateTimeSlot` | missing-test | Test `a changed time slot is saved and bumps the school version` in `TimeGridUseCasesTest` | fixed |
| F-102 | application | `MasterDataUseCases.kt` dependants of a time slot | missing-test | Test `deleting a slot a constraint refers to is blocked and names the constraint by the slot label` | fixed |
| F-103 | persistence | `ExposedConstraintRepositories.kt` `findReferencingSubject`, fixation `delete`/`findByClass`/`findBySubject`; `ExposedSchoolRepository.findAllIds` | missing-test | Assertions for `findReferencingSubject`, fixations `findByClass`/`findBySubject`, test `a deleted teacher fixation is gone` (`SchedulingConstraintRepositoryIT`), test `lists the ids of all schools` (`SchoolRepositoryIT`) | fixed |
| F-104 | web | `ClassLevelRoutes.kt` update and delete of a class level, update of a school class | missing-test | Tests `a class level's curriculum is changed and the level deleted …` and `a school class is changed …` (`CurriculumRoutesTest`) | fixed |
| F-105 | web | `SubjectRoutes.kt` update of a subject | missing-test | Test `a subject is renamed, and an empty name shows the form again` (`CurriculumRoutesTest`) | fixed |
| F-106 | web | `TeacherRoutes.kt` update of the general data | missing-test | Test `the general data of a teacher is changed …` (`TeacherRoutesTest`) | fixed |
| F-107 | web | `TimeGridRoutes.kt` update of a time slot | missing-test | Test `a time slot is changed, a time that is no time shows the editor again` (`TimeGridRoutesTest`) | fixed |
| F-108 | web | `ConstraintRoutes.kt` update and delete of a teacher fixation | missing-test | Test `the teacher of a fixation is changed and the fixation deleted …` (`ConstraintRoutesTest`) | fixed |
| F-109 | web | `ConstraintForm.kt` parsing and edit values of 7 of the 10 constraint types | missing-test | New `ConstraintFormTest`: the edit values of all 12 rule variants of the catalogue are read back as the same rule; format errors of a limit and a missing teacher | fixed |
| F-110 | web | invalid-fixation texts (`invalidityReason` DE/EN, readiness issue of a fixation), contradiction page, `EnglishTexts.hardRuleName` | missing-test | `UiTextsTest` checks every hard rule name and every invalidity reason in both languages (and the fixation readiness heading); route tests for a fixation that no longer fits (list and readiness page) and for the contradiction page at start (`GenerationRoutesTest`) | fixed |
| F-111 | solver | `ProblemMapping.ruleFactsOf` separate objectives | missing-test | Test `a separate objective gets its own index and normalizer, the other soft constraints share the next one` (`ProblemMappingTest`) | fixed |
| F-112 | domain | `InfeasibilityPreCheck` excluded slots of a subject | missing-test | Test `hard subject exclusions … for the class they name only` in `InfeasibilityPreCheckTest` | fixed |
| F-113 | bootstrap | `Main.kt` `ensureInstalled` | missing-test | New `EnsureInstalledTest` (fakes of the application test fixtures): first start, empty database without setup, later start, rejected setup without revealing the values | fixed |
| F-114 | domain | `RuleViolations.kt`, `InfeasibilityPreCheck.kt` | clean-code | The scope of a subject exclusion was written out three times; the slot kinds of a day were named `breakFree`; a fully qualified type | `SubjectExcludedSlots.appliesTo(course)` with its own test written first (`ExcludedSlotsRulesTest`), used in both places; renamed to `slotKinds`; import | fixed |
| F-115 | domain | `ParetoArchive.offer`, `removeMostCrowded`, `RuleViolations.gapsPerDay`, `unpairedLessons` | layout (FR-002) | Setup, loop and result of these functions were not separated by blank lines | Blank lines between the steps | fixed |
| F-116 | application | `GenerationRunner.run` | clean-code | One function of about 45 lines solved, derived the end status, built the ended generation, stored and logged | `solve` and `endedGeneration` extracted (private, covered by `GenerationRunnerTest` before and after); class comment re-wrapped; blank line before the offer step | fixed |
| F-117 | application | `GetSchoolReadiness.ReadinessReportLoader.load` (35 lines) | clean-code | Longer than the guide of about 30 lines | Justified: one level of abstraction — it loads the school's master data and assembles one report; splitting would only pass the loaded lists around | justified |
| F-118 | persistence | `TimeGridStore.load`, `ExposedTeacherRepository.loadTeachers`, `ExposedGenerationRepository.loadAlternatives` | clean-code | 35–40 lines each: several queries written inline before the result is assembled | One function per query (`loadSlots`, `loadExclusions`, `qualificationsOf`, `unavailabilityOf`, `lessonsOf`, `unplacedOf`, `softViolationsOf`); private, covered by the repository ITs before and after | fixed |
| F-119 | persistence | `ConstraintTables.toRule`/`RuleColumns.of`, `ResultRow.toGeneration`, `constraintSnapshotOf` (33–44 lines) | clean-code | Longer than the guide of about 30 lines | Justified: each is one mapping between a table row and a domain object, a `when` with one short branch per catalogue type; splitting would scatter the mapping | justified |
| F-120 | solver | `ProblemMapping.toPlan` (44 lines), `AppliedRuleFact.covers` (35 lines) | clean-code | `toPlan` built teacher facts, lesson placements and pass weights inline; `covers` repeated the scope check of a subject exclusion and was long only because every branch was braced | `lessonPlacementsOf` and `equalPassWeights` extracted (covered by the solver tests before and after); `covers` one line per branch, using `SubjectExcludedSlots.appliesTo` (F-114) | fixed |
| F-121 | solver | `ProblemMapping.ruleFactsOf` (37 lines) | clean-code | Longer than the guide of about 30 lines | Justified: one step per objective group (separate, combined, hard), explained by its comment and now pinned by `ProblemMappingTest` | justified |
| F-122 | solver | `PlannerConstraintScoringTest` | layout | Auto-format had wrapped a value `if` into blocks inside an argument list and broken a short chain; fully qualified types | Named value, one-line assertion, imports | fixed |
| F-123 | web | `GenerationRoutes.kt` `generationRoutes` (165 lines) | clean-code | One function registered the routes of runs and of alternatives, the start handler parsed its form inline (about 50 lines) | Split into `generationRunRoutes` and `alternativeRoutes`; `parseStart` like `parseSlot`/`parseClass` of the other routes; covered by the route tests before and after | fixed |
| F-124 | web | route registrations `accountRoutes`, `authRoutes`, `classLevelRoutes`, `schoolClassRoutes`, `constraintRoutes`, `fixationRoutes`, `roomRoutes`, `schoolRoutes`, `subjectRoutes`, `teacherRoutes`, `timeGridRoutes` (40–111 lines) | clean-code | Longer than the guide of about 30 lines | Justified: each lists the routes of one resource, one short handler each (≤ 35 lines, read request → use case → answer); the list is one level of abstraction and splitting would only move routes apart. The update branches in `constraintRoutes` were shortened to one line each | justified |
| F-125 | web | `GermanTexts`/`EnglishTexts` `describe` (71 lines), `ConstraintForm.parse` and `ConstraintFormValues.of` (60 lines) | clean-code | Longer than the guide of about 30 lines | Justified: one `when` with a short branch per readiness issue or catalogue type; the round trip of the form is now pinned by `ConstraintFormTest` | justified |
| F-126 | web | `ConstraintViews.respondConstraintList` (52 lines) | clean-code | The page built its three sections inline | `constraintTable` and `fixationList` extracted like `sectionLinks` in `SchoolViews`; page output unchanged (route tests) | fixed |
| F-127 | bootstrap | `Main.kt` `main` | missing-test | The composition root is not executed by a test | Justified: it only wires the modules and starts the server; every part it calls is tested (`AppConfigTest`, `EnsureInstalledTest`, `GenerationInfrastructureTest`, persistence ITs), and it runs at every start of the quickstarts 001 and 002 (T040) | justified |
| F-128 | tooling | `ControlFlowBodyRule.wrapInBlock` (32 lines) | clean-code | Slightly longer than the guide of about 30 lines | Justified: one tree rewrite in three blank-line separated steps, explained by its comment and following ktlint's own `multiline-if-else` auto-correct; covered by `ControlFlowBodyRuleTest` | justified |
