# Contract: Web Routes (Constraints, Generations, Timetables)

**Feature**: [spec.md](../spec.md) | **Data model**: [data-model.md](../data-model.md) | **Builds on**: [001 routes](../../001-scheduling-master-data/contracts/web-routes.md)

All common rules of feature 001 apply unchanged: session required, planner role (`403` otherwise, FR-025), foreign `schoolId` → `404`, CSRF on every `POST`, `422` for validation errors with entered values, `409` for version conflicts and blocked deletions, `500` generic page with correlation id, Post/Redirect/Get with `303`, every page in the request's language with the language switch. `{s}` = `schoolId`.

## Navigation

The school navigation gets three entries after *Bereitschaft* / *Readiness*: *Bedingungen* / *Constraints*, *Stundenplanläufe* / *Generations*, *Gewählter Stundenplan* / *Chosen timetable*.

## Class master data — extension of feature 001 (FR-002a)

| Method | Path | Form fields | Result |
|--------|------|-------------|--------|
| POST | `/schools/{s}/classes` and `/schools/{s}/classes/{id}` | + optional `homeRoomId` (select of rooms, "kein Klassenraum") | `422` if the room is already another class's home room (names the class) |

## Constraints (US2)

| Method | Path | Form fields | Result |
|--------|------|-------------|--------|
| GET | `/schools/{s}/constraints` | | List of constraints (readable description, hard/soft + weight, active, invalid mark) and of teacher fixations; catalogue as links "Neue Bedingung: …" |
| GET | `/schools/{s}/constraints/new?type={TYPE}` | | Form for the type (G9): selects for class/teacher/subject, number field for limit, slot grid with checkboxes (whole-day checkbox per day for excluded slots), radio `kind`, select `weight` (shown for soft) |
| POST | `/schools/{s}/constraints` | `type`, `kind` (`HARD`\|`SOFT`), `weight` (`LOW`\|`MEDIUM`\|`HIGH`, soft only), `classId` (`ALL` = all classes for class-related types, FR-013b), `subjectId` (`ALL` = every subject for "subject per day"), `teacherId`, `subjectId`, `limit`, `acrossBreaks`, `slot` (repeated, `DAY:slotId`) — per type | `303` to list; `422` with messages (FR-015) |
| GET | `/schools/{s}/constraints/{id}` | | Edit form (type fixed) with `version` |
| POST | `/schools/{s}/constraints/{id}` | as create + `version` | `303` to list; `409` on version conflict |
| POST | `/schools/{s}/constraints/{id}/active` | `active` (`true`\|`false`), `version` | Switch on/off; `303` to list |
| POST | `/schools/{s}/constraints/{id}/delete` | `version` | `303` to list (never blocked; see data model) |
| GET | `/schools/{s}/teacher-fixations/new` | | Form: class, subject (curriculum subjects of the class), teacher (qualified teachers, with remaining load) |
| POST | `/schools/{s}/teacher-fixations` | `classId`, `subjectId`, `teacherId` | `303` to constraints list; `422` (not qualified, not in curriculum, exceeds load, already fixed) |
| GET/POST | `/schools/{s}/teacher-fixations/{id}` | `teacherId`, `version` | Change teacher |
| POST | `/schools/{s}/teacher-fixations/{id}/delete` | `version` | `303` to list |

## Generations (US1, US4)

| Method | Path | Form fields | Result |
|--------|------|-------------|--------|
| GET | `/schools/{s}/generations` | | List (start time, planner, status, alternatives, outdated mark, link); start form: `timeLimitMinutes` (default 10), `minimumPlacedShare` (default 90, FR-007a), up to 3 checkboxes over active soft constraints for separate objectives, readiness warnings with confirmation checkbox, blocking issues instead of the start button (link to readiness) |
| POST | `/schools/{s}/generations` | `timeLimitMinutes` (1–60), `minimumPlacedShare` (50–100, default 90 when missing, FR-007a), `separateObjective` (repeated constraint ids, ≤ 3), `startFromChosen` (`true`, only offered if a chosen timetable exists, FR-006b), `warningsConfirmed` | `303` to the new generation; `422` for > 3 objectives, invalid limit, a minimum share outside 50–100, unconfirmed warnings; `409` business page if blocking issues exist (named, FR-010), if contradictions are detected (FR-017, names the constraints) or if a generation is already running (link to it, FR-011) |
| GET | `/schools/{s}/generations/{g}` | | Running: progress (elapsed, alternatives so far), stop button, `<meta http-equiv="refresh" content="3">`. Finished: status, outdated hint, objective definition, constraints and teacher fixations in effect at the start (from the snapshot, current names, "gelöschter Eintrag" for deleted entries), alternatives table (objective values in %, unplaced lessons, violated soft constraints count; sortable via `?sort={objective}&dir=asc\|desc`), link to comparison |
| POST | `/schools/{s}/generations/{g}/stop` | | `303` to generation; status becomes `STOPPED` with alternatives kept |
| POST | `/schools/{s}/generations/{g}/delete` | | `303` to list; `409` if it contains the chosen timetable (FR-024) |
| GET | `/schools/{s}/generations/{g}/compare?x={objective}&y={objective}` | | Sortable table + inline SVG scatter chart of the two selected objectives; each point links to its alternative (FR-020) |

Objective keys: `classFill`, `teacherLoad`, `separate1`…`separate3`, `otherSoft`, `stability` (only for generations started from the chosen timetable; shown as changed lessons).

## Alternatives (US1, US3)

| Method | Path | Form fields | Result |
|--------|------|-------------|--------|
| GET | `/schools/{s}/generations/{g}/alternatives/{a}?view=class\|teacher\|room&id={id}` | | Weekly grid for the selected class (default: first class), teacher or room; selects to switch; objective values; unplaced lessons per class; violated soft constraints with counts; for outdated generations lessons that now violate a hard constraint are highlighted with the reason (FR-023); choose button |
| GET | `/schools/{s}/generations/{g}/alternatives/{a}/diff/{b}?view=…&id=…` | | Both grids side by side, differing cells highlighted (FR-020) |
| POST | `/schools/{s}/generations/{g}/alternatives/{a}/choose` | `chosenVersion` (version of the current choice, empty if none), `unplacedConfirmed` | Alternative with unplaced lessons and without `unplacedConfirmed=true` → `200` confirmation page listing the unplaced lessons per class with confirm/cancel (FR-021); otherwise `303` to chosen timetable; `409` on concurrent choice (FR-028); `409` business page if the alternative violates a hard constraint under current data |

## Chosen timetable (US3)

| Method | Path | Result |
|--------|------|--------|
| GET | `/schools/{s}/timetable?view=class\|teacher\|room&id={id}` | Grid of the chosen timetable with source generation, chosen by/at, and its lessons "not placed" per class; lessons that violate a hard constraint under current data highlighted with the rule, lessons no longer required by the current curricula marked "entfällt", lessons required now but missing listed as "nicht verplant" per class (incl. classes created after choosing), plus — whenever any of these exist — a hint "passt nicht mehr zu den aktuellen Daten" linking to the start form with `startFromChosen` preselected (FR-023); hint if none has been chosen yet |

## Language and texts

All new texts (constraint descriptions, catalogue names, objective names, statuses, progress, messages) exist in German and English (FR-027, G14). Names entered by users are never translated.
