# Implementation Plan: Timetable Generation with Planner-Defined Constraints

**Branch**: `002-timetable-generation` (from `one-school`) | **Date**: 2026-10-09 | **Spec**: [spec.md](./spec.md) | **Constitution**: v1.2.0

**Input**: Feature specification from `specs/002-timetable-generation/spec.md`

## Summary

Planners define scheduling constraints from a catalogue (hard or soft with weight) and optional teacher fixations, start a generation in the background and receive up to 10 equally optimal (Pareto-optimal) alternative timetables for all classes. They compare the alternatives by objective values (table and chart), compare timetables cell by cell and choose one as the school's timetable. Results stay viewable and are marked outdated when master data or constraints change.

Technical approach: the existing Kotlin/Ktor/PostgreSQL application (feature 001) gets a framework-free scheduling core in `domain` (constraint catalogue, hard-constraint checker, objective evaluator, Pareto archive, infeasibility pre-check, input fingerprint) and a new adapter module `adapters:solver` that wraps Timefold Solver Community Edition (G1). Several equally optimal alternatives come from a sequence of weighted-sum solver passes whose intermediate best solutions feed the domain's Pareto archive (G2). Every alternative is re-checked by the domain hard-constraint checker before it is kept (G4). Generations run on a background executor and persist their progress (G6); pages refresh via HTML meta refresh and the trade-off chart is server-rendered SVG, so no new browser script is needed (G6, G10).

## Technical Context

**Language/Version**: Kotlin 2.4, JVM toolchain 21 (unchanged)

**Primary Dependencies**: as feature 001 (Ktor 3, `kotlinx.html`, Exposed, HikariCP, Flyway, PostgreSQL, bcrypt, SLF4J/Logback) + **Timefold Solver Community Edition 2.7.1** (`ai.timefold.solver:timefold-solver-core`, Apache-2.0, Java 21 baseline) in `adapters:solver` only (G1). SHA-256 from the JDK for the input fingerprint (G8).

**Storage**: PostgreSQL; three Flyway migrations, one per story: `V6__home_room_and_generations.sql` (US1), `V7__scheduling_constraints.sql` (US2), `V8__chosen_timetable.sql` (US3) (data-model)

**Testing**: Test-first with `kotlin.test` / JUnit 5; domain tests for every constraint type, checker, evaluator and archive; solver adapter tests on seeded data sets (SC-002 – SC-004); Ktor `testApplication` route tests; Testcontainers repository tests; tagged performance test (G12); quickstart against a running instance

**Target Platform**: JVM 21 on a Linux server; modern desktop browsers (unchanged)

**Project Type**: Web application (server-rendered, single deployable)

**Performance Goals**: first alternative ≤ 2 min, ≥ 3 alternatives within the 10-minute default for an SC-001-sized school, or 1 alternative that fills every class and violates no constraint (G12); all pages incl. comparison and grids < 1 s (001 R15); fingerprint < 50 ms (G8)

**Constraints**: Hard constraints never violated (Constitution IV, SC-002); no confidential data in logs incl. solver logs (G13); one running generation per school; Kotlin/Gradle only, no new browser script

**Scale/Scope**: 1 school, ≤ 100 classes, ≤ 200 teachers, ≤ 60 teaching slots per week, ≤ 100 planner constraints, ≤ 10 alternatives per generation, retained generations unbounded (planner deletes); ~10 constraint types, ~20 new routes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.* Checked against constitution v1.2.0.

