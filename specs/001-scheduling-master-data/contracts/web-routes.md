# Contract: Web Routes (Planner UI)

**Feature**: [spec.md](../spec.md) | **Data model**: [data-model.md](../data-model.md)

Server-rendered HTML (R2). All `GET` routes render a page; all `POST` routes accept `application/x-www-form-urlencoded` and answer with `303 See Other` (Post/Redirect/Get) on success.

## Common rules

| Rule | Behaviour |
|------|-----------|
| Authentication | Every route except `/login`, `/static/*` requires a session. No session → `302` to `/login`. |
| Authorization | Every route under `/schools`, `/teachers`, `/accounts` requires an active account with role `PLANNER`. Other role → `403` page without any master data (FR-028, SC-006). |
| School scope | `/schools` lists only assigned schools. Any `/schools/{schoolId}/…` of an unassigned school → `404` (existence not revealed, FR-028a). `/teachers` lists only teachers of assigned schools; teacher edits are limited per data-model "Access rules" (FR-028b), otherwise `403`. |
| Validation error | `422` re-rendering the form with entered values and one business message per violated rule (SC-003). |
| Version conflict | `409` page: "Der Eintrag wurde inzwischen geändert" with a reload link (FR-027). |
| Deletion blocked | `409` business-error page listing the blocking dependants the planner may see by name; dependants of other schools only as a count (FR-023, constitution III, R10). |
| Unknown id | `404` generic page. |
| Unexpected error | `500` technical-error page: generic text + correlation id only, never data or exception text (R10, FR-029, constitution III). |
| CSRF | Every form contains a per-session token; `POST` without valid token → `403`. |
| Optimistic locking | Every edit/delete form carries hidden field `version`. |

## Authentication

| Method | Path | Form fields | Result |
|--------|------|-------------|--------|
| GET | `/login` | | Login form |
| POST | `/login` | `username`, `password` | Success → `/schools` for planners, `/home` for teachers; failure → `401` form with generic message (no hint which field was wrong) |
| GET | `/home` | | Teacher start page: "Für Lehrkräfte ist noch keine Ansicht verfügbar", links to password change and logout; no master data |
| POST | `/logout` | | Session invalidated → `/login` |

## Schools (FR-001)

| Method | Path | Form fields |
|--------|------|-------------|
| GET | `/schools` | list |
| POST | `/schools` | `name` |
| GET | `/schools/{schoolId}` | school dashboard (links to all sections + readiness summary) |
| POST | `/schools/{schoolId}` | `name`, `version` |
| POST | `/schools/{schoolId}/delete` | `version` |

## Room features and rooms (FR-002, FR-003)

| Method | Path | Form fields |
|--------|------|-------------|
| GET | `/schools/{schoolId}/room-features` | list |
| POST | `/schools/{schoolId}/room-features` | `name` |
| POST | `/schools/{schoolId}/room-features/{featureId}/delete` | |
| GET | `/schools/{schoolId}/rooms` | list |
| POST | `/schools/{schoolId}/rooms` | `identifier`, `featureId` (repeatable) |
| GET | `/schools/{schoolId}/rooms/{roomId}` | edit form |
| POST | `/schools/{schoolId}/rooms/{roomId}` | `identifier`, `featureId`*, `version` |
| POST | `/schools/{schoolId}/rooms/{roomId}/delete` | `version` |

## Time grid (FR-004 – FR-007)

| Method | Path | Form fields |
|--------|------|-------------|
| GET | `/schools/{schoolId}/time-grid` | weekly grid view (days × slots, breaks visually distinct) |
| POST | `/schools/{schoolId}/time-grid/days` | `dayOfWeek` (repeatable), `version` (school) |
| POST | `/schools/{schoolId}/time-grid/slots` | `label`, `startTime` (HH:mm), `endTime`, `kind` |
| POST | `/schools/{schoolId}/time-grid/slots/{slotId}` | `label`, `startTime`, `endTime`, `kind`, `version` |
| GET | `/schools/{schoolId}/time-grid/slots/{slotId}/delete` | Confirmation page: slot label/time, number of day exclusions and teacher unavailabilities that will be removed |
| POST | `/schools/{schoolId}/time-grid/slots/{slotId}/delete` | `version` |
| POST | `/schools/{schoolId}/time-grid/slots/{slotId}/exclusions` | `dayOfWeek` (repeatable) — days on which the slot does not exist |

## Subjects (FR-008, FR-009)

