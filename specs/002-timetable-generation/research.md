# Research: Timetable Generation with Planner-Defined Constraints

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-10-09

Decisions R1–R19 of feature 001 ([research](../001-scheduling-master-data/research.md)) remain in force (Ktor, server-rendered HTML, PostgreSQL + Exposed + Flyway, login and access policy, optimistic locking, confidentiality, TDD, module structure, interface languages). This document adds the decisions for scheduling. Numbering continues with G1 to avoid confusion with 001's R-numbers.

## G1 — Solver engine

- **Decision**: Timefold Solver Community Edition (`ai.timefold.solver:timefold-solver-core`, Apache-2.0), version 2.7.1, used only inside a new adapter module `adapters:solver`.
- **Rationale**: School timetabling is a classic constraint-satisfaction/optimization problem that Timefold (successor of OptaPlanner; school timetabling is its reference example) solves with construction heuristics and local search. Writing a competitive metaheuristic by hand would contradict Principle II (established library for non-trivial functionality). Version 2.x requires Java 21, which matches the project's toolchain. The Community Edition is open source; the Enterprise-only features (multithreaded solving, nearby selection) are not needed at the scale of SC-001.
- **Verified during implementation (2026-10-09)**: In 2.7.1 the long-based score types are the default and named without `Long` (`ai.timefold.solver.core.api.score.HardSoftScore`, `of(long, long)`), and `ConstraintVerifier` is part of `timefold-solver-core` (`ai.timefold.solver.core.api.score.stream.test`); there is no separate `timefold-solver-test` 2.x artifact. Only `timefold-solver-core` is used.
- **Alternatives considered**:
  - *Jenetics (NSGA-II in `jenetics.ext`)*: real multi-objective evolutionary algorithm, but genetic operators handle the many hard timetabling constraints poorly (most offspring are infeasible, need hand-written repair); quality for 25 classes in minutes is doubtful.
  - *MOEA Framework*: broad multi-objective algorithms, LGPL, same repair problem as Jenetics.
  - *Choco Solver / OR-Tools CP-SAT*: exact constraint solvers; CP-SAT is a native library (JNI, platform binaries), Choco needs the whole model expressed as CP constraints — both need the same scalarization for several objectives (G2) and offer no advantage for local search at this size.
  - *Hand-written local search*: smallest dependency footprint but a large, error-prone amount of non-trivial code.

## G2 — Producing several equally optimal (Pareto-optimal) alternatives

- **Decision**: Weighted-sum scalarization with a Pareto archive.
  1. The objectives (G3) are normalized to 0…1 so that weights are comparable.
  2. A generation runs a sequence of solver *passes*. Each pass uses a different weight vector over the objectives (first pass: equal weights; then each objective emphasized in turn; then evenly spread vectors of the weight simplex), and starts from the best solution of the previous pass (warm start) so that later passes are cheap.
  3. Every new best solution reported by the solver during a pass (not only the final one) is evaluated by the **domain** objective evaluator and offered to a `ParetoArchive` (domain). The archive keeps only non-dominated alternatives, keeps alternatives with identical objective values but different timetables (FR-007), and when it exceeds the maximum (default 10, FR-008) drops the alternative with the smallest crowding distance (the one whose objective values are closest to its neighbours), so the trade-off range stays covered.
  4. The time limit (FR-009) is divided evenly between the passes; the planner can stop at any time.
- **Rationale**: Timefold optimizes one (lexicographic) score; weighted-sum passes are the standard way to approximate a Pareto front with a single-objective solver. Collecting intermediate best solutions makes the archive richer at no extra cost. Keeping dominance and crowding in the domain makes Constitution IV testable without the solver.
- **Known limitation**: weighted sums cannot reach non-convex parts of the front. Accepted, because the spec only requires the non-dominated set of what was found (spec assumptions); epsilon-constraint passes can be added later behind the same port.
- **Alternatives considered**: Timefold bendable score with one level per objective (lexicographic — always favours the first objective, does not produce trade-offs); true multi-objective EA (see G1).

## G3 — Objectives and their normalization (FR-006, FR-006a)

