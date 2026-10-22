---

description: "Task list for Timetable Generation with Planner-Defined Constraints"
---

# Tasks: Timetable Generation with Planner-Defined Constraints

**Input**: Design documents from `specs/002-timetable-generation/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/web-routes.md](./contracts/web-routes.md), [quickstart.md](./quickstart.md)

**Tests**: Mandatory. Constitution v1.2.0 Principle V (Test-Driven Development, NON-NEGOTIABLE) requires meaningful tests written before the implementation; the quality gate for scheduling additionally requires tests showing that hard constraints are never violated and that Pareto comparisons behave as intended. Within each phase, write the test tasks first and make sure they fail before implementing.

**Organization**: Tasks are grouped by user story in priority order: US1 (P1, MVP) generate, US2 (P2) constraints, US3 (P3) compare and choose, US4 (P4) outdated results. The framework-free scheduling core (problem, checker, evaluator, Pareto archive) is foundational because every story needs it.

**Migrations** (one per story, constitution quality gate): `V6__home_room_and_generations.sql` (US1), `V7__scheduling_constraints.sql` (US2), `V8__chosen_timetable.sql` (US3). Table definitions: [data-model.md](./data-model.md#relational-schema-migrations-v6v8).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story from spec.md (US1–US4)

## Path Conventions

As in feature 001. Kotlin base package `timetablescheduler`. Abbreviations:

- `DOM` = `domain/src/main/kotlin/timetablescheduler/domain`, `DOM_T` = `domain/src/test/kotlin/timetablescheduler/domain`
- `APP` = `application/src/main/kotlin/timetablescheduler/application`, `APP_T` = `application/src/test/kotlin/timetablescheduler/application`, `APP_F` = `application/src/testFixtures/kotlin/timetablescheduler/application`
- `SOL` = `adapters/solver/src/main/kotlin/timetablescheduler/adapters/solver`, `SOL_T` = `adapters/solver/src/test/kotlin/timetablescheduler/adapters/solver`
- `PER` = `adapters/persistence/src/main/kotlin/timetablescheduler/adapters/persistence`, `PER_R` = `adapters/persistence/src/main/resources/db/migration`, `PER_T` = `adapters/persistence/src/test/kotlin/timetablescheduler/adapters/persistence`
- `WEB` = `adapters/web/src/main/kotlin/timetablescheduler/adapters/web`, `WEB_T` = `adapters/web/src/test/kotlin/timetablescheduler/adapters/web`
- `BOOT` = `bootstrap/src/main/kotlin/timetablescheduler/bootstrap`

**Conventions for every task** (constitution I, III; feature 001 conventions continue):

- Speaking domain names, small single-purpose functions; an explanatory comment on every compact-but-complex function (dominance, crowding distance, gap counting, double-period pairing, weight vectors, score scaling, multiset lesson matching).
- Domain and application stay language-free and Timefold-free; Timefold types never leave `adapters:solver` (G5).
- Logs contain only ids, statuses, counts and durations — never names, lessons or constraint descriptions (G13, FR-026). Business errors may name entries only on planner pages.
- Every new use case goes through `SchoolAccessPolicy` (planner only, FR-025); every new interface text exists in `GermanTexts` and `EnglishTexts` (FR-027).
- Application and web tests use the deterministic `FakeTimetableSolver`; only `SOL_T` runs Timefold.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Solver module, dependency, logging and test task

- [X] T001 Add `timefold = "2.7.1"` with library `timefold-solver-core` (`ai.timefold.solver:timefold-solver-core`) to `gradle/libs.versions.toml`; verify on Maven Central that 2.7.1 is the current stable release and adjust if not (G1). In 2.x `ConstraintVerifier` is part of the core artifact; there is no separate test artifact
- [X] T002 Include `adapters:solver` in `settings.gradle.kts` and create `adapters/solver/build.gradle.kts` depending on `:application`, `:domain`, `timefold-solver-core`, `slf4j-api`; test deps `testFixtures(project(":application"))`, `logback-classic`
- [X] T003 Register a `performanceTest` Gradle task in `adapters/solver/build.gradle.kts` that runs only JUnit tests tagged `performance`, and exclude that tag from the normal `test` task (G12)
- [X] T004 Add `implementation(project(":adapters:solver"))` to `bootstrap/build.gradle.kts`; pin `<logger name="ai.timefold" level="WARN"/>` in `bootstrap/src/main/resources/logback.xml` and extend `bootstrap/src/test/kotlin/timetablescheduler/bootstrap/LogbackConfigurationTest.kt` to assert it (G13); verify `./gradlew build` is green

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Framework-free scheduling core in the domain, ports, problem builder and fakes

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundation (write first, must fail)

- [X] T005 [P] Dominance and archive tests in `DOM_T/scheduling/pareto/ParetoArchiveTest.kt`: a candidate better or equal in all objectives and better in one dominates; a dominated offer is rejected and a dominating offer evicts the dominated ones; two candidates with identical objective values but different lessons are both kept, identical lessons are kept once (FR-007); beyond capacity (default 10, FR-008) the candidate with the smallest crowding distance is evicted and the extreme candidates of each objective are always kept; values compared after rounding to 4 decimals (G3)
- [X] T006 [P] Weight vector tests in `DOM_T/scheduling/pareto/WeightVectorsTest.kt`: first vector equal weights, then one vector emphasizing each objective, then evenly spread vectors; every vector sums to 1; count = 2 + objectives + 2 (G2, G12)
- [X] T007 [P] Hard rule tests in `DOM_T/scheduling/evaluation/HardConstraintCheckerTest.kt`, one test per rule with a minimal problem: teacher, class or room booked twice in a day-slot; teacher not qualified; room lacks a required feature; lesson of a subject without required features outside the class's home room (FR-002a); lesson in a break slot or on a non-teaching day; teacher above weekly load; lesson in a slot where its teacher is unavailable; two teachers for the same class and subject (FR-004); valid candidate → no violations; every violation names the lesson and the rule (FR-003)
- [X] T008 [P] Objective tests in `DOM_T/scheduling/evaluation/ObjectiveEvaluatorTest.kt`: class fill weighted by priority weights (e.g., weight 2 class missing 1 of 10 periods vs. weight 1 class missing 1 of 10), teacher load usage = Σ min(assigned, load) / Σ load ignoring load 0 and 1 when no teacher has a load, other soft constraints = 1 without soft constraints, rounding to 4 decimals (G3)
- [X] T009 [P] Fingerprint tests in `DOM_T/scheduling/problem/SchedulingInputFingerprintTest.kt`: same problem in different list orders → same hash; changing any of teacher unavailability, load, qualification, curriculum periods, room feature, time slot, class weight or home room → different hash (G8)
- [X] T010 [P] Home room domain tests in `DOM_T/curriculum/SchoolClassTest.kt`: class with and without `homeRoomId`; `withHomeRoom` keeps all other fields (FR-002a)
- [X] T011 [P] Problem builder tests in `APP_T/scheduling/SchedulingProblemBuilderTest.kt` (with `MasterDataWorld`): teaching day-slots exclude breaks and per-day removed slots, classes carry weight, home room and the curriculum from `ClassCurriculumResolver`, teachers carry qualifications, load and unavailable day-slots, unavailabilities on removed days are ignored (001 clarification)

### Implementation for Foundation

- [X] T012 [P] Create value types `DaySlot` (`TeachingDay`, `TimeSlotId`), `LessonAssignment` (`classId`, `subjectId`, `teacherId`, `roomId`, `daySlot`), `TimetableCandidate` (lessons + `unplaced: Map<CourseKey, Int>`, `CourseKey` = class + subject) and `ObjectiveValues` (class fill, teacher load usage, up to 3 separate values, other soft, optional stability; 4-decimal rounding) in `DOM/scheduling/solution/`
- [X] T013 [P] Create `SchedulingProblem` with `ProblemClass`, `ProblemTeacher`, `ProblemRoom`, `ProblemSubject` and `requiredPeriods(courseKey)` in `DOM/scheduling/problem/SchedulingProblem.kt`; add the sealed interface `ConstraintRule` and the aggregate `SchedulingConstraint` (`id`, `schoolId`, `kind` HARD|SOFT, `weight` LOW|MEDIUM|HIGH only for SOFT, `active`, `rule`, `version`) without subtypes yet in `DOM/scheduling/constraint/SchedulingConstraint.kt`; the problem holds an (empty for now) list of effective constraints and fixations
- [X] T014 Implement `Dominance` and `ParetoArchive` (offer → admitted/rejected/evicted ids, crowding distance pruning, identical-value duplicates kept) in `DOM/scheduling/pareto/` to pass T005
- [X] T015 [P] Implement `WeightVectors` in `DOM/scheduling/pareto/WeightVectors.kt` to pass T006
- [X] T016 Implement `HardConstraintChecker` with one function per rule returning typed `HardViolation`s in `DOM/scheduling/evaluation/HardConstraintChecker.kt` to pass T007 (planner constraints and fixations are added in US2)
- [X] T017 Implement `ObjectiveEvaluator` in `DOM/scheduling/evaluation/ObjectiveEvaluator.kt` to pass T008
- [X] T018 [P] Implement `SchedulingInputFingerprint` (SHA-256 over a canonical, sorted text form) in `DOM/scheduling/problem/SchedulingInputFingerprint.kt` to pass T009
- [X] T019 [P] Add `homeRoomId: RoomId?` and `withHomeRoom` to `DOM/curriculum/SchoolClass.kt` to pass T010
- [X] T020 Define ports in `APP/port/SchedulingPorts.kt`: `TimetableSolver.solve(problem, objectiveSetup, timeLimit, stopSignal, onCandidate)`, `GenerationExecutor`, `StopSignal`, `SolverListener` (the `GenerationRepository` port follows with the `Generation` aggregate in US1, T034/T041)
- [X] T021 Implement `SchedulingProblemBuilder` in `APP/scheduling/SchedulingProblemBuilder.kt` to pass T011
- [X] T022 Add `FakeTimetableSolver` (returns scripted candidates, honours the stop signal), `ImmediateGenerationExecutor` (`InMemoryGenerationRepository` follows in US1) to `APP_F/fakes/SchedulingFakes.kt` and expose them through `APP_F/fakes/MasterDataWorld.kt`

**Checkpoint**: `./gradlew :domain:test :application:test` green; scheduling core usable without Timefold

---

## Phase 3: User Story 1 - Generate equally optimal timetables (Priority: P1) 🎯 MVP

**Goal**: Planners start a generation in the background and see up to 10 non-dominated alternatives that never violate a hard constraint, each viewable per class, teacher and room; classes may have a home room.

**Independent Test**: With ready data, start a generation, wait for it to finish, verify ≥ 1 alternative, 0 hard violations, no dominated alternative (quickstart 0–6, 4a).

### Tests for User Story 1 (write first, must fail)

- [X] T023 [P] [US1] Gate tests in `DOM_T/scheduling/generation/GenerationGateTest.kt`: `TEACHER_LOAD_EXCEEDS_GRID` and `LEVEL_EXCEEDS_GRID` are blocking, all other readiness issues are warnings (G11, FR-010)
- [X] T024 [P] [US1] Generation aggregate tests in `DOM_T/scheduling/generation/GenerationTest.kt`: `timeLimitMinutes` must be 1…60 (default 10, FR-009); transitions only `RUNNING` → `FINISHED` | `STOPPED` | `FAILED` | `FAILED_INFEASIBLE`
- [X] T025 [P] [US1] Solver adapter tests in `SOL_T/TimefoldTimetableSolverTest.kt` on seeded data sets (`SOL_T/SeededSchools.kt`): (a) every candidate passed to `onCandidate` has 0 violations by `HardConstraintChecker` (SC-002); (b) a school with a known complete solution yields a candidate placing 100 % of lessons (SC-004); (c) an over-constrained school yields candidates with unplaced lessons instead of violations (FR-005); (d) home-room lessons only in the home room (FR-002a); (e) a stop signal ends solving within 2 s; (f) all periods of a class's subject have one teacher (FR-004); (g) on a school with competing objectives, the candidates kept by a `ParetoArchive` fed from `onCandidate` are pairwise non-dominated (SC-003) — use short time limits (≤ 20 s per test)
- [X] T026 [P] [US1] Start tests in `APP_T/scheduling/StartGenerationTest.kt`: teacher role → access denied; blocking readiness issue → refused naming it; warnings without confirmation → invalid; running generation exists → refused with its id (FR-011); valid → generation `RUNNING` with fingerprint, handed to the executor
- [X] T027 [P] [US1] Runner tests in `APP_T/scheduling/GenerationRunnerTest.kt` with `FakeTimetableSolver`: candidates violating a hard rule are discarded (count logged, no data); admitted candidates are persisted with objective values and unplaced lessons, evicted ones removed; progress count updated; normal end → `FINISHED`, stop → `STOPPED` with alternatives kept, solver exception → `FAILED` with technical log entry (exception class + generation id only)
- [X] T028 [P] [US1] Recovery tests in `APP_T/scheduling/RecoverInterruptedGenerationsTest.kt`: `RUNNING` generations become `FAILED` at start-up, their stored alternatives remain
- [X] T029 [P] [US1] Home room use case tests in `APP_T/curriculum/SchoolClassUseCasesTest.kt`: set, change and clear a home room; room already home room of another class → `Invalid` naming that class; deleting a room that is a home room → `DeletionBlocked` listing the class
- [X] T030 [P] [US1] Repository tests in `PER_T/scheduling/ExposedGenerationRepositoryTest.kt` (Testcontainers): store/load generation with alternatives, lessons and unplaced lessons; second `RUNNING` generation for the school fails on the partial unique index; removing an evicted alternative cascades its lessons; `school_class.home_room_id` unique and restricting in `PER_T/curriculum/CurriculumRepositoryIT.kt`
- [X] T031 [P] [US1] Route tests in `WEB_T/generation/GenerationRoutesTest.kt`: list page with start form; `POST /schools/{s}/generations` → `303`, invalid limit → `422`, blocking issue / running generation → `409` business page; running page contains `<meta http-equiv="refresh" content="3">` and stop button; stop → `303` and status stopped; result table sortable by `?sort=`; teacher → `403`, foreign school → `404`
- [X] T032 [P] [US1] Route tests in `WEB_T/generation/AlternativeRoutesTest.kt`: grid per class (default first class), teacher and room with subject, class, short code and room; break rows marked as breaks; unplaced lessons per class listed; unknown alternative → `404`
- [X] T033 [P] [US1] Class form test in `WEB_T/curriculum/CurriculumRoutesTest.kt`: `homeRoomId` select saves; duplicate home room → `422` naming the other class

### Implementation for User Story 1

- [X] T034 [P] [US1] Implement `GenerationGate` in `DOM/scheduling/generation/GenerationGate.kt` and `Generation`/`GenerationStatus` in `DOM/scheduling/generation/Generation.kt` to pass T023, T024
- [X] T035 [P] [US1] Create the Timefold model in `SOL/model/`: `TimetablePlan` (`@PlanningSolution`, problem facts, `HardSoftScore` (long-based in 2.x): one hard level for all hard constraints, one soft level for the weighted sum of the pass (G2, G5)), `CourseAssignment` (class, subject, `@PlanningVariable teacher`, `@PlanningPin` for fixations later), `LessonPlacement` (one per required period; `timeslot` and `room` variables with `allowsUnassigned = true`; room value range restricted to the home room for subjects without required features); keep annotations inside the module (G5)
- [X] T036 [US1] Implement `ProblemMapping` in `SOL/ProblemMapping.kt` (SchedulingProblem → `TimetablePlan`, `TimetablePlan` → `TimetableCandidate` with unplaced lessons)
- [X] T037 [US1] Implement `TimetableConstraintProvider` in `SOL/constraints/TimetableConstraintProvider.kt`: hard streams for every rule of T007, soft stream = weighted sum of the normalized objectives of the current pass (class fill, teacher load usage; score scaling commented)
- [X] T038 [US1] Implement `TimefoldTimetableSolver` in `SOL/TimefoldTimetableSolver.kt`: passes from `WeightVectors`, time limit split evenly with ≥ max(60 s, limit / passes) for the first pass, warm start from the previous best, every new best solution mapped and passed to `onCandidate`, stop signal → `terminateEarly()` (G2, G12); passes T025
- [X] T039 [US1] Create `PER_R/V6__home_room_and_generations.sql`: `school_class.home_room_id` (`UUID NULL REFERENCES room ON DELETE RESTRICT`, `UNIQUE`), tables `generation` (incl. `time_limit_minutes INT CHECK (time_limit_minutes BETWEEN 1 AND 60)`, `status VARCHAR(17)`, `input_fingerprint CHAR(64)`, partial unique index on `school_id WHERE status = 'RUNNING'`), `alternative` (objective columns `NUMERIC(5,4)`), `alternative_lesson` (no foreign keys to master data, G7), `alternative_unplaced`, `alternative_soft_violation`
- [X] T040 [US1] Map `home_room_id` in `PER/curriculum/CurriculumTables.kt` and `PER/curriculum/ExposedCurriculumRepositories.kt`; add room-deletion dependant query for home rooms
- [X] T041 [US1] Implement `PER/scheduling/GenerationTables.kt` and `PER/scheduling/ExposedGenerationRepository.kt` to pass T030
- [X] T042 [US1] Implement home room handling (set/clear, uniqueness, room dependant source) in `APP/curriculum/SchoolClassUseCases.kt` and `APP/room/RoomUseCases.kt` to pass T029
- [X] T043 [US1] Implement `StartGeneration`, `StopGeneration`, `GenerationRunner`, `RecoverInterruptedGenerations`, `ListGenerations`, `GetGeneration`, `GetAlternative` in `APP/scheduling/` to pass T026–T028; wire them into `APP/MasterDataUseCases.kt`
- [X] T044 [US1] Add `SingleThreadGenerationExecutor` in `BOOT/SingleThreadGenerationExecutor.kt`; wire `TimefoldTimetableSolver`, the repository and use cases in `BOOT/Main.kt`; run `RecoverInterruptedGenerations` at start-up before serving requests
- [X] T045 [P] [US1] Add `GenerationTexts` and `TimetableTexts` areas (statuses, progress, objective names, start form, grid labels, unplaced lessons, business messages) to `WEB/i18n/UiTexts.kt`, `WEB/i18n/GermanTexts.kt`, `WEB/i18n/EnglishTexts.kt`
- [X] T046 [US1] Add the home room select to the class form in `WEB/classlevel/ClassLevelViews.kt` / `WEB/classlevel/ClassLevelRoutes.kt` to pass T033
- [X] T047 [US1] Create the reusable weekly grid component (days × slots, breaks, lesson cell, optional highlight) in `WEB/timetable/TimetableGrid.kt`
- [X] T048 [US1] Implement `WEB/generation/GenerationRoutes.kt` and `WEB/generation/GenerationViews.kt` (list, start form with time limit and warnings confirmation, progress with meta refresh, stop, result table with sorting) to pass T031
- [X] T049 [US1] Implement `WEB/generation/AlternativeRoutes.kt` and `WEB/generation/AlternativeViews.kt` (class/teacher/room views, objective values, unplaced lessons) to pass T032
- [X] T050 [US1] Add navigation entry *Stundenplanläufe* / *Generations* in `WEB/layout/PageLayout.kt` and register the routes in `WEB/WebModule.kt`
- [X] T051 [US1] Run quickstart scenarios 0 (home room part), 1–6 and 4a against `:bootstrap:run` with a fresh database — 2026-10-09 on a separate database `timetable_002`: 20/20 checks passed; first alternative after ~1 s, 100 % of 52 lessons placed within the 1-minute limit (after making the room always assigned in the solver model, which raised it from 92 %)

**Checkpoint**: US1 delivers the MVP: valid, equally optimal timetables viewable per class, teacher and room

---

## Phase 4: User Story 2 - Define scheduling constraints (Priority: P2)

**Goal**: Planners manage hard and soft constraints from the catalogue (per class or for all classes), teacher fixations and two seeded defaults; generations respect them and can compare up to 3 soft constraints as separate objectives.

**Independent Test**: Define a hard and a soft constraint and a fixation, generate, verify the hard ones hold and the soft one is reported per alternative (quickstart 0, 7–17, 16a).

### Tests for User Story 2 (write first, must fail)

- [X] T052 [P] [US2] Day-limit rule tests in `DOM_T/scheduling/constraint/DayLimitRulesTest.kt`: `ClassMaxPeriodsPerDay`, `ClassMinPeriodsPerDay`, `TeacherMaxPeriodsPerDay` — validation (`1 ≤ limit ≤ teaching slots of the longest teaching day`, class/teacher exists, hard min ≤ hard max of the same class) and violation counting (excess / missing periods per day)
- [X] T053 [P] [US2] Gap rule tests in `DOM_T/scheduling/constraint/GapRulesTest.kt`: `ClassNoGaps` counts free teaching slots between first and last lesson of a day, breaks never count; `TeacherMaxGapsPerDay` (`0 ≤ limit ≤ teaching slots per day − 2`) counts gaps above the limit
- [X] T054 [P] [US2] Subject rule tests in `DOM_T/scheduling/constraint/SubjectRulesTest.kt`: `SubjectMaxPerDayInClass` (`subjectId` null = every subject; for a specific class the subject must be in its curriculum; `1 ≤ limit ≤ teaching slots per day`); `SubjectDoublePeriodsInClass` (subject in curriculum with ≥ 2 weekly periods; lesson not part of a consecutive pair counts, one single allowed for an odd number; pairs across a break only when `acrossBreaks`)
- [X] T055 [P] [US2] Excluded-slot rule tests in `DOM_T/scheduling/constraint/ExcludedSlotsRulesTest.kt`: `ClassExcludedSlots`, `TeacherExcludedSlots`, `SubjectExcludedSlots` (optional class with the subject in its curriculum) — ≥ 1 slot, every slot a teaching slot on a teaching day; one violation per lesson in an excluded slot
- [X] T056 [P] [US2] Scope tests in `DOM_T/scheduling/constraint/EffectiveConstraintsTest.kt`: an `AllClasses` constraint applies to every class incl. classes created later; a `SpecificClass` constraint of the same type (and for `SubjectMaxPerDayInClass` the same subject or every subject) replaces it for that class only; switched-off constraints are ignored (FR-013b, US2 scenario 9); `DefaultConstraints` = `SubjectMaxPerDayInClass(AllClasses, every subject, 2)` and `ClassNoGaps(AllClasses)`, both SOFT/MEDIUM/active (FR-013c)
- [X] T057 [P] [US2] Fixation tests in `DOM_T/scheduling/constraint/TeacherFixationTest.kt`: rejected if the teacher is not qualified, the subject is not in the class's curriculum, or Σ fixed weekly periods of the teacher > weekly load (FR-013a); validity re-check after master data changes yields reason `NOT_QUALIFIED` | `NOT_IN_CURRICULUM` | `EXCEEDS_LOAD`
- [X] T058 [P] [US2] Pre-check tests in `DOM_T/scheduling/generation/InfeasibilityPreCheckTest.kt`: hard min per day > teaching slots of a day; hard min × teaching days > curriculum periods of the class; hard excluded slots leaving fewer slots than required periods of a class or fixed periods of a teacher — each names the constraints involved (FR-017)
- [X] T059 [P] [US2] Extend `DOM_T/scheduling/evaluation/HardConstraintCheckerTest.kt` (active hard planner constraints of every type, fixations) and `DOM_T/scheduling/evaluation/ObjectiveEvaluatorTest.kt` (separate objective k = 1 − violations_k / maxViolations_k; other soft = 1 − Σ w·v / Σ w·max with weights LOW 1, MEDIUM 3, HIGH 9; violation counts per constraint) (G3)
- [X] T060 [P] [US2] Readiness test in `DOM_T/readiness/ReadinessCheckTest.kt` and gate test in `DOM_T/scheduling/generation/GenerationGateTest.kt`: invalid fixation → `INVALID_TEACHER_FIXATION`, which is blocking (G11)
- [X] T061 [P] [US2] Constraint provider tests in `SOL_T/constraints/TimetableConstraintProviderTest.kt` with Timefold's `ConstraintVerifier`: each catalogue type penalizes exactly the violations counted by the domain rule (hard → hard score, soft → weighted soft); fixed `CourseAssignment` stays pinned; solver test with a hard `ClassMaxPeriodsPerDay` and a fixation: 0 violations, fixed teacher on every lesson (US2 scenarios 3, 10)
- [X] T062 [P] [US2] Use case tests in `APP_T/scheduling/SchedulingConstraintUseCasesTest.kt` and `APP_T/scheduling/TeacherFixationUseCasesTest.kt`: create/update/switch off/on/delete with optimistic locking (FR-028); invalid parameters → `Invalid` naming the problem (FR-015); duplicate fixation for class + subject → `Invalid`
- [X] T063 [P] [US2] Dependant tests in `APP_T/curriculum/` and `APP_T/teacher/` use case tests: deleting a class, teacher, subject or time slot referenced by a constraint or fixation → `DeletionBlocked` listing it with its description (FR-016); deleting a constraint is never blocked
- [X] T064 [P] [US2] Start tests in `APP_T/scheduling/StartGenerationTest.kt`: up to 3 active soft constraints as separate objectives, 4 → `Invalid`, hard or inactive → `Invalid` (FR-006a); pre-check contradictions → refused naming the constraints; a started generation stores a snapshot of all active constraints (incl. the defaults) and all fixations, switched-off constraints are not included (FR-022); runner (`APP_T/scheduling/GenerationRunnerTest.kt`): passes reporting violated hard constraints while no valid candidate was admitted → `FAILED_INFEASIBLE` with constraint ids; the same infeasible passes after at least one admitted valid alternative → `FINISHED` (or `STOPPED`) with that alternative kept, never `FAILED_INFEASIBLE` (G4); installation creates the two defaults (`APP_T/account/InstallationUseCaseTest.kt`)
- [X] T065 [P] [US2] Repository tests in `PER_T/scheduling/ExposedSchedulingConstraintRepositoryTest.kt`: each type round-trips incl. slots and `all_classes`; `CHECK ((kind = 'SOFT') = (weight IS NOT NULL))`; restricting foreign keys; migration V7 seeds the two defaults for the existing school; fixation uniqueness `(class_id, subject_id)`; in `PER_T/scheduling/ExposedGenerationRepositoryTest.kt` the constraint and fixation snapshot of a generation round-trips and stays unchanged after the original constraint is changed or deleted and after a referenced class is deleted (FR-022)
- [X] T066 [P] [US2] Route tests in `WEB_T/constraint/ConstraintRoutesTest.kt` and `WEB_T/constraint/TeacherFixationRoutesTest.kt`: list with readable descriptions ("Klasse 6b: höchstens 6 Stunden pro Tag – Muss"), invalid fixation mark, catalogue links, create per type incl. `classId=ALL` / `subjectId=ALL`, `422` messages, version conflict `409`, switch on/off, delete, fixation form with qualified teachers and remaining load; generation start form shows up to 3 soft-constraint checkboxes and result columns for them; the generation page lists the constraints and fixations of its snapshot, unchanged after the constraint was edited and showing a deleted class as deleted entry; teacher → `403`

### Implementation for User Story 2

- [X] T067 [P] [US2] Implement `ConstraintKind`, `SoftWeight` (1/3/9), `ClassScope`, and one file per `ConstraintRule` subtype with validation and violation counting in `DOM/scheduling/constraint/` to pass T052–T055
- [X] T068 [US2] Implement `EffectiveConstraints` and `DefaultConstraints` in `DOM/scheduling/constraint/` to pass T056
- [X] T069 [P] [US2] Implement `TeacherFixation` with validity check in `DOM/scheduling/constraint/TeacherFixation.kt` to pass T057; add `InvalidTeacherFixation` to `DOM/readiness/ReadinessIssue.kt`, `DOM/readiness/ReadinessCheck.kt` and `GenerationGate` to pass T060
- [X] T070 [US2] Implement `InfeasibilityPreCheck` in `DOM/scheduling/generation/InfeasibilityPreCheck.kt` to pass T058
- [X] T071 [US2] Extend `HardConstraintChecker` and `ObjectiveEvaluator` (separate objectives, other soft, violation counts) to pass T059; extend `SchedulingProblem`/`SchedulingProblemBuilder` with effective constraints, fixations and the separate-objective selection, and the fingerprint with constraints
- [X] T072 [US2] Add constraint streams per catalogue type, soft weighting per pass and fixation pinning in `SOL/constraints/TimetableConstraintProvider.kt`, `SOL/model/` and `SOL/ProblemMapping.kt`; report a pass ending with a violated hard constraint to the runner as an infeasibility signal (not as failure); passes T061
- [X] T073 [US2] Create `PER_R/V7__scheduling_constraints.sql`: `scheduling_constraint` (`kind VARCHAR(4) CHECK IN ('HARD','SOFT')`, `weight VARCHAR(6) NULL CHECK IN ('LOW','MEDIUM','HIGH')`, `CHECK ((kind = 'SOFT') = (weight IS NOT NULL))`, `all_classes`, restricting class/teacher/subject references, `limit_value`, `across_breaks`, per-type `CHECK`s, `version`), `scheduling_constraint_slot`, `teacher_fixation` (`UNIQUE (class_id, subject_id)`), `generation_separate_objective` (`position SMALLINT 1..3`, `constraint_id … ON DELETE SET NULL`, `constraint_type`), `generation_infeasible_constraint`, snapshot tables `generation_constraint` (no foreign keys to master data or `scheduling_constraint`), `generation_constraint_slot`, `generation_fixation`; insert the two default constraints for the existing school
- [X] T074 [US2] Implement `PER/scheduling/SchedulingConstraintTables.kt`, `ExposedSchedulingConstraintRepository.kt`, `ExposedTeacherFixationRepository.kt`; store separate objectives, infeasible constraint ids and the constraint/fixation snapshot in `ExposedGenerationRepository.kt` to pass T065
- [X] T075 [US2] Implement `SchedulingConstraintUseCases` and `TeacherFixationUseCases` in `APP/scheduling/`, dependant sources for class/teacher/subject/time slot deletion, separate-objective validation, pre-check and the constraint/fixation snapshot (`ConstraintSnapshot`, `FixationSnapshot` in `DOM/scheduling/generation/`) in `StartGeneration`, `FAILED_INFEASIBLE` in `GenerationRunner` only when the generation ends without any admitted valid alternative (G4), default seeding in `APP/account/InstallationUseCase.kt`; wire in `APP/MasterDataUseCases.kt` to pass T062–T064
- [X] T076 [P] [US2] Add `ConstraintTexts` (catalogue names, descriptions rendered from typed rules with entered names, kinds, weights, scope "alle Klassen"/"all classes", messages) and new `DependantType` names (`SCHEDULING_CONSTRAINT`, `TEACHER_FIXATION`) to `WEB/i18n/UiTexts.kt`, `GermanTexts.kt`, `EnglishTexts.kt`
- [X] T077 [US2] Implement `WEB/constraint/ConstraintRoutes.kt`, `ConstraintViews.kt` (per-type form, slot grid with whole-day checkboxes) and `TeacherFixationRoutes.kt`/`TeacherFixationViews.kt`; extend the start form, the result table and the list of constraints in effect (rendered from the snapshot) in `WEB/generation/GenerationViews.kt`; navigation entry *Bedingungen* / *Constraints* to pass T066
- [X] T078 [US2] Run quickstart scenarios 0 (defaults part), 7–17 and 16a against `:bootstrap:run` — 2026-10-09 on `timetable_002` (V7 seeded the two defaults for the existing school): 18/18 checks passed; generations with constraints still place 100 % of the lessons, one run produced 2 equally optimal alternatives (class fill 100 % vs. soft constraints 98 %)

**Checkpoint**: US1 + US2 work independently; generated timetables respect planner constraints

---

## Phase 5: User Story 3 - Compare alternatives and choose the timetable (Priority: P3)

**Goal**: Planners compare alternatives by objective values (table + SVG chart) and timetable by timetable, choose one (with confirmation of unplaced lessons) and can start later generations from the chosen timetable.

**Independent Test**: Generate ≥ 2 alternatives, compare, choose one, verify it stays chosen after a new generation (quickstart 18–21, 20a–20e).

### Tests for User Story 3 (write first, must fail)

- [X] T079 [P] [US3] Diff tests in `DOM_T/scheduling/solution/TimetableDiffTest.kt`: per class, teacher or room the differing day-slots (subject, teacher or room differs, lesson only in one) (FR-020)
- [X] T080 [P] [US3] Stability tests in `DOM_T/scheduling/evaluation/StabilityTest.kt`: changed lessons via multiset matching per class and subject on (day-slot, teacher, room); stability = 1 − changed / reference lessons; reference lessons that violate a hard rule under current data must count as changed (FR-006b)
- [X] T081 [P] [US3] Choose tests in `APP_T/scheduling/ChooseAlternativeTest.kt`: choosing marks it and replaces a previous choice; with unplaced lessons and without confirmation → result "confirmation required" listing unplaced lessons per class, nothing changed; with confirmation → chosen incl. unplaced lessons (FR-021); alternative violating a hard rule under current data → refused naming the conflicting lessons and rules, previous choice unchanged; outdated alternative without conflicts → can be chosen (FR-021, US3 scenario 7); stale `chosenVersion` → `VersionConflict` (FR-028)
- [X] T082 [P] [US3] Tests in `DOM_T/scheduling/solution/ChosenTimetableDriftTest.kt` (missing lessons per class incl. a whole new class → "not placed"; fewer periods → the last lessons of the subject in week order marked "no longer required"; subject removed from the curriculum → all its lessons "no longer required"; unchanged curricula → no drift), `APP_T/scheduling/GetChosenTimetableTest.kt` (after a teacher became unavailable in a chosen lesson's slot, the lesson is returned as conflicting with the violated rule and the result is flagged "no longer fits"; a new class or more weekly periods yield "not placed" lessons per class and fewer periods or a removed subject yield "no longer required" lessons (multiset comparison per class and subject), both also setting the flag; the stored chosen timetable is unchanged; without changes no conflicts and no flag, FR-023), `APP_T/scheduling/CompareAlternativesTest.kt` (objective table sorted by any objective, axis selection) and `APP_T/scheduling/StartGenerationTest.kt` (`startFromChosen` only with a chosen timetable; reference stored and kept when the choice changes later; stability objective added, not counted against the 3 separate objectives)
- [X] T083 [P] [US3] Solver test in `SOL_T/TimefoldTimetableSolverTest.kt`: starting from a reference with one now-unavailable teacher slot, at least one candidate changes few lessons (≤ 3 for the seeded school) besides the conflicting one
- [X] T084 [P] [US3] Repository tests in `PER_T/scheduling/ExposedChosenTimetableRepositoryTest.kt`: choose copies lessons and unplaced lessons; restricting references block deleting a teacher/room/class/subject/slot in use; deleting the alternative's generation is refused; `version` increments
- [X] T085 [P] [US3] Route tests in `WEB_T/generation/CompareRoutesTest.kt` (table, `?x=&y=` axes, inline `<svg>` with one linked point per alternative), `WEB_T/generation/DiffRoutesTest.kt` (highlighted cells), `WEB_T/generation/ChooseRoutesTest.kt` (confirmation page with unplaced lessons, cancel, confirm → `303`, `409` on conflict) and `WEB_T/timetable/ChosenTimetableRoutesTest.kt` (grid, "nicht verplant" list, hint when none chosen, conflicting lessons highlighted with the rule, "nicht verplant" lessons of a class created after choosing, "entfällt" marks for lessons no longer required, and a hint offering a new generation from the chosen timetable, FR-023); deleting a teacher in the chosen timetable → `409` listing *Gewählter Stundenplan*; a teacher account gets `403` on the comparison, diff, choose and chosen-timetable routes without any timetable data (SC-008)

### Implementation for User Story 3

- [X] T086 [P] [US3] Implement `TimetableDiff` in `DOM/scheduling/solution/TimetableDiff.kt` and the stability objective in `DOM/scheduling/evaluation/` to pass T079, T080
- [X] T087 [US3] Create `PER_R/V8__chosen_timetable.sql`: `chosen_timetable` (`school_id` PK, `alternative_id … ON DELETE RESTRICT`, `chosen_by`, `chosen_at`, `version`), `chosen_lesson` and `chosen_unplaced` with `ON DELETE RESTRICT` references to master data, `generation.reference_alternative_id … ON DELETE SET NULL`, `alternative.stability NUMERIC(5,4) NULL`, `alternative.changed_lessons INT NULL`
- [X] T088 [US3] Implement `PER/scheduling/ExposedChosenTimetableRepository.kt` and the reference/stability columns in `ExposedGenerationRepository.kt` to pass T084
- [X] T089 [US3] Implement `ChooseAlternative`, `CompareAlternatives`, `GetChosenTimetable` (conflict check of the chosen lessons against the current problem with `HardConstraintChecker` and comparison with the current curricula via `ClassCurriculumResolver` and the domain function `ChosenTimetableDrift` in `DOM/scheduling/solution/ChosenTimetableDrift.kt` for "not placed" / "no longer required" lessons, FR-023), `DiffAlternatives`, `startFromChosen` in `StartGeneration`, and the `CHOSEN_TIMETABLE` dependant source in `APP/scheduling/`; wire in `APP/MasterDataUseCases.kt` to pass T081, T082
- [X] T090 [US3] Warm start from the reference (still-valid lessons) and stability objective in the weight vectors in `SOL/TimefoldTimetableSolver.kt` and `SOL/ProblemMapping.kt` to pass T083
- [X] T091 [P] [US3] Add comparison, diff, choose/confirm and chosen-timetable texts and the `CHOSEN_TIMETABLE` dependant name to `WEB/i18n/UiTexts.kt`, `GermanTexts.kt`, `EnglishTexts.kt`
- [X] T092 [US3] Implement the SVG trade-off chart (scatter of two selected objectives, Pareto points connected, points linking to alternatives; axis scaling commented) in `WEB/generation/TradeOffChart.kt` and the comparison page in `WEB/generation/CompareViews.kt`
- [X] T093 [US3] Implement diff and choose routes/views in `WEB/generation/AlternativeRoutes.kt`/`AlternativeViews.kt`, `WEB/timetable/ChosenTimetableRoutes.kt`/`ChosenTimetableViews.kt` (conflict and "entfällt" highlighting via `TimetableGrid`, current "nicht verplant" list, "no longer fits" hint with a link to the start form preselecting `startFromChosen`), the `startFromChosen` option in the start form, navigation entry *Gewählter Stundenplan* / *Chosen timetable* to pass T085
- [X] T094 [US3] Run quickstart scenarios 18–21, 20a–20e against `:bootstrap:run` — 2026-10-09 on `timetable_002` (V8 applied to the existing data): 28/28 checks passed; starting from the chosen timetable with one newly unavailable teacher slot changed 1 of 52 lessons; the log contained no names

**Checkpoint**: Planners can choose the school's timetable; later generations can minimise changes

---

## Phase 6: User Story 4 - Keep track of generations when master data changes (Priority: P4)

**Goal**: Generations show status and outdated marks, outdated alternatives highlight lessons that now violate hard rules, and planners delete generations they no longer need.

**Independent Test**: Generate, change a teacher's unavailability, verify the generation is marked outdated and the conflicting lesson highlighted (quickstart 22–24).

### Tests for User Story 4 (write first, must fail)

- [X] T095 [P] [US4] Tests in `APP_T/scheduling/ListGenerationsTest.kt` and `APP_T/scheduling/GetAlternativeTest.kt`: outdated ⇔ stored fingerprint ≠ current fingerprint (master data or constraints changed, also while running); outdated alternative returns the lessons that now violate a hard rule with the rule; deleted master data entries shown as "deleted entry"
- [X] T096 [P] [US4] Delete tests in `APP_T/scheduling/DeleteGenerationTest.kt`: deleting removes alternatives; the generation containing the chosen timetable → `DeletionBlocked` (FR-024); a running generation cannot be deleted
- [X] T097 [P] [US4] Route tests in `WEB_T/generation/GenerationRoutesTest.kt`: list shows start time, planner, status, number of alternatives and outdated mark; outdated alternative page highlights conflicting cells with the reason; delete with confirmation → `303`, chosen one → `409`

### Implementation for User Story 4

- [X] T098 [US4] Compute outdated marks in `ListGenerations`/`GetGeneration` and conflict highlighting in `GetAlternative` (current problem + `HardConstraintChecker`) in `APP/scheduling/` to pass T095
- [X] T099 [US4] Implement `DeleteGeneration` in `APP/scheduling/DeleteGeneration.kt` and the repository delete in `PER/scheduling/ExposedGenerationRepository.kt` to pass T096
- [X] T100 [US4] Show outdated marks, highlighted conflicts (via `TimetableGrid` highlight) and the delete action with confirmation in `WEB/generation/GenerationViews.kt` and `AlternativeViews.kt`, texts in both languages, to pass T097
- [X] T101 [US4] Run quickstart scenarios 22–24 against `:bootstrap:run` (incl. stopping the server during a running generation) — 2026-10-09 on `timetable_002`: 9/9 checks passed (one script check was too broad — older generations are rightly outdated — and was re-checked on a fresh generation); the restart marked the interrupted generation failed and kept its 5 alternatives; the log contained no names

**Checkpoint**: All four user stories work independently

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T102 [P] Extend `WEB_T/LogConfidentialityTest.kt` (both languages) with a full generation run, constraint creation, fixation and choosing, using recognizable names; assert the log contains none of them and no lesson data (FR-026, G13)
- [X] T103 [P] Extend `WEB_T/ConcurrentEditTest.kt` with concurrent constraint edits and concurrent choices of the chosen timetable (FR-028)
- [X] T104 [P] Create `SOL_T/GenerationPerformanceTest.kt` tagged `performance` with a generated SC-001-sized school (40 rooms, 15 subjects, 6 levels, 25 classes, 60 teachers, 5 × 10 grid, ~20 constraints): first candidate < 2 min, ≥ 3 non-dominated alternatives within 10 min (SC-001); run it locally with `./gradlew :adapters:solver:performanceTest` and record the timings in the task — 2026-10-09 locally (`performanceTest`, 10-minute limit): first timetable placing ≥ 90 % of the lessons after 6 s, 3 non-dominated alternatives, 0 invalid candidates. Note: without a soft constraint competing with a full timetable the generated school has a single optimum (1 alternative, correct Pareto behaviour); the test school therefore has the soft rule "at most 5 periods per day" against 30 weekly periods. Two of the three alternatives place only 2 % and 13 % of the lessons — Pareto-optimal (they violate no soft constraint) but of little use; see follow-up in the completion report
- [X] T105 [P] Extend `WEB_T/i18n/EnglishPagesTest.kt` with all new pages (constraints, fixations, generations, alternative, comparison, diff, confirmation, chosen timetable): `lang="en"`, no German interface words, names unchanged (FR-027)
- [X] T106 [P] Update `README.md`: scheduling section (constraints, generations, comparison, choosing, home room), the `performanceTest` task and the solver module in the project structure table
- [X] T107 Review the whole change against the constitution gates (naming, function size, comments on complex-but-compact functions, no confidential data in logs and technical errors, hard-constraint and Pareto tests present) and fix findings — findings fixed: shared `conflictCell` instead of duplicated conflict-cell code, chosen-timetable page split into `chosenWeek`/`droppedLessons`, immutable `ObjectiveColumn`, speaking names in `TradeOffChart`
- [X] T108 Run the full quickstart 0–27 against `:bootstrap:run` with a fresh database and record the result — 2026-10-09 on a freshly created `timetable_002`: 86/88 checks passed; the 2 failing checks were script defects (an over-broad "outdated" text match across older generations and a "5c" match inside a UUID), the behaviour itself was verified via the database and a fresh generation; log free of names, codes, rooms and passwords
- [ ] T109 Push the branch, open a pull request `002-timetable-generation` → `main` (after `one-school` is merged, or based on `one-school`) and confirm that the workflows Build and CodeQL succeed without new security alerts (incl. the Timefold dependency in GitHub Advanced Security); fix findings

---

## Phase 8: Extended Validation with a Test School of Realistic Size

**Goal**: Check on a school of realistic size — a fictitious G9 Gymnasium in the style of Baden-Württemberg, grades
5–10, 3 classes per level — that timetable generation works in principle, in a way that can be repeated for later
features.

**Test set**: [test-set-bw-gymnasium.md](test-set-bw-gymnasium.md). **All data is marked as test data** (school
"TESTDATEN – Testgymnasium Musterstadt", teachers "Testlehrkraft NN", rooms "TEST-…"); it is not real school data.

**Independent Test**: `./gradlew :bootstrap:testSchoolTest` seeds the test school into a fresh database, runs one
generation with the default time limit and checks the expected results of the test set.

`BOOT_T` = `bootstrap/src/test/kotlin/timetablescheduler/bootstrap`. The test set is test code only and never part of
the production artifact.

- [X] T110 [P] Describe the test set as typed Kotlin data in `BOOT_T/testschool/BwGymnasiumTestSet.kt` exactly as in `test-set-bw-gymnasium.md` (grid, features, rooms, subjects, weekly periods per level, 18 classes with home rooms, 32 teachers with qualifications, loads and unavailability, 20 constraints, 3 fixations), with a file header stating that it is fictitious test data; test it first in `BOOT_T/testschool/BwGymnasiumTestSetTest.kt`: 31 rooms, 18 subjects, 18 classes, 32 teachers, 588 weekly lessons, 705 periods of teacher capacity, 20 constraints, every subject of every curriculum taught by at least one qualified teacher, every school, teacher and room name carrying its test marker
- [X] T111 Implement `BOOT_T/testschool/TestSchoolSeeder.kt`: enters the test set through the application use cases as the initial planner (so every validation of 001 and 002 applies; no direct SQL), renames the installed school to the test name and returns the created ids; refuses to run when the school already has rooms, subjects, classes or teachers, so it can never mix test data into existing data; logs only counts (constitution III); test the refusal and a complete seeding with the in-memory fakes in `BOOT_T/testschool/TestSchoolSeederTest.kt`
- [X] T112 Add Testcontainers PostgreSQL to the test dependencies of `bootstrap/build.gradle.kts` and register two tasks: `testSchoolTest` (JUnit tag `testschool`, excluded from `test` and `build` like `performanceTest`, never up to date) and `seedTestSchool` (runs `BOOT_T/testschool/SeedTestSchool.kt` on the test runtime classpath)
- [X] T113 Implement `BOOT_T/testschool/SeedTestSchool.kt`: reads the `TTS_*` configuration, applies the migrations, runs the installation and the seeder against that database, so the test school can be inspected in the running application; prints the school id and counts only
- [X] T114 Implement the scenario test `BOOT_T/testschool/TestSchoolScenarioTest.kt` (tag `testschool`): PostgreSQL via Testcontainers, the real wiring of `Main.kt` (Exposed repositories, `TimefoldTimetableSolver`, background executor); seed, assert the readiness check has no blocking issue, start a generation with the default time limit, wait for its end and assert the expected results of the test set (first alternative < 2 min, ≥ 3 alternatives, independent re-check of all hard constraints, fixations and unavailability on the stored alternatives, no lessons of grades 5–6 in the 9./10. Stunde, room features and home rooms respected, no dominated alternative); print the recorded metrics of the test set (times, counts, objective values — no names) — class names (e.g. "5a") of the fictitious test set are printed with the unplaced lessons, nothing else
- [X] T115 Run `./gradlew :bootstrap:testSchoolTest` and record the metrics in this task; if fewer than 3 alternatives place at least 90 % of the lessons (see the near-empty alternatives found in T104), raise it for `/speckit-clarify` on this feature before the pull request (T109) — 2026-10-22, default time limit 10 min: the run ended FINISHED with **1 alternative** that places all 588 lessons with 0 soft violations (objectives class fill 1.0, teacher load usage 0.834 = 588/705, other soft 1.0); first alternative after 4 s, first useful one after 83 s; every hard constraint, fixation, unavailability and room rule held. **The assertion "≥ 3 alternatives" (SC-001) fails**: this alternative is optimal in every objective, so Pareto-correctly no other one is kept, and timetables with equal values are not reported because the solver only reports improvements. Raised for `/speckit-clarify` (see T116 for the 3-minute run with 10 alternatives); the scenario test keeps the SC-001 assertion until the specification is refined
- [X] T116 Seed a fresh database `timetable_testschool` with `./gradlew :bootstrap:seedTestSchool`, start `:bootstrap:run` against it and review in the user interface: generation list and result table, comparison chart, class 5a, teacher T07, room TEST-NW1, choosing an alternative; record the findings in this task — 2026-10-22 on `timetable_testschool`: seeding via `seedTestSchool` (31 rooms, 18 subjects, 18 classes, 32 teachers), app pages checked over HTTP: school with test marker, readiness without blocking issues, constraints, generation progress, result table, class 5a (home room TEST-R101, nothing in the 9./10. Stunde), teacher T07, comparison chart, choosing; room TEST-NW1 holds only Biologie/BNT/Chemie/Physik/Profilfach (verified in the database; the scripted page check matched the language switch "Deutsch" and was a script defect). A generation with a 3-minute limit ended with 10 alternatives — trade-offs exist while the solver has not yet reached the optimum
- [X] T117 [P] Document the test school in `README.md` and `quickstart.md` (commands, separate database, the "test data — not a real school" note, how to extend the test set in later features)

**Checkpoint**: Generation is validated on a school of realistic size; the scenario can be rerun for later features

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → user stories → **Polish (Phase 7)** → **Extended validation (Phase 8)**
- T109 (pull request) waits for Phase 8.
- Feature 001 (branch `one-school`) must be in place; this branch is based on it.

### User Story Dependencies

- **US1 (P1)**: after Foundational. MVP.
- **US2 (P2)**: after US1 (extends the start form, the runner and the solver model).
- **US3 (P3)**: after US1; independent of US2 except for shared files (`StartGeneration`, start form) — run after US2 to avoid conflicts.
- **US4 (P4)**: after US1; deletion protection of the chosen timetable needs US3.

```text
Setup → Foundational → US1 → US2 → US3 → US4 → Polish → Extended validation → T109 (pull request)
```

### Within Each Phase

- Tests first and failing (Principle V) → domain → ports/use cases → solver adapter → migration → repositories → texts → routes/views → wiring → quickstart
- Shared files run sequentially: `APP/MasterDataUseCases.kt`, `BOOT/Main.kt`, `WEB/WebModule.kt`, `WEB/layout/PageLayout.kt`, `WEB/i18n/*Texts.kt`, `SOL/constraints/TimetableConstraintProvider.kt`, `APP/scheduling/StartGeneration.kt`, `WEB/generation/GenerationViews.kt`

### Parallel Opportunities

- Foundational: T005–T011 together; T012, T013, T015, T018, T019 together
- US1: T023–T033 together; T034, T035, T045 together
- US2: T052–T066 together; T067, T069, T076 together
- US3: T079–T085 together; T086, T091 together
- US4: T095–T097 together
- Polish: T102–T106 together

---

## Parallel Example: User Story 1

```text
# All US1 tests at once (different files):
T023 GenerationGateTest, T024 GenerationTest, T025 TimefoldTimetableSolverTest, T026 StartGenerationTest,
T027 GenerationRunnerTest, T028 RecoverInterruptedGenerationsTest, T029 SchoolClassUseCasesTest,
T030 ExposedGenerationRepositoryTest, T031 GenerationRoutesTest, T032 AlternativeRoutesTest, T033 CurriculumRoutesTest

# Then in parallel: T034 domain gate + aggregate, T035 Timefold model, T045 texts
```

## Parallel Example: User Story 2

```text
# Domain rule tests per catalogue group:
T052 DayLimitRulesTest, T053 GapRulesTest, T054 SubjectRulesTest, T055 ExcludedSlotsRulesTest,
T056 EffectiveConstraintsTest, T057 TeacherFixationTest, T058 InfeasibilityPreCheckTest
```

---

## Implementation Strategy

### MVP First

1. Setup + Foundational → scheduling core tested without Timefold
2. US1 → valid, equally optimal timetables with home rooms → validate with quickstart 0–6 → demo

### Incremental Delivery

1. + US2 → planner constraints, defaults, fixations, separate objectives
2. + US3 → comparison, choosing, start from the chosen timetable
3. + US4 → outdated marks and cleanup
4. Polish → log leak, concurrency, performance (SC-001), English pages, full quickstart, PR with CI security results

---

## Notes

- Commit after each task or logical group on branch `002-timetable-generation`.
- If Timefold 2.7.1 is unavailable or its API differs from the plan (score types, `terminateEarly`, `ConstraintVerifier`), record the deviation in research.md G1/G5 before implementing.
- Stop at any checkpoint to validate the story independently.