| Principle / Constraint | How the plan complies | Pre | Post |
|------------------------|-----------------------|-----|------|
| I. Kotlin Clean Code | Domain names from the spec (`ParetoArchive`, `HardConstraintChecker`, `ObjectiveEvaluator`, `ClassNoGaps`, `TeacherFixation`, `classFill`). Each constraint type evaluates its violations in its own function; compact-but-complex functions (dominance, crowding distance, gap counting, double-period pairing, weight-vector generation, score scaling) carry explanatory comments. | ✅ | ✅ |
| II. Tools/Libraries where appropriate | Timefold for the non-trivial optimization (G1); no library for trivial parts: Pareto dominance, chart (SVG via `kotlinx.html`), diff, fingerprint (JDK SHA-256) are hand-written (G2, G8, G10). | ✅ | ✅ |
| III. Confidentiality (NON-NEGOTIABLE) | Every new use case through `SchoolAccessPolicy` (planner only, FR-025); Timefold logging pinned to `WARN`, runner logs only ids/status/counts/durations, log-leak test with a full generation (G13); solver exceptions → generic message + correlation id; business messages (blocking issues, contradicting constraints, dependants) only on planner pages; pages show only what the view needs. | ✅ | ✅ |
| IV. Pareto-Optimal Scheduling Integrity | Result = non-dominated set incl. equal-value alternatives (FR-007), trade-off table and chart instead of a single answer (FR-020); hard constraints enforced in the solver and independently re-checked by the domain before an alternative is kept (G4); unplaced lessons instead of violations (FR-005). | ✅ | ✅ |
| V. Test-Driven Development (NON-NEGOTIABLE) | Tests first for every domain rule, use case, repository, route and the solver adapter; meaningful seeded scenarios rather than coverage targets. | ✅ | ✅ |
| Tech: Kotlin + Gradle, CI/CD on GitHub | New Gradle module `adapters:solver`; no browser script added (meta refresh, SVG); existing GitHub workflows build and scan the new module unchanged. | ✅ | ✅ |
| Tech: Clean architecture | `domain` (scheduling core, no Timefold) ← `application` (use cases, ports `TimetableSolver`, `GenerationRepository`, …) ← `adapters:solver` / `adapters:persistence` / `adapters:web` ← `bootstrap` (executor, wiring, restart recovery). Timefold types never leave `adapters:solver` (G5). | ✅ | ✅ |
| Tech: Transactional DB with access control | PostgreSQL; use cases in one transaction; generation progress and archive changes in short transactions of their own (G6); least-privilege user unchanged. | ✅ | ✅ |
| Tech: Secrets not in repo | No new secrets. | ✅ | ✅ |
| Tech: Security testing | CodeQL and dependency submission cover the new module and the Timefold dependency (GitHub Advanced Security SCA). | ✅ | ✅ |
| Gate: Review criteria | As 001; plus review that no lesson data reaches logs. | ✅ | ✅ |
| Gate: Tests for constraint and weighting logic | Required by the constitution for this feature: tests per constraint type (violation counting, hard rejection), checker tests per hard rule, solver tests asserting 0 hard violations on seeded data, `ParetoArchive` tests (dominance, equal values kept, crowding pruning), evaluator tests for normalization and weights. | ✅ | ✅ |
| Gate: Migrations with entity changes | One migration per story (V6 US1, V7 US2, V8 US3), each in the same change set as its entities. | ✅ | ✅ |
| Gate: UI verified on running instance | Quickstart scenarios 1–27 against `:bootstrap:run`. | ✅ | ✅ |
| Gate: Feature branch | Own branch `002-timetable-generation`, created from `one-school` because it needs feature 001's code (which is not yet merged to `main`). | ✅ | ✅ |

**Result**: No violations. Complexity Tracking not required.