- **Decision**: Every objective is expressed as "higher is better", 0…1, rounded to 4 decimals for comparison and display:
  - **Class fill** = Σ_c weight_c · placed_c / Σ_c weight_c · required_c (priority weight from feature 001).
  - **Teacher load usage** = Σ_t min(assigned_t, load_t) / Σ_t load_t (teachers with load 0 ignored; 1 if no teacher has a load).
  - **Separate soft constraint *k*** (up to 3, chosen at start) = 1 − violations_k / maxViolations_k, where maxViolations_k is the number of violation opportunities of that constraint (e.g., teaching days × 1 for "no gaps", lessons of the subject for "double periods").
  - **Other soft constraints** = 1 − Σ_k w_k · violations_k / Σ_k w_k · maxViolations_k, weights low = 1, medium = 3, high = 9; 1 if no other soft constraint is active.
  - **Stability** (only when starting from the chosen timetable, FR-006b) = 1 − changedLessons / lessons of the reference; a lesson counts as changed if its (class, subject) occurrence is not found with the same day-slot, teacher and room (multiset matching per class and subject). Shown as "changed lessons" (count) in the pages.
- The pages show objectives as percentages; the evaluator also returns the raw violation counts per soft constraint (FR-018).
- **Rationale**: Normalized, same-direction objectives are needed for weighted sums (G2) and are easy to read for planners. The 1/3/9 weights make "high" clearly dominant without making "low" irrelevant.
- **Alternatives considered**: raw counts (not comparable across objectives); free numeric weights (harder to use, spec asks for low/medium/high).

## G4 — Hard constraints and their independent check

- **Decision**: Hard constraints are enforced twice:
  1. **In the solver model** (adapter): as Timefold hard constraints; lessons may stay unassigned (`allowsUnassigned`), so a timetable that respects all hard constraints always exists (an empty one) — unplaced lessons only lower class fill (FR-005).
  2. **In the domain** by a `HardConstraintChecker` that validates every alternative before it enters the archive. An alternative with a violation is discarded and logged as a technical event without data (count only). The same checker highlights violating lessons of outdated alternatives (FR-023).
- The structural rules "all periods of one subject in one class have the same teacher" (FR-004) and "teacher fixation" (FR-013a) are modelled structurally: one planning entity per (class, subject) holds the teacher; fixed ones are pinned.
- The planner hard constraint "minimum periods per day" can make the problem infeasible. A domain `InfeasibilityPreCheck` detects contradictions before the start where they can be determined (FR-017): minimum per day above teaching slots of a day; minimum × days above curriculum periods; excluded hard slots leaving fewer slots than the class's / teacher's required periods; min > max for the same class. A pass ending with a violated hard constraint is not a failure: its candidates are discarded by the checker and the generation continues with the next pass. The generation ends with status `FAILED_INFEASIBLE` **only if it ends (time limit, stop or last pass) without a single admitted valid alternative** and at least one pass ended with a violated hard constraint; the message then names the active hard planner constraints that can cause infeasibility (minimum periods per day, excluded slots). As soon as one valid alternative exists, the generation ends as `FINISHED` or `STOPPED` as usual.
- **Rationale**: SC-002 (0 violations) must not depend on the correctness of the solver configuration alone; the domain checker is the reference and is fully unit-testable (Constitution gate "tests demonstrating hard constraints are never violated").

## G5 — Solver model (adapter `adapters:solver`)

- **Decision**:
  - Problem facts: teaching slots (day × slot), rooms with features, teachers with qualifications, load and unavailable slots, classes, planner constraints.
  - Planning entities: `CourseAssignment` (class, subject; variable `teacher`, pinned if fixed) and `LessonPlacement` (one per required period; variables `timeslot` and `room`, both allowing unassigned). For a class with a home room, placements of subjects without required features get the home room as their only room value, which keeps the home-room rule structural (FR-002a).
  - Starting from the chosen timetable (FR-006b): the first pass is initialized with the reference's lessons (those still valid under hard constraints), and the stability objective enters the weight vectors like the others.
  - Constraints written with Constraint Streams; each planner constraint type maps to one stream; soft streams are multiplied by the pass's weight vector into one soft score level. Score type: `HardSoftScore` (long-based in 2.x) (one hard level for all hard constraints, one soft level for the weighted sum).
  - The adapter translates between the application's `SchedulingProblem`/`TimetableCandidate` (plain Kotlin types from the domain) and the Timefold model; Timefold annotations and types never leave the module.
