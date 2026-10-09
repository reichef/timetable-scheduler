# Data Model: Timetable Generation with Planner-Defined Constraints

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md) | **Builds on**: [001 data model](../001-scheduling-master-data/data-model.md)

All new data belongs to the school (`school_id`, as required by 001 FR-036). Identifiers are UUIDs; aggregate roots that planners edit carry an optimistic-locking `version` (001 R8).

## Domain types (module `domain`, package `timetablescheduler.domain.scheduling`)

### SchoolClass — extension of feature 001 (FR-002a)

| Field | Type | Rules |
|-------|------|-------|
| `homeRoomId` | `RoomId?` | optional; a room is the home room of at most one class (rejected naming the other class); the room cannot be deleted while it is a home room |

### SchedulingConstraint (aggregate root)

| Field | Type | Rules |
|-------|------|-------|
| `id` | `SchedulingConstraintId` | |
| `schoolId` | `SchoolId` | |
| `kind` | `HARD` \| `SOFT` | |
| `weight` | `LOW` \| `MEDIUM` \| `HIGH` | only for `SOFT`; default `MEDIUM`; numeric 1 / 3 / 9 (G3) |
| `active` | Boolean | default `true`; switched off → ignored by generations (US2 scenario 7) |
| `rule` | sealed `ConstraintRule` | one subtype per catalogue entry (G9) |
| `version` | Int | |

`ConstraintRule` subtypes and validation (FR-015, all rejected with a typed `ValidationError`):

| Subtype | Fields | Validation |
|---------|--------|-----------|
| `ClassMaxPeriodsPerDay` | `classScope`, `limit` | class exists; 1 ≤ limit ≤ teaching slots of the longest teaching day |
| `ClassMinPeriodsPerDay` | `classScope`, `limit` | as above; when hard: not greater than an active hard `ClassMaxPeriodsPerDay` of the same class |
| `ClassNoGaps` | `classScope` | class exists |
| `TeacherMaxPeriodsPerDay` | `teacherId`, `limit` | teacher exists; 1 ≤ limit ≤ teaching slots per day |
| `TeacherMaxGapsPerDay` | `teacherId`, `limit` | 0 ≤ limit ≤ teaching slots per day − 2 |
| `SubjectMaxPerDayInClass` | `classScope`, `subjectId?`, `limit` | `subjectId` null = every subject; for a specific class the subject is in its curriculum (via `ClassCurriculumResolver`); 1 ≤ limit ≤ teaching slots per day |
| `SubjectDoublePeriodsInClass` | `classId`, `subjectId`, `acrossBreaks` | subject in the class's curriculum with ≥ 2 weekly periods |
| `ClassExcludedSlots` | `classId`, `slots: Set<DaySlot>` | ≥ 1 slot; every slot a teaching slot on a teaching day |
| `TeacherExcludedSlots` | `teacherId`, `slots` | as above (soft = preferred free slots/days) |
| `SubjectExcludedSlots` | `subjectId`, `classId?`, `slots` | as above; class (if given) has the subject in its curriculum |

`DaySlot` = (`TeachingDay`, `TimeSlotId`).

`ClassScope` = `AllClasses` | `SpecificClass(classId)` (FR-013b). **Precedence**: for a class, a constraint of the same type with `SpecificClass` (and, for `SubjectMaxPerDayInClass`, the same subject or every subject) replaces the `AllClasses` constraint of that type; resolved by the domain function `EffectiveConstraints` when building the `SchedulingProblem`.

**Default constraints (FR-013c)**: `SubjectMaxPerDayInClass(AllClasses, every subject, 2)` and `ClassNoGaps(AllClasses)`, both `SOFT`/`MEDIUM`/active; created together with the school at installation and by migration V7 for the existing school.

### TeacherFixation (aggregate root, always hard — FR-013a)

| Field | Type | Rules |
|-------|------|-------|
| `id`, `schoolId`, `version` | | |
| `classId` | `SchoolClassId` | |
| `subjectId` | `SubjectId` | in the class's curriculum |
| `teacherId` | `TeacherId` | qualified for the subject; Σ weekly periods of all fixations of the teacher ≤ teacher's weekly load |

Unique per (`classId`, `subjectId`). A fixation that no longer satisfies these rules after master-data changes is reported as readiness issue `INVALID_TEACHER_FIXATION` (blocking, G11) and marked invalid in the list.

### SchedulingProblem (value, input of a generation)

Built by the application from master data and active constraints: teaching day-slots, rooms with features, teachers (qualifications, load, unavailable day-slots), classes with weights and resolved curricula, subjects with required features, active constraints, fixations, separate-objective selection. `SchedulingInputFingerprint` (G8) hashes its canonical form.

