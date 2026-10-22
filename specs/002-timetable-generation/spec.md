# Feature Specification: Timetable Generation with Planner-Defined Constraints

**Feature Branch**: `002-timetable-generation`

**Created**: 2026-10-09

**Status**: Draft

**Input**: User description: "As a user, i want to have a scheduler functionality that provides me with several equally optimal solutions for distributing teachers and subjects over the time tables for each class. For this scheduler, I want to be able to define constraints for the scheduling. See @docs/APP_CONTEXT.md"

## Context

Feature 001 lets the planner capture the master data of the school (rooms, time grid, subjects, class levels with curricula, classes with priority weights, teachers with qualifications, weekly loads and unavailabilities) and check its readiness. This feature uses that data to generate timetables: for every class it places the required lessons into the weekly time grid and assigns a qualified teacher and a suitable room to each lesson.

There is rarely one single "best" timetable. Filling every class completely, giving teachers their full weekly load and respecting the planner's wishes (e.g., no free periods in between lessons for classes) compete with each other. Following the project's core principle (Constitution IV), the scheduler therefore presents a set of **equally optimal (Pareto-optimal) alternatives**: no alternative in the set is better than another in every respect. The planner compares the trade-offs and chooses the timetable the school will use. Hard constraints are never violated by any alternative.

## Clarifications

### Session 2026-10-09

- Q: Which constraint types should the catalogue offer in this version? → A: Rules for distributing lessons over the week (limits per day, free periods, subject frequency per day, double periods, excluded and preferred slots) plus optional teacher fixation: the planner can fix the teacher of selected subjects in selected classes; all other subjects are assigned by the scheduler. Fixing lessons to slots is not included.
- Q: How should soft constraints enter the comparison of alternatives? → A: The planner selects, when starting a generation, which soft constraints are compared as separate objectives; all other soft constraints are combined into one objective by their weights.
- Q: What belongs to this version after choosing a timetable? → A: Choosing only; showing timetables to teachers and editing timetables manually are later features.
- Q: Where should lessons take place whose subject requires no special room features? → A: Each class can optionally have a home room; lessons without required room features take place there (hard). Classes without a home room get any free room.
- Q: May the planner choose an alternative in which some lessons could not be placed? → A: Yes, after the unplaced lessons per class have been shown and the planner has confirmed them; the chosen timetable keeps showing them as not placed.
- Q: Should a new generation take the chosen timetable into account so that small changes move as few lessons as possible? → A: Optionally, chosen when starting ("start from the chosen timetable"); the number of lessons changed compared to the chosen timetable then becomes an additional objective (fewer is better).
- Q: Should sensible default constraints exist from the start? → A: Yes, school-wide soft constraints with weight medium that can be changed or switched off: every subject at most 2 periods per day per class, and no free periods between lessons for classes; they apply to all classes, including classes created later.
- Q: Should every week look the same, or does the school need A/B weeks (timetables alternating every second week)? → A: Every week is the same; A/B weeks are out of scope for this version and come as a separate later feature.
- Q: How should the chosen timetable react when teaching demand changes after choosing (e.g., a new class, more or fewer periods in a curriculum)? → A: Missing lessons appear as "not placed", lessons no longer required are marked "no longer required", and the page shows the "no longer fits the current data" hint with a link to a new generation from the chosen timetable; the chosen timetable itself stays unchanged.

### Session 2026-10-22

- Q: Must a generation always return at least 3 alternatives (SC-001), even when one timetable is best in every objective? → A: No. One alternative is sufficient if it places every lesson of every class and violates no constraint (hard or soft); otherwise at least 3 alternatives are expected.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Generate equally optimal timetables (Priority: P1)

