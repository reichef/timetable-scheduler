# Contract: Web Routes (Planner UI)

**Feature**: [spec.md](../spec.md) | **Data model**: [data-model.md](../data-model.md)

Server-rendered HTML (R2). Single-school variant (R17): URLs keep the `/schools/{schoolId}/…` scheme so the multi-school extension only adds routes. All `GET` routes render a page; all `POST` routes accept `application/x-www-form-urlencoded` and answer with `303 See Other` (Post/Redirect/Get) on success.

## Common rules

| Rule | Behaviour |
|------|-----------|
| Authentication | Every route except `/`, `/login`, `/language`, `/static/*` requires a session. No session → `302` to `/login`. |
| Authorization | Every route under `/schools`, `/accounts` requires an active account with role `PLANNER`. Other role → `403` page without any master data (FR-028, SC-006). |
| School scope | A `schoolId` other than the installation's school → `404` (checked by `SchoolAccessPolicy`). |
| Validation error | `422` re-rendering the form with entered values and one business message per violated rule (SC-003). |
| Version conflict | `409` page: "Der Eintrag wurde inzwischen geändert" with a reload link (FR-027). |
| Deletion blocked | `409` business-error page listing the blocking dependants by name (FR-023, constitution III, R10). |
| Unknown id | `404` generic page. |
| Unexpected error | `500` technical-error page: generic text + correlation id only, never data or exception text (R10, FR-029, constitution III). |
| CSRF | Every form contains a per-session token; `POST` without valid token → `403`. |
| Optimistic locking | Every edit/delete form carries hidden field `version`. |
| Language (US6) | Every page, including login and error pages, is rendered in the request's language (R19) with `<html lang="de|en">` and shows the language switch (one button per language, current one marked). If a form on the page has unsaved changes, pressing a language button first asks for confirmation (FR-038); cancelling keeps the page and its entries. All texts in this contract (e.g. "Der Eintrag wurde inzwischen geändert") are the German texts; the English page shows the English equivalent. |

## Authentication

| Method | Path | Form fields | Result |
|--------|------|-------------|--------|
| GET | `/` | | `303` to `/login` |
| GET | `/login` | | Login form in the resolved language (cookie → `Accept-Language` → German) with language switch; with `?chosen=1` the form additionally carries the hidden field `chosenLanguage` = current language |
| POST | `/login` | `username`, `password`, optional `chosenLanguage` (`de`\|`en`) — saved only if the account has no saved language (FR-042); cookie and browser preference are never saved | Success → `/schools` (redirects to the school) for planners, `/home` for teachers; failure → `401` form with generic message (no hint which field was wrong) |
| GET | `/home` | | Teacher start page: "Für Lehrkräfte ist noch keine Ansicht verfügbar", links to password change and logout; no master data |
| POST | `/logout` | | Session invalidated → `/login` (language cookie kept) |
| POST | `/language` | `language` (`de`\|`en`), `returnTo`; `_csrf` when logged in | Sets cookie `TTS_LANG`; when logged in also saves the account's language and updates the session (FR-038, FR-039). `303` to `returnTo` if it is a local path, else to `/`. Unknown `language` → `303` without change. Without session: cookie only, `303` to `/login?chosen=1`, no CSRF token (nothing to bind it to; only the login page presentation changes) |

## School (FR-001)

| Method | Path | Form fields |
|--------|------|-------------|
| GET | `/schools` | `303` to `/schools/{schoolId}` of the installation's school (no list in this variant) |
| GET | `/schools/{schoolId}` | school dashboard (links to all sections + readiness summary) |
| POST | `/schools/{schoolId}` | `name`, `version` — rename |

Creating and deleting schools is not offered (FR-001).

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
| GET | `/schools/{schoolId}/time-grid/slots/{slotId}` | edit form incl. day exclusions |
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

## Teachers (FR-016 – FR-018, FR-021)

| Method | Path | Form fields |
|--------|------|-------------|
| GET | `/schools/{schoolId}/teachers` | list (name, short code, weekly load) + create form |
| POST | `/schools/{schoolId}/teachers` | `name`, `shortCode`, `weeklyLoad` |
| GET | `/schools/{schoolId}/teachers/{teacherId}` | edit page: general data, qualifications, unavailability grid |
| POST | `/schools/{schoolId}/teachers/{teacherId}` | `name`, `shortCode`, `weeklyLoad`, `version` |
| POST | `/schools/{schoolId}/teachers/{teacherId}/qualifications` | `subjectId`*, `version` |
| POST | `/schools/{schoolId}/teachers/{teacherId}/unavailability` | `slot` (repeatable, format `{dayOfWeek}:{slotId}`), `version` |
| POST | `/schools/{schoolId}/teachers/{teacherId}/delete` | `version` |

## User accounts (FR-030 – FR-035)

| Method | Path | Form fields |
|--------|------|-------------|
| GET | `/accounts` | all accounts: username, role, active |
| POST | `/accounts` | `username`, `role`, `initialPassword` — the account is assigned to the school automatically (FR-036) |
| GET | `/accounts/{accountId}` | edit form |
| POST | `/accounts/{accountId}` | `role`, `version` — FR-033 violation → `422` |
| POST | `/accounts/{accountId}/deactivate` | `version` — FR-033 violation → `422` |
| POST | `/accounts/{accountId}/activate` | `version` |
| POST | `/accounts/{accountId}/password` | `newPassword` (≥ 10 chars) |
| GET | `/account/password` | own password change form (any logged-in user) |
| POST | `/account/password` | `currentPassword`, `newPassword` |

## Readiness (FR-024, FR-025)

| Method | Path | Result |
|--------|------|--------|
| GET | `/schools/{schoolId}/readiness` | List of readiness issues grouped by code (see data-model.md), each linking to the affected entry; or "Keine blockierenden Probleme gefunden" |

## Response content rules (constitution III)

- Pages show only data of the school; lists show name/identifier columns only.
- Password hashes, session ids and CSRF tokens never appear in rendered output except the token in its hidden field.