### TimetableCandidate / LessonAssignment (value)

`LessonAssignment` = `classId`, `subjectId`, `teacherId`, `roomId`, `daySlot`. A candidate = list of assignments + `unplaced: Map<(classId, subjectId), Int>`.

### Domain services

| Name | Responsibility |
|------|---------------|
| `HardConstraintChecker` | Returns all hard violations of a candidate against a problem (001 FR-022 rules, home room FR-002a, FR-004 same teacher, fixations, active hard planner constraints). Used for archive admission and outdated highlighting (G4, FR-023). |
| `ObjectiveEvaluator` | Computes `ObjectiveValues` (class fill, teacher load usage, up to 3 separate soft constraints, other soft constraints) and violation counts per soft constraint (G3). |
| `ParetoArchive` | Keeps non-dominated candidates, identical-value duplicates with different timetables, capacity limit with crowding-distance pruning (G2, FR-007, FR-008). |
| `InfeasibilityPreCheck` | Detects contradicting hard constraints before start and names them (FR-017). |
| `GenerationGate` | Splits readiness issues into blocking and warnings (FR-010, G11). |
| `TimetableDiff` | Cells (day-slot) that differ between two candidates for a class, teacher or room (FR-020). |
| `SchedulingInputFingerprint` | Hash of the problem's canonical form (G8). |
| `ChosenTimetableDrift` | Compares the chosen lessons with the current curricula per class and subject (multiset): lessons required but missing → "not placed", surplus lessons → "no longer required", choosing the last ones of the subject in week order (latest teaching day, then latest slot) (FR-023). |

### Generation (aggregate root)

| Field | Type | Rules / states |
|-------|------|---------------|
| `id`, `schoolId` | | |
| `startedBy` | `UserAccountId` | |
| `startedAt`, `finishedAt?` | Instant | |
| `timeLimitMinutes` | Int | 1…60, default 10 (FR-009) |
| `maxAlternatives` | Int | 10 (FR-008) |
| `status` | `RUNNING` → `FINISHED` \| `STOPPED` \| `FAILED` \| `FAILED_INFEASIBLE` | `STOPPED` = stopped early by a planner; `FAILED` = technical error or server restart; `FAILED_INFEASIBLE` = ended without a single valid alternative because hard constraints could not be met (G4) — never set when at least one valid alternative exists |
| `inputFingerprint` | String | outdated ⇔ ≠ current fingerprint (G8) |
| `referenceAlternativeId?` | `AlternativeId` | set when started "from the chosen timetable" (FR-006b): the alternative chosen at start; kept even if the choice changes later |
| `constraintSnapshot` | list of `ConstraintSnapshot` (original constraint id, type, kind, weight, scope, class/teacher/subject ids, limit, across breaks, slots) + list of `FixationSnapshot` (class, subject, teacher) | copied from all active constraints and all fixations at start (FR-022); never changed afterwards |
| `separateObjectives` | list of ≤ 3 `SchedulingConstraintId?` with a snapshot of each constraint's type | must be active soft constraints at start (FR-006a); null after the constraint was deleted |
| `infeasibleConstraintIds` | set | filled for `FAILED_INFEASIBLE` |
| `alternativesFound` | Int | progress (FR-011) |

### Alternative

| Field | Type |
|-------|------|
| `id`, `generationId` | |
| `objectiveValues` | class fill, teacher load usage, separate objectives (by position), other soft constraints, stability (only with reference, FR-006b) — 4 decimals |
| `changedLessons` | Int? | lessons changed compared to the reference (only with reference) |
| `lessons` | list of `LessonAssignment` |
| `unplaced` | (class, subject) → count |
| `softViolations` | constraint id → count |

### ChosenTimetable (one per school)

`schoolId`, `alternativeId`, `chosenBy`, `chosenAt`, `version`, plus copied lessons with restricting references (G7) and copied unplaced lessons per class (shown as "not placed", FR-021). Choosing an alternative with unplaced lessons requires `unplacedConfirmed = true`.

## Relational schema (migrations V6–V8)

Split per story: `V6__home_room_and_generations.sql` (home room, generation, alternative tables), `V7__scheduling_constraints.sql` (constraints, slots, fixations, separate objectives, infeasible constraints, default seed), `V8__chosen_timetable.sql` (chosen timetable tables, reference and stability columns).