| Method | Path | Form fields |
|--------|------|-------------|
| GET | `/schools/{schoolId}/subjects` | list |
| POST | `/schools/{schoolId}/subjects` | `name`, `requiredFeatureId`* |
| GET | `/schools/{schoolId}/subjects/{subjectId}` | edit form |
| POST | `/schools/{schoolId}/subjects/{subjectId}` | `name`, `requiredFeatureId`*, `version` |
| POST | `/schools/{schoolId}/subjects/{subjectId}/delete` | `version` |

## Class levels and classes (FR-010 – FR-015)

| Method | Path | Form fields |
|--------|------|-------------|
| GET | `/schools/{schoolId}/class-levels` | list with weekly totals and classes per level |
| POST | `/schools/{schoolId}/class-levels` | `identifier` |
| GET | `/schools/{schoolId}/class-levels/{levelId}` | curriculum editor + classes of the level; shows grid-exceeded warning |
| POST | `/schools/{schoolId}/class-levels/{levelId}` | `identifier`, `subjectId[i]`, `periodsPerWeek[i]`, `version` |
| POST | `/schools/{schoolId}/class-levels/{levelId}/delete` | `version` |
| GET | `/schools/{schoolId}/class-levels/{levelId}/classes/new` | form pre-filled with proposed suffix (FR-013) |
| POST | `/schools/{schoolId}/class-levels/{levelId}/classes` | `suffix`, `priorityWeight` |
| GET | `/schools/{schoolId}/classes/{classId}` | class details incl. resolved curriculum (read only, FR-014) |
| POST | `/schools/{schoolId}/classes/{classId}` | `suffix`, `priorityWeight`, `version` |
| POST | `/schools/{schoolId}/classes/{classId}/delete` | `version` |

## Teachers (FR-016 – FR-021) — installation-wide

| Method | Path | Form fields |
|--------|------|-------------|
| GET | `/teachers` | list (name, short code, schools) |
| POST | `/teachers` | `name`, `shortCode`, `weeklyLoad`, `schoolId`+ |
| GET | `/teachers/{teacherId}` | edit page with sections below |
| POST | `/teachers/{teacherId}` | `name`, `shortCode`, `weeklyLoad`, `schoolId`+, `version` |
| POST | `/teachers/{teacherId}/qualifications` | `subjectId`*, `version` |
| POST | `/teachers/{teacherId}/commute-times` | `schoolAId[i]`, `schoolBId[i]`, `minutes[i]`, `version` |
| POST | `/teachers/{teacherId}/unavailability` | `slot` (repeatable, format `{dayOfWeek}:{slotId}`), `version` |
| POST | `/teachers/{teacherId}/delete` | `version` — only if all of the teacher's schools are the planner's, else `403` (FR-028c) |
| POST | `/teachers/{teacherId}/remove-from-my-schools` | `version` — removes the planner's schools and the related qualifications, unavailabilities and commute times (FR-028c) |

## User accounts (FR-030 – FR-035)

| Method | Path | Form fields |
|--------|------|-------------|
| GET | `/accounts` | only manageable accounts: all of their schools are the current planner's (FR-032a); username, role, active, schools |
| POST | `/accounts` | `username`, `role`, `initialPassword`, `schoolId`+ (≥ 1, own schools only, FR-031a) |
| GET | `/accounts/{accountId}` | edit form |
| POST | `/accounts/{accountId}` | `role`, `schoolId`+ (≥ 1, own schools only), `version` — FR-031a/FR-033 violation → `422` |
| POST | `/accounts/{accountId}/deactivate` | `version` — FR-033 violation → `422` |
| POST | `/accounts/{accountId}/activate` | `version` |
| POST | `/accounts/{accountId}/password` | `newPassword` (≥ 10 chars) |

All `/accounts/{accountId}…` routes answer `404` for accounts that are not manageable by the current planner (FR-032a).
| GET | `/account/password` | own password change form (any logged-in user) |
| POST | `/account/password` | `currentPassword`, `newPassword` |

## Readiness (FR-024, FR-025)

| Method | Path | Result |
|--------|------|--------|
| GET | `/schools/{schoolId}/readiness` | List of readiness issues grouped by code (see data-model.md), each linking to the affected entry; or "Keine blockierenden Probleme gefunden" |

## Response content rules (constitution III)

- Pages show only data of the requested school/teacher; lists show name/identifier columns only.
- Password hashes, session ids and CSRF tokens never appear in rendered output except the token in its hidden field.