**Clarifications applied (spec session 2026-10-09)**: catalogue of distribution rules + optional teacher fixations (G9, FR-013a); up to 3 soft constraints as separate objectives, others combined by weight (G3, FR-006a); version ends with choosing a timetable (no teacher view, no manual editing); optional home room per class, hard for subjects without required features (FR-002a, G5, extends 001's class); incomplete alternatives can be chosen after confirming the unplaced lessons (FR-021); optional start from the chosen timetable with "changed lessons" as additional objective (FR-006b, G3, G5); school-wide constraints and two soft defaults seeded with the school (FR-013b, FR-013c, G9).

## Project Structure

### Documentation (this feature)

```text
specs/002-timetable-generation/
├── plan.md              # This file
├── research.md          # Phase 0: decisions G1–G14
├── data-model.md        # Phase 1: domain types, schema V6, dependants, readiness issue
├── quickstart.md        # Phase 1: run and validation guide (scenarios 1–27)
├── contracts/
│   └── web-routes.md    # Phase 1: HTTP routes and form contracts
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
settings.gradle.kts                 # + "adapters:solver"
gradle/libs.versions.toml           # + timefold = "2.7.1"

domain/src/main/kotlin/timetablescheduler/domain/
├── scheduling/
│   ├── constraint/                 # SchedulingConstraint, ConstraintRule (sealed, one file per type), ConstraintKind,
│   │                               # SoftWeight, DaySlot, ClassScope, EffectiveConstraints, DefaultConstraints,
│   │                               # TeacherFixation, constraint validation
│   ├── problem/                    # SchedulingProblem, SchedulingInputFingerprint
│   ├── solution/                   # LessonAssignment, TimetableCandidate, ObjectiveValues, TimetableDiff
│   ├── evaluation/                 # HardConstraintChecker, ObjectiveEvaluator, violation counters per rule
│   ├── pareto/                     # Dominance, ParetoArchive (crowding distance), WeightVectors
│   └── generation/                 # Generation, GenerationStatus, GenerationGate, InfeasibilityPreCheck
└── readiness/                      # + InvalidTeacherFixation issue

application/src/main/kotlin/timetablescheduler/application/
├── port/                           # + SchedulingConstraintRepository, TeacherFixationRepository, GenerationRepository,
│                                   #   ChosenTimetableRepository, TimetableSolver, GenerationExecutor
├── scheduling/                     # constraint & fixation use cases, SchedulingProblemBuilder,
│                                   # StartGeneration, StopGeneration, GenerationRunner, GetGeneration, ListGenerations,
│                                   # CompareAlternatives, GetAlternative, ChooseAlternative, DeleteGeneration,
│                                   # GetChosenTimetable, RecoverInterruptedGenerations
└── (testFixtures)                  # in-memory fakes + a deterministic FakeTimetableSolver

adapters/solver/                    # NEW: Timefold adapter
└── src/
    ├── main/kotlin/timetablescheduler/adapters/solver/
    │   ├── model/                  # CourseAssignment, LessonPlacement, TimetablePlan (@PlanningSolution), facts
    │   ├── constraints/            # TimetableConstraintProvider: hard streams + one stream per catalogue type
    │   └── TimefoldTimetableSolver.kt   # implements TimetableSolver: passes, weight vectors, warm start, stop flag
    └── test/kotlin/...             # seeded scenarios: 0 hard violations, non-dominance, full placement; performance (tagged)

adapters/persistence/
├── src/main/kotlin/.../scheduling/ # Exposed tables + repositories
└── src/main/resources/db/migration/V6__home_room_and_generations.sql, V7__scheduling_constraints.sql, V8__chosen_timetable.sql

adapters/web/src/main/kotlin/timetablescheduler/adapters/web/
├── constraint/                     # routes + views (catalogue forms, slot grid)
├── generation/                     # list/start, progress, result table, comparison (SVG chart)
├── timetable/                      # weekly grid component (class/teacher/room), diff, chosen timetable
└── i18n/                           # + ConstraintTexts, GenerationTexts, TimetableTexts in GermanTexts/EnglishTexts

bootstrap/                          # wiring, single-thread GenerationExecutor, RecoverInterruptedGenerations at start,
                                    # logback: ai.timefold → WARN
```

**Structure Decision**: Extend the existing multi-module layout (001 R12) with one adapter module for the solver, so that the optimization library is replaceable and the scheduling rules stay testable without it.

## Design Notes

- **Generation flow**: `StartGeneration` (planner, in a transaction) checks access, readiness gate (G11), single running generation, separate-objective selection, `InfeasibilityPreCheck`; builds the `SchedulingProblem`, stores the generation with fingerprint as `RUNNING` and hands it to `GenerationExecutor`. `GenerationRunner` calls `TimetableSolver.solve(problem, timeLimit, stopSignal, onCandidate)`; each candidate → `HardConstraintChecker` → `ObjectiveEvaluator` → `ParetoArchive`; archive changes are persisted immediately. End: `FINISHED`, `STOPPED`, `FAILED` or — only if no single valid alternative was admitted and hard constraints could not be met — `FAILED_INFEASIBLE` (G4).
- **Fake solver for tests**: application and web tests use a deterministic `FakeTimetableSolver` from test fixtures; only `adapters:solver` tests run Timefold.
- **Outdated**: computed per request from `SchedulingInputFingerprint` (G8); hard-violation highlighting of outdated alternatives uses the current problem and `HardConstraintChecker`.
- **Chosen timetable**: `ChooseAlternative` re-checks the alternative against current data (refuses with named conflicts) and requires confirmation of unplaced lessons, and copies its lessons and unplaced lessons into `chosen_lesson` / `chosen_unplaced` with restricting references (G7); concurrent choices detected via `chosen_timetable.version`.
- **Home room (extends 001)**: `SchoolClass.homeRoomId` with uniqueness rule, class form field, room-deletion dependant; enforced structurally in the solver and by `HardConstraintChecker`.
- **Defaults and scope**: installation (bootstrap) and migration V6 seed the two default constraints; `EffectiveConstraints` resolves school-wide vs. class-specific constraints for the problem.
- **Dependants**: new `DependantSource`s for constraints, fixations and the chosen timetable plug into the existing deletion use cases (001 FR-023).
- **Readiness**: `INVALID_TEACHER_FIXATION` added to the readiness check and page; the generation gate reuses the readiness report.
- **Interface texts**: new `UiTexts` areas in both languages; constraint descriptions rendered from typed rules (G14).

## Complexity Tracking

Not required — the Constitution Check has no violations.