- **Rationale**: The two-entity model keeps "same teacher per class and subject" structural and lets construction heuristics place lessons quickly; the adapter boundary keeps the domain framework-free (Constitution clean architecture).

## G6 — Running generations in the background (FR-009, FR-011, edge cases)

- **Decision**: An application service `GenerationRunner` (port `TimetableSolver` implemented by the solver adapter) runs generations on a dedicated single-thread executor per installation, created in `bootstrap`. Progress (elapsed time, alternatives so far) is written to the `generation` row after every archive change; alternatives are persisted as they enter the archive and removed when they are displaced, so a stop or crash keeps what was found. Stop sets a flag that the adapter passes to Timefold's early termination. At start-up, `bootstrap` marks generations still `RUNNING` as `FAILED` (server restart, edge case). One running generation per school is enforced by a partial unique index (`status = 'RUNNING'`).
- Pages showing a running generation refresh themselves every 3 s with `<meta http-equiv="refresh">` — no script needed (Constitution: scripts only where HTML cannot do it).
- **Rationale**: Minimal moving parts for one school; no message queue or job framework needed.
- **Alternatives considered**: Timefold `SolverManager` (handles threads itself, but our pass sequence and archive need custom orchestration anyway); Quartz/job library (unneeded dependency).

## G7 — Storing generations and alternatives (FR-022, FR-016)

- **Decision**:
  - Lesson assignments of alternatives are stored by id **without** foreign keys to master data, because results must stay viewable after master data changes (US4); deleted entries are shown as "deleted entry".
  - The **chosen timetable** is copied into its own table `chosen_lesson` **with** `ON DELETE RESTRICT` foreign keys, so deleting a teacher, room, class, subject or slot used in the chosen timetable is blocked and listed as a dependant (FR-016); the existing deletion use cases get one more `DependantSource`.
  - Constraints and fixations reference master data with restricting foreign keys and are listed as dependants as well.
  - **Constraint snapshot (FR-022)**: at start, `StartGeneration` copies every active constraint (type, kind, weight, scope, referenced ids, limit, across-breaks flag, slots) and every teacher fixation into `generation_constraint` / `generation_constraint_slot` / `generation_fixation`. Like the lessons, the snapshot stores ids **without** foreign keys to master data or to `scheduling_constraint`, so later changes or deletions neither alter nor block it. The result page renders the snapshot with the current names in the user's language (structured, not as stored text, because texts depend on the language); deleted entries appear as "deleted entry". The fingerprint (G8) stays the means to detect outdated generations; the snapshot is for showing what the generation used.
- **Rationale**: Combines "old results remain viewable" with "the chosen timetable stays consistent".

## G8 — Detecting outdated generations (FR-022, US4)

- **Decision**: A domain function `SchedulingInputFingerprint` computes a SHA-256 hash over a canonical, sorted representation of all master data and active constraints that the scheduler uses. It is stored with the generation at its start; a generation is outdated when the current fingerprint differs. The fingerprint is computed once per page request that lists or shows generations.
- **Rationale**: No coupling of every master-data use case to generations (no "mark outdated" calls to forget); correct by construction. Cost: one read of the school's master data (< 50 ms for SC-001 size).
- **Alternatives considered**: database triggers incrementing a revision counter (logic in SQL, harder to test); explicit "mark outdated" calls in ~30 use cases (easy to miss one).

## G9 — Constraint catalogue and parameters (FR-012 – FR-015)