The planner starts the scheduler for the school. The scheduler uses the current master data and all hard constraints from feature 001 and produces several alternative timetables. Each alternative contains a complete weekly timetable for every class: which subject is taught in which slot, by which teacher and in which room. The planner sees the list of alternatives together with how well each one achieves the objectives (e.g., how completely the classes are filled, how fully teachers' loads are used), and can open each alternative to view the timetable of any class.

**Why this priority**: Generating valid timetables is the purpose of the application; without it, no other part of this feature has value.

**Independent Test**: With a ready data set (no blocking readiness issues), start a generation, wait for it to finish and verify that at least one alternative exists, that no alternative violates a hard constraint and that no alternative is better than another one in every objective.

**Acceptance Scenarios**:

1. **Given** master data without blocking readiness issues, **When** the planner starts a generation, **Then** the system shows that the generation is running and how many alternatives have been found so far.
2. **Given** a finished generation, **When** the planner opens its result, **Then** they see the alternatives with their objective values, and no alternative in the list is better than or equal to another one in all objectives while being strictly better in at least one (no dominated alternatives).
3. **Given** a finished generation, **When** the planner opens an alternative and selects a class, **Then** they see that class's weekly timetable as a grid of teaching days and slots, each lesson showing subject, teacher short code and room; breaks are shown as breaks.
4. **Given** any alternative, **When** it is checked against the master data, **Then** no teacher, class or room is booked twice in the same slot, every lesson is taught by a teacher qualified for the subject, takes place in a room offering all required features of the subject (or, for subjects without required features, in the class's home room if it has one), lies in a teaching slot on a teaching day, no teacher exceeds their weekly load and no lesson lies in a slot in which its teacher is unavailable.
5. **Given** a class whose curriculum cannot be placed completely without violating a hard constraint, **When** the generation finishes, **Then** each alternative lists the lessons it could not place per class, instead of violating a constraint.
6. **Given** master data with blocking readiness issues (e.g., a teacher's load exceeds the teaching slots per week), **When** the planner tries to start a generation, **Then** the start is refused and the planner is pointed to the readiness overview.
7. **Given** class 5a with home room A-101, **When** a generation runs, **Then** in every alternative all lessons of 5a whose subject requires no room features take place in A-101, and lessons requiring features (e.g., Chemistry) take place in a room offering them.
8. **Given** a running generation, **When** the planner stops it, **Then** the alternatives found so far are kept as the result and marked as stopped early.

---

### User Story 2 - Define scheduling constraints (Priority: P2)

The planner defines additional constraints that the scheduler must or should respect, beyond the ones contained in the master data. They choose a constraint type from a catalogue, fill in its parameters (e.g., "class 6b: at most 6 periods per day"), and mark it as **hard** (must never be violated) or **soft** (should be respected as far as possible, with a weight expressing its importance). Constraints can be viewed, changed, switched off temporarily and deleted. The next generation takes them into account.

In addition, the planner can fix the teacher for selected subjects of selected classes (e.g., "Mathematics 6b is taught by SCHA"). Fixing is optional: for every subject of a class without a fixed teacher, the scheduler chooses a qualified teacher itself. Fixing individual lessons to slots is not part of this version.

When starting a generation, the planner can select up to 3 active soft constraints that are compared as separate objectives instead of being combined with the others.

**Why this priority**: The master data constraints alone produce valid but often impractical timetables; planner-defined constraints make the alternatives usable for the school. Generation (US1) already delivers value without them.

**Independent Test**: Define a hard constraint (e.g., "class 5a: at most 5 periods per day") and a soft constraint, generate, and verify that no alternative violates the hard constraint and that the result shows for each alternative how well the soft constraint is met.

**Acceptance Scenarios**:

1. **Given** the constraint catalogue, **When** the planner creates a constraint of a type, fills in its parameters and marks it as hard, **Then** it is saved and listed with a readable description (e.g., "Class 6b: at most 6 periods per day – hard").
2. **Given** a soft constraint, **When** the planner saves it, **Then** it carries a weight (default: medium on a scale of low / medium / high) that expresses its importance relative to other soft constraints.
3. **Given** a hard constraint, **When** a generation runs, **Then** no alternative violates it.
4. **Given** soft constraints, **When** a generation finishes, **Then** each alternative shows how many soft constraints it violates and which ones, per class and teacher where applicable.
5. **Given** a constraint with invalid parameters (e.g., at most 0 periods per day, a slot that is not a teaching slot, a class that does not exist), **When** the planner saves it, **Then** it is rejected with a message naming the problem.
6. **Given** a constraint that refers to a class, teacher, subject or slot, **When** that entry is deleted in the master data, **Then** the deletion is blocked and the constraint is listed as a dependent entry (as for other dependencies in feature 001).
7. **Given** a constraint that is switched off, **When** a generation runs, **Then** the constraint is ignored, and it is still listed and can be switched on again.
8. **Given** a new school, **When** the planner opens the constraint list for the first time, **Then** it contains the two school-wide default constraints (subject at most 2 periods per day; no free periods for classes), both soft with weight medium and active.
9. **Given** the school-wide constraint "subject at most 2 periods per day" and a constraint "Mathematics in 6b at most 1 period per day", **When** a generation runs, **Then** for 6b only the specific constraint applies to Mathematics, and the school-wide one to all other subjects and classes.
10. **Given** the planner has fixed SCHA as the teacher of Mathematics in 6b and fixed no teacher for the other subjects of 6b, **When** a generation runs, **Then** in every alternative all Mathematics lessons of 6b are taught by SCHA, and the other subjects of 6b are taught by qualified teachers chosen by the scheduler.
11. **Given** a teacher who is not qualified for a subject, **When** the planner tries to fix them as the teacher of that subject in a class, **Then** the fixation is rejected with a message naming the problem.
12. **Given** fixed teachers whose fixed lessons together exceed a teacher's weekly load, **When** the planner saves the fixation that causes it, **Then** it is rejected with a message naming the teacher's load and the fixed periods.
13. **Given** active soft constraints, **When** the planner starts a generation and selects one of them (e.g., "no free periods for class 5a") as a separate objective, **Then** the result shows a value for that constraint for each alternative, next to the class fill, the teacher load usage and the combined value of all other soft constraints.
14. **Given** the planner selects a fourth soft constraint as a separate objective, **When** they start the generation, **Then** the start is refused with a message that at most 3 soft constraints can be compared separately.

---

### User Story 3 - Compare alternatives and choose the timetable (Priority: P3)

The planner compares the alternatives of a generation: they see the trade-off between the objectives across all alternatives (e.g., as a table sortable by each objective and a chart of the trade-off curve), can pick two alternatives and see where their timetables differ, and finally choose one alternative as the school's chosen timetable. Later generations can start from the chosen timetable so that as few lessons as possible change.

**Why this priority**: Constitution IV requires that the planner can compare the trade-offs instead of the system silently picking one answer; choosing makes the result usable. A planner can already work with US1 alone by opening alternatives one by one.

**Independent Test**: Generate at least two alternatives, compare them side by side, choose one and verify that it is marked as the chosen timetable and stays chosen after a new generation.

**Acceptance Scenarios**:

1. **Given** a result with several alternatives, **When** the planner opens the comparison, **Then** they see all alternatives with their values for each objective, can sort by each objective, and see the trade-off between the objectives as a chart.
2. **Given** two alternatives, **When** the planner compares them, **Then** for a selected class, teacher or room the system highlights the slots in which the two timetables differ.
3. **Given** an alternative, **When** the planner chooses it, **Then** it is marked as the chosen timetable of the school; a previously chosen timetable loses this mark.
4. **Given** an alternative with unplaced lessons, **When** the planner chooses it, **Then** the system first lists the unplaced lessons per class and asks for confirmation; only after confirming is it chosen, and the chosen timetable shows the unplaced lessons per class as "not placed". Without confirmation nothing changes.
5. **Given** a chosen timetable, **When** the planner starts a new generation, **Then** the chosen timetable remains unchanged and chosen until the planner chooses another one.
6. **Given** an alternative, **When** the planner views it per teacher or per room instead of per class, **Then** they see that teacher's or room's weekly timetable.
7. **Given** an outdated alternative in which a lesson now violates a hard constraint (e.g., its teacher has since become unavailable in that slot), **When** the planner tries to choose it, **Then** the choice is refused, the conflicting lessons are named with the violated rule, and the planner is advised to start a new generation (optionally from the chosen timetable); the previously chosen timetable stays unchanged.
8. **Given** a chosen timetable and a teacher who has since become unavailable in one of its slots, **When** the planner starts a generation with "start from the chosen timetable", **Then** each alternative shows the number of lessons changed compared to the chosen timetable as an additional objective, and at least one alternative changes only few lessons besides the conflicting one.
9. **Given** no chosen timetable exists, **When** the planner opens the start form, **Then** the option "start from the chosen timetable" is not offered.
10. **Given** a chosen timetable and a teacher who has since become unavailable in one of its slots, **When** the planner opens the chosen timetable, **Then** the conflicting lesson is highlighted with the violated rule, a hint says the chosen timetable no longer fits the current data and offers a new generation from the chosen timetable, and the chosen timetable itself is unchanged.
11. **Given** a chosen timetable, **When** the planner afterwards creates class 5c and reduces Mathematics in level 6 from 4 to 3 periods, **Then** the chosen timetable lists all lessons of 5c as "not placed", marks the last Mathematics lesson of the week of each level-6 class as "no longer required", shows the "no longer fits" hint with the link to a new generation, and is otherwise unchanged.

---

### User Story 4 - Keep track of generations when master data changes (Priority: P4)

Planners keep earlier generation results to come back to them. When the master data or the constraints change after a generation, the result is marked as outdated, with a hint that a new generation would take the changes into account; earlier results stay viewable. The planner can delete results they no longer need.

**Why this priority**: Prevents planners from unknowingly choosing a timetable that no longer matches the data, but is not needed for the first generation.

**Independent Test**: Generate, then change a teacher's unavailability; verify the result is marked as outdated and still viewable, and that an alternative which now conflicts with the changed data shows the conflicts.

**Acceptance Scenarios**:

1. **Given** finished generations, **When** the planner opens the list of generations, **Then** they see each with its start time, the planner who started it, its status (running, finished, stopped early, failed, failed because hard constraints could not be met without a single valid alternative), its number of alternatives and whether it is outdated.
2. **Given** a finished generation, **When** master data or constraints used by it change, **Then** the generation is marked as outdated.
3. **Given** an outdated alternative, **When** the planner opens it, **Then** lessons that now violate a hard constraint (e.g., a teacher became unavailable in that slot) are highlighted.
4. **Given** a generation that does not contain the chosen timetable, **When** the planner deletes it, **Then** it is removed after confirmation; a generation containing the chosen timetable cannot be deleted until another timetable is chosen.

---

### Edge Cases

- No alternative can place all lessons: the alternatives still respect all hard constraints and list the unplaced lessons per class; the planner can use the readiness overview and constraints to find the cause.
- Hard constraints contradict each other or the master data so that no lesson arrangement is possible at all (e.g., "class 5a at least 7 periods per day" with only 6 teaching slots per day): the generation ends with a message naming the conflicting constraints, without alternatives.
- Two alternatives have identical objective values but different timetables: both are kept as distinct alternatives (both are equally optimal).
- A planner starts a generation while another generation for the school is already running: the start is refused with a hint to the running generation, which every planner can see and stop.
- The server is restarted while a generation is running: the generation is marked as failed; already found alternatives are kept if they were saved.
- Master data changes while a generation is running: the generation continues with the data as of its start and is marked as outdated when it finishes.
- A soft constraint refers to the same thing as a hard one (e.g., hard "at most 6 per day" and soft "at most 5 per day" for the same class): both are allowed; the soft one is only evaluated within the hard one.
- An entry referenced by the chosen timetable is deleted: the deletion is blocked like other dependencies, listing the chosen timetable.
- A planner assigns a room as home room that is already the home room of another class: rejected, naming the other class. A home room is deleted: blocked, the class is listed as dependent entry.
- A class has a home room and a subject without required features: all its lessons of that subject are in the home room; the home room may also be used by lessons of other classes (e.g., a subject requiring a feature the home room offers) in slots where its class is not using it. A room is never booked twice in the same slot; when two lessons compete for the home room, the scheduler moves one of them to another slot or another suitable room, or leaves one unplaced. Neither the home class nor the other class has priority.
- A teacher fixation becomes invalid after it was saved (e.g., the teacher's qualification for the subject is removed, the subject is removed from the class level's curriculum, or the teacher's load is reduced below the fixed periods): the master data change is saved, the fixation is marked as invalid in the constraint list, and generations cannot be started until it is corrected or deleted.
- A generation starts from the chosen timetable and the planner chooses another timetable while it runs: the generation keeps the reference it started with; its result states which timetable it was compared with.
- A soft constraint selected as a separate objective is switched off or deleted after the generation: the generation keeps showing its values as recorded; the generation is marked as outdated.
- The generation takes long: the planner can leave the page and come back; the generation continues and its progress is shown again.
- Teachers (role teacher) try to open generations, alternatives or constraints: access is denied and no data is revealed.

## Requirements *(mandatory)*

### Functional Requirements

**Generation**

- **FR-001**: Planners MUST be able to start a generation of timetables for the school based on the current master data and the active constraints.
- **FR-002**: A generation MUST produce, for every class, an assignment of the lessons required by its curriculum (subject and number of periods per week) to teaching slots on teaching days, each with exactly one teacher and one room.
- **FR-002a**: Planners MUST be able to assign each class an optional home room in the class master data (extends feature 001); a room can be the home room of at most one class. Lessons of a subject without required room features MUST take place in the class's home room; lessons of subjects with required features take place in a room offering them. Classes without a home room get any room that is free.
- **FR-003**: Every alternative MUST respect all hard constraints: no teacher, class or room booked twice in the same slot; teacher qualified for the subject; room offers all required features of the subject; lessons without required room features in the class's home room, if it has one (FR-002a); lesson in a teaching slot on a teaching day; teacher's weekly load not exceeded; teacher not unavailable in that slot (feature 001, FR-022); and every active planner-defined hard constraint (FR-012).
- **FR-004**: All periods of one subject in one class MUST be taught by the same teacher within an alternative.
- **FR-005**: If lessons cannot be placed without violating a hard constraint, the alternative MUST leave them unplaced and list them per class; it MUST NOT violate a hard constraint instead.
- **FR-006**: The system MUST evaluate each alternative by the following objectives: (1) how completely the classes' curricula are placed, weighted by the classes' priority weights (feature 001, FR-015); (2) how fully the teachers' weekly loads are used (feature 001, FR-018); (3) one objective for each soft constraint the planner has selected as a separate objective for this generation (FR-006a); (4) how well all other active soft constraints are met together, weighted by their weights; (5) only when the generation starts from the chosen timetable (FR-006b): how few lessons are changed compared to the chosen timetable.
- **FR-006a**: When starting a generation, planners MUST be able to select active soft constraints that are compared as separate objectives (at most 3; default: none). The selection MUST be stored with the generation and shown with its result.
- **FR-006b**: When a chosen timetable exists, planners MUST be able to start a generation "from the chosen timetable". The number of changed lessons then becomes an additional objective (fewer is better); a lesson counts as changed if its class and subject are no longer taught in the same slot by the same teacher in the same room. Lessons of the chosen timetable that now violate a hard constraint must change. The option and the chosen timetable used as reference MUST be stored with the generation; it does not count against the limit of 3 separate soft-constraint objectives.
- **FR-007**: The result of a generation MUST contain only alternatives that are not dominated by another alternative of the same generation (no other alternative is at least as good in all objectives and better in at least one). Alternatives with identical objective values but different timetables MUST be kept as separate alternatives.
- **FR-008**: The number of alternatives per generation MUST be limited (default: at most 10); if more equally optimal alternatives exist, the system MUST prefer alternatives that differ most in their objective values, so that the trade-off range is covered.
- **FR-009**: A generation MUST end after a time limit set by the planner when starting it (default: 10 minutes, at most 60 minutes) or when the planner stops it; alternatives found until then are the result.
- **FR-010**: The system MUST refuse to start a generation while readiness issues exist that must be fixed before scheduling (feature 001: teacher load exceeding the teaching slots per week, class levels whose weekly periods exceed the teaching slots) or while a teacher fixation has become invalid after master data changes (FR-013a), and MUST name them; other readiness issues MUST be shown as a warning that the planner can confirm.
- **FR-011**: At most one generation per school MUST run at a time; every planner MUST be able to see the progress of the running generation (elapsed time, alternatives found so far) and stop it.

**Constraints**

- **FR-012**: Planners MUST be able to create, view, change, switch off/on and delete scheduling constraints chosen from a catalogue of constraint types with parameters. Each constraint MUST be marked as hard or soft.
- **FR-013**: The catalogue MUST offer at least the following constraint types: maximum and minimum periods per day for a class; maximum periods per day for a teacher; no free periods between lessons of a class on a day; maximum number of free periods between lessons of a teacher on a day; a subject at most a given number of periods per day in a class; a subject taught as double periods (two consecutive slots) in a class; a subject, class or teacher not scheduled in given slots; a teacher's preferred free slots or days.
- **FR-013a**: Planners MUST be able to fix the teacher of a subject in a class (teacher fixation). A fixation is always a hard constraint; it is optional per subject and class, and subjects without a fixation are assigned by the scheduler (FR-004). A fixation MUST be rejected if the teacher is not qualified for the subject, if the subject is not in the class's curriculum, or if the teacher's fixed periods would exceed their weekly load. Fixing lessons to slots is out of scope.
- **FR-013b**: Class-related constraint types (limits per day for a class, no free periods for a class, subject at most a given number of periods per day) MUST also be definable for **all classes** of the school; such a school-wide constraint applies to every class, including classes created later. A constraint of the same type for a specific class replaces the school-wide one for that class.
- **FR-013c**: Every school MUST start with two school-wide default constraints, both soft with weight medium: "every subject at most 2 periods per day in a class" and "no free periods between lessons for classes". Planners can change, switch off or delete them like any other constraint.
- **FR-014**: Soft constraints MUST carry a weight (low, medium, high; default medium); a constraint's violations MUST count more the higher its weight.
- **FR-015**: The system MUST reject constraints with invalid parameters or references (non-existent entries, values outside the time grid, a hard minimum periods per day above an active hard maximum periods per day of the same class) with a message naming the problem.
- **FR-016**: The system MUST prevent deletion of master data entries referenced by constraints, by a class as its home room, or by the chosen timetable and list them as dependent entries (feature 001, FR-023).
- **FR-017**: If the active hard constraints contradict each other or the master data so that no valid arrangement exists, the generation MUST end with a message naming the constraints involved, where they can be determined.

**Results, comparison and choice**

- **FR-018**: For each alternative the system MUST show its objective values, the number of unplaced lessons, and the violated soft constraints.
- **FR-019**: Planners MUST be able to view each alternative per class, per teacher and per room as a weekly grid of teaching days and slots; each lesson shows subject, class, teacher short code and room as applicable.
- **FR-020**: Planners MUST be able to compare all alternatives of a generation by their objective values (sortable table and a chart of the trade-off between two objectives the planner selects) and to compare two alternatives timetable by timetable, with differences highlighted.
- **FR-021**: Planners MUST be able to choose exactly one alternative as the school's chosen timetable; choosing another one replaces the choice. The system MUST refuse to choose an alternative that violates a hard constraint under the current master data and constraints (e.g., an outdated alternative after a new unavailability), name the conflicting lessons with the violated rule and point to starting a new generation; outdated alternatives without such conflicts can be chosen. If the alternative has unplaced lessons, the system MUST list them per class and require an explicit confirmation before choosing; the chosen timetable MUST keep showing them as not placed. The chosen timetable MUST stay unchanged when new generations run. Showing the chosen timetable to teachers and editing timetables manually are not part of this version.
- **FR-022**: Generations and their alternatives MUST be stored persistently, with start time, starting planner, time limit, status (running, finished, stopped early, failed, failed because hard constraints could not be met without a single valid alternative), a snapshot of the constraints and teacher fixations in effect at the start (shown with the result and unchanged by later changes or deletions of constraints), and an indication whether master data or constraints changed since (outdated).
- **FR-023**: When an outdated alternative or the chosen timetable is viewed, lessons that violate a hard constraint under the current master data and constraints MUST be highlighted with the violated rule. For the chosen timetable the page MUST additionally compare it with the current curricula: lessons now required but not in it (e.g., a new class, more weekly periods) MUST be listed per class as "not placed", and lessons no longer required (e.g., fewer weekly periods, a subject removed from a curriculum) MUST be marked as "no longer required". If fewer lessons of a subject are required than the chosen timetable contains, the lessons marked "no longer required" are the last ones of that subject in week order (latest teaching day, then latest slot). Whenever there are conflicts or such differences, the page MUST show a hint that the chosen timetable no longer fits the current data and offer starting a new generation from the chosen timetable (FR-006b); the chosen timetable itself stays unchanged.
- **FR-024**: Planners MUST be able to delete generations, except the one containing the chosen timetable.

**Access, confidentiality, language**

- **FR-025**: Only authenticated users with the planner role MUST be able to start, view, stop or delete generations, view or choose alternatives and manage constraints.
- **FR-026**: Timetables, constraints and teacher data MUST NOT appear in logs, technical error messages or diagnostic output; logs record only identifiers and event types (e.g., generation started/finished, number of alternatives, duration). Business messages (e.g., which constraints contradict each other) may name entries only to users allowed to see them (Constitution III).
- **FR-027**: All new interface texts (including constraint descriptions, objective names, statuses and messages) MUST be available in German and English, following the language rules of feature 001 (FR-037 – FR-048).
- **FR-028**: Concurrent changes to the same constraint, and concurrent choices of the chosen timetable, MUST be detected; a later save MUST NOT silently overwrite an earlier one (feature 001, FR-027).

### Key Entities

- **Scheduling Constraint**: A planner-defined rule of a type from the catalogue, with parameters (e.g., a class or all classes, teacher, subject, slots, limits), a kind (hard or soft), a weight for soft constraints and an active/switched-off state; belongs to the school.
- **Constraint Type**: An entry of the catalogue describing what a constraint means and which parameters it needs (e.g., "maximum periods per day for a class").
- **Generation**: One run of the scheduler: start time, starting planner, time limit, status, the master data and constraints in effect at the start, the soft constraints selected as separate objectives, whether it starts from the chosen timetable (and which one), the alternatives found and whether it is outdated.
- **Alternative (Timetable Solution)**: One complete, hard-constraint-respecting assignment of lessons for all classes within a generation; has a value for each objective, the list of unplaced lessons and the violated soft constraints.
- **Lesson Assignment**: One lesson within an alternative: class, subject, teaching day, slot, teacher and room.
- **Home Room**: An optional room of a class (master data, extends feature 001) in which all lessons of subjects without required room features take place; at most one class per room.
- **Teacher Fixation**: A hard constraint stating which teacher teaches a subject in a class; optional per subject and class.
- **Objective**: A criterion by which alternatives are compared: class fill weighted by priority, teacher load usage, each soft constraint selected as a separate objective, the combined satisfaction of all other soft constraints and, when starting from the chosen timetable, the number of changed lessons.
- **Chosen Timetable**: The one alternative the planner has chosen as the school's timetable, including its lessons that were not placed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a typical school (40 rooms, 15 subjects, 6 class levels, 25 classes, 60 teachers, 5-day grid with 10 slots, about 20 planner-defined constraints), a generation with the default time limit returns at least 3 alternatives — or a single alternative if that one places every lesson of every class and violates no constraint (hard or soft), since no other alternative can then be equally good — and the first alternative is shown within 2 minutes after the start.
- **SC-002**: In automated checks over seeded test data sets, 0 alternatives violate a hard constraint.
- **SC-003**: In automated checks, 0 alternatives in a result are dominated by another alternative of the same result.
- **SC-004**: For a seeded data set that has a known complete solution, at least one alternative places 100% of all lessons.
- **SC-005**: A planner can create a constraint from the catalogue in under 1 minute.
- **SC-006**: A planner can identify the alternative with the best value for any single objective, and see what it costs in the other objectives, within 1 minute of opening the comparison.
- **SC-007**: 90% of planners who already use the master data screens can generate, compare and choose a timetable without help or documentation.
- **SC-008**: 0 generation results, alternatives or constraints are visible to users without the planner role.
- **SC-009**: All generations, alternatives, constraints and the chosen timetable are available unchanged after a restart of the application (0 data loss), except for a generation running during the restart (FR-022, edge cases).

## Assumptions

- This feature builds on feature 001: it uses its master data, readiness overview, login, roles, single-school scope and interface languages. One installation manages exactly one school.
- "Equally optimal solutions" means Pareto-optimal alternatives (Constitution IV): none is better than another in every objective. The planner decides which trade-off to take.
- The scheduler is not required to find the mathematically best possible alternatives; within the time limit it finds the best it can, and the result is presented as the non-dominated set of what it found.
- The scheduler decides which qualified teacher teaches a subject in a class, unless the planner has fixed the teacher (FR-013a), and which suitable room is used, unless the class has a home room and the subject requires no room features (FR-002a).
- At most 3 soft constraints can be compared as separate objectives, because with more objectives nearly every alternative becomes equally optimal and the comparison loses its value; all other soft constraints are combined by their weights.
- Showing timetables to users with the teacher role and manually editing timetables (including fixing lessons to slots) are later features; this version ends with choosing a timetable.
- Every curriculum subject of a class is taught to the whole class; split groups, electives and lessons shared by several classes are out of scope (as in feature 001).
- Lessons have the length of one slot; double periods are expressed as a constraint over two consecutive slots. A double period does not span a break unless the planner allows it in the constraint.
- Constraints are chosen from a predefined catalogue; a free-form rule language is out of scope.
- Exports (e.g., PDF or print views) and import of timetables are out of scope; viewing in the web application is sufficient for this version.
- Substitution planning (daily changes, absences) is out of scope.
- Every week has the same timetable. A/B weeks (alternating timetables, e.g., for subjects with 1.5 periods per week on average) are out of scope and expected as a separate later feature; curricula therefore keep whole periods per week (feature 001).
- A generation runs in the background on the server; planners can leave the page and return.