```text
ALTER TABLE school_class ADD home_room_id UUID NULL REFERENCES room ON DELETE RESTRICT, UNIQUE (home_room_id)
scheduling_constraint (id PK, school_id FK school, type VARCHAR(40), kind VARCHAR(4) CHECK IN ('HARD','SOFT'),
    weight VARCHAR(6) NULL CHECK (weight IN ('LOW','MEDIUM','HIGH')), active BOOLEAN,
    all_classes BOOLEAN, class_id FK school_class RESTRICT NULL, teacher_id FK teacher RESTRICT NULL, subject_id FK subject RESTRICT NULL,
    limit_value INT NULL, across_breaks BOOLEAN NULL, version INT,
    CHECK ((kind = 'SOFT') = (weight IS NOT NULL)), CHECK per type for required columns)
scheduling_constraint_slot (constraint_id FK CASCADE, day VARCHAR(9), time_slot_id FK time_slot RESTRICT, PK all three)
teacher_fixation (id PK, school_id, class_id FK RESTRICT, subject_id FK RESTRICT, teacher_id FK RESTRICT, version,
    UNIQUE (class_id, subject_id))
generation (id PK, school_id, started_by FK user_account, started_at, finished_at NULL, time_limit_minutes INT CHECK 1..60,
    max_alternatives INT, status VARCHAR(17), input_fingerprint CHAR(64), alternatives_found INT,
    reference_alternative_id FK alternative SET NULL NULL,
    UNIQUE INDEX one_running_per_school ON (school_id) WHERE status = 'RUNNING')
generation_separate_objective (generation_id FK CASCADE, position SMALLINT 1..3, constraint_id FK SET NULL NULL,
    constraint_type VARCHAR(40), PK (generation_id, position))
generation_infeasible_constraint (generation_id FK CASCADE, constraint_id FK CASCADE)
generation_constraint (generation_id FK CASCADE, position SMALLINT, original_constraint_id UUID, type VARCHAR(40),
    kind VARCHAR(4), weight VARCHAR(6) NULL, all_classes BOOLEAN, class_id UUID NULL, teacher_id UUID NULL,
    subject_id UUID NULL, limit_value INT NULL, across_breaks BOOLEAN NULL, PK (generation_id, position))
                                                              -- snapshot, no FKs to master data or constraints (G7)
generation_constraint_slot (generation_id, position, day VARCHAR(9), time_slot_id UUID,
    FK (generation_id, position) → generation_constraint CASCADE)
generation_fixation (generation_id FK CASCADE, class_id UUID, subject_id UUID, teacher_id UUID)
alternative (id PK, generation_id FK CASCADE, class_fill NUMERIC(5,4), teacher_load_usage NUMERIC(5,4),
    separate_1 NUMERIC(5,4) NULL, separate_2 …, separate_3 …, other_soft NUMERIC(5,4),
    stability NUMERIC(5,4) NULL, changed_lessons INT NULL)
alternative_lesson (alternative_id FK CASCADE, class_id UUID, subject_id UUID, teacher_id UUID, room_id UUID,
    day VARCHAR(9), time_slot_id UUID)                        -- no FKs to master data (G7)
alternative_unplaced (alternative_id FK CASCADE, class_id UUID, subject_id UUID, periods INT)
alternative_soft_violation (alternative_id FK CASCADE, constraint_id UUID, violations INT)
chosen_timetable (school_id PK FK, alternative_id FK alternative RESTRICT, chosen_by FK user_account, chosen_at, version)
chosen_lesson (school_id FK chosen_timetable CASCADE, class_id FK RESTRICT, subject_id FK RESTRICT, teacher_id FK RESTRICT,
    room_id FK RESTRICT, day VARCHAR(9), time_slot_id FK RESTRICT)
chosen_unplaced (school_id FK chosen_timetable CASCADE, class_id FK RESTRICT, subject_id FK RESTRICT, periods INT)
-- seed: the two default constraints (FR-013c) for the existing school
```

`alternative RESTRICT` on `chosen_timetable` makes deleting the generation of the chosen timetable impossible at database level too (FR-024).

## Deletion dependants (extends 001 FR-023)

| Deleted master data | New dependants listed |
|---------------------|----------------------|
| Class, teacher, subject | Constraints and fixations referencing it ("Bedingung: Klasse 6b: höchstens 6 Stunden pro Tag"), chosen timetable |
| Room | Class using it as home room, chosen timetable |
| Time slot | Constraints with that slot, chosen timetable |
| Constraint | Not blocked; separate-objective references become "deleted constraint" |

New `DependantType` values: `SCHEDULING_CONSTRAINT`, `TEACHER_FIXATION`, `CHOSEN_TIMETABLE`.

## New readiness issue

`INVALID_TEACHER_FIXATION(fixationId, reason: NOT_QUALIFIED | NOT_IN_CURRICULUM | EXCEEDS_LOAD)` — blocking for generation (G11).

## Access rules

All new use cases go through `SchoolAccessPolicy` (active planner of the school, FR-025); teachers get `403`, foreign school ids `404` (001 R17).