- **Decision**: A sealed domain type `SchedulingConstraint` with one subtype per catalogue entry; stored in one table with typed nullable reference columns (class, teacher, subject), an integer `limit_value`, a flag `across_breaks` and a child table of slots. A database `CHECK` per type guarantees the required columns. Catalogue (FR-013):

  | Type | Parameters | Violation counted per |
  |------|-----------|------------------------|
  | `CLASS_MAX_PERIODS_PER_DAY` | class, limit 1…slots per day | day above limit (by excess periods) |
  | `CLASS_MIN_PERIODS_PER_DAY` | class, limit 1…slots per day | teaching day below limit (by missing periods) |
  | `CLASS_NO_GAPS` | class | free teaching slot between first and last lesson of a day |
  | `TEACHER_MAX_PERIODS_PER_DAY` | teacher, limit | as class |
  | `TEACHER_MAX_GAPS_PER_DAY` | teacher, limit 0…slots per day | gap above limit |
  | `SUBJECT_MAX_PER_DAY_IN_CLASS` | class, subject, limit | period above limit on a day |
  | `SUBJECT_DOUBLE_PERIODS_IN_CLASS` | class, subject, across breaks yes/no | lesson not part of a pair (one single allowed for an odd number) |
  | `CLASS_EXCLUDED_SLOTS` | class, slots | lesson in an excluded slot |
  | `TEACHER_EXCLUDED_SLOTS` | teacher, slots (whole days selectable) | lesson in an excluded slot — as soft constraint this is the teacher's *preferred free slots or days* |
  | `SUBJECT_EXCLUDED_SLOTS` | subject, optional class, slots | lesson in an excluded slot |

  Class-related types accept the scope *all classes* (FR-013b); a class-specific constraint of the same type replaces the school-wide one for that class (domain `EffectiveConstraints`). The two default constraints (FR-013c) are seeded with the school.

  Teacher fixations (FR-013a) are a separate entity `TeacherFixation` (class, subject, teacher; unique per class and subject), always hard.
- Validation (FR-015) lives in the domain factories (limits within the time grid, slots that are teaching slots, subject in the class curriculum for subject-in-class types, `CLASS_MIN` ≤ `CLASS_MAX` for the same class when both are hard).
- **Rationale**: Typed columns allow restricting foreign keys (dependants, FR-016) and keep queries simple; a JSON parameter column would need a JSON library and lose referential integrity.

## G10 — Comparison chart and timetable diff (FR-020)

- **Decision**: The trade-off chart is a server-rendered inline SVG (scatter plot of two planner-selected objectives, Pareto points connected, each point linking to its alternative) built with `kotlinx.html`'s SVG support; no chart library and no script. The diff marks every (day, slot) cell whose lesson (subject, teacher, room) differs between two alternatives for the selected class, teacher or room.
- **Rationale**: Two numeric axes and ≤ 10 points do not justify a chart library (Principle II: no library for trivial functionality); works without JavaScript.

## G11 — Readiness gate (FR-010)

- **Decision**: A domain function `GenerationGate` classifies the readiness issues of feature 001: `TEACHER_LOAD_EXCEEDS_GRID` and `LEVEL_EXCEEDS_GRID` plus the new `INVALID_TEACHER_FIXATION` are *blocking*; all others are *warnings* that the planner confirms with a checkbox on the start form.
- **Rationale**: Blocking issues make the problem ill-defined; the others only limit how complete the timetable can be.

## G12 — Performance (SC-001)

- **Decision**: Construction heuristic `FIRST_FIT_DECREASING` followed by late acceptance / tabu local search (Timefold defaults); first pass gets max(60 s, limit / passes) so that the first alternative is available within 2 minutes; number of passes = 2 + number of objectives + 2 spread vectors (6–11: 2 to 7 objectives incl. up to 3 separate soft-constraint objectives and the stability objective). A performance smoke test with a generated SC-001-sized school asserts "first alternative meeting the minimum share of placed lessons < 2 min; no alternative below the share or without placed lessons" (refined SC-001: no fixed number of alternatives) (tagged, run in CI with a reduced limit and locally in full).
- **Rationale**: Timefold reaches feasible school timetables of this size within seconds to a minute in its reference benchmarks; the rest of the budget improves trade-offs.

## G13 — Confidentiality (FR-026)

- **Decision**: Solver logging (`ai.timefold`) pinned to `WARN` in `logback.xml` (Timefold logs best scores and, at debug level, entity values); the generation runner logs only generation id, status, pass count, alternative count and duration. Exceptions from the solver are mapped to the generic technical error with correlation id; the generation status becomes `FAILED`. Business messages (contradicting constraints, blocking readiness issues) name entries only on planner pages. A log test like feature 001's `LogConfidentialityTest` runs a full generation with recognizable names and asserts none appears in the log.

## G14 — Interface texts

- **Decision**: New sub-interfaces of `UiTexts` (`constraints`, `generations`, `timetables`) implemented in `GermanTexts` and `EnglishTexts` (feature 001, R18). Constraint descriptions are rendered from the typed constraint plus the entered names (e.g., "Klasse 6b: höchstens 6 Stunden pro Tag – Muss" / "Class 6b: at most 6 periods per day – hard").
