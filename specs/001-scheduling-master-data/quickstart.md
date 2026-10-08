# Quickstart: Scheduling Master Data Management

**Feature**: [spec.md](./spec.md) | **Routes**: [contracts/web-routes.md](./contracts/web-routes.md) | **Data model**: [data-model.md](./data-model.md)

Validation guide proving the feature works end-to-end. The constitution requires UI changes to be verified against a running instance — run the scenarios below before reporting the feature as complete.

## Prerequisites

- JDK 21
- Docker (for PostgreSQL and Testcontainers)
- Commands below use the Gradle wrapper (`./gradlew`, on Windows `.\gradlew.bat`).

## Configuration (never committed)

| Variable | Purpose |
|----------|---------|
| `TTS_DB_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/timetable` |
| `TTS_DB_USER` / `TTS_DB_PASSWORD` | Least-privilege application DB user |
| `TTS_INITIAL_SCHOOL_NAME` | Name of the first school, created at first start (e.g. `Gymnasium Nord`) |
| `TTS_INITIAL_PLANNER_USER` / `TTS_INITIAL_PLANNER_PASSWORD` | First planner account, assigned to the first school; only used when the database is empty |
| `TTS_SESSION_SIGN_KEY` | Hex key for signing session cookies |
| `TTS_DEV_MODE` | `true` locally (allows non-`Secure` cookies over http) |

Put them in a local, git-ignored `.env` file or your shell profile.

## Run

```sh
docker compose up -d db          # starts PostgreSQL (compose file in repo root)
./gradlew test                   # unit, route and Testcontainers integration tests
./gradlew :bootstrap:run         # starts the app on http://localhost:8080, applies Flyway migrations
```

CI check: push the branch and open a pull request — workflows **Build** (tests + dependency submission) and **CodeQL** must succeed and report no new security alerts.

Expected: all tests green; start-up log shows migrations applied and "first school and initial planner created" on first start (no user name or password in the log).

## Scenarios

Log in at `http://localhost:8080/login` with the initial planner.

| # | Story | Steps | Expected |
|---|-------|-------|----------|
| 0 | US5 | First login with the initial planner | Exactly "Gymnasium Nord" listed, still empty |
| 1 | US1 | Open "Gymnasium Nord"; add feature "Chemieraum"; add rooms A-101, B-104 (Chemieraum), Turnhalle | Rooms listed with features |
| 2 | US1 | Add room "b-104" again | Rejected: identifier already in use |
| 3 | US1 | Teaching days Mo–Fr; slots 08:00–08:45, 08:50–09:35, break 09:35–09:55, … up to 8 teaching slots; exclude slots 7–8 on Friday | Grid in chronological order, break visually distinct, Friday shorter |
| 4 | US1 | Add slot 08:30–09:15 | Rejected: overlap |
| 5 | US2 | Subjects Mathematik, Deutsch, Chemie (requires Chemieraum) | Listed, Chemie shows requirement |
| 6 | US2 | Level "6": Mathematik 4, Deutsch 4, Chemie 2 | Weekly total 10 |
| 7 | US2 | Create classes in level 6 twice, accepting the proposal | 6a, 6b; third proposal is "c" |
| 8 | US2 | Open class 6b | Shows the 10 periods of level 6 (read only) |
| 9 | US2 | Raise level 6 Mathematik to 40 periods | Saved with grid-exceeded warning |
| 10 | US3 | Create school "Realschule Süd"; teacher "Anna Schmidt", SCHA, 24 periods, both schools, qualified Mathematik + Chemie | Saved; marked incomplete (commute time missing) |
| 11 | US3 | Second "Anna Schmidt" with code SCHA | Rejected: short code in use; with SCHM accepted |
| 12 | US3 | Commute time 30 min; mark Friday slots 6 as unavailable | Stored and shown as blocked |
| 13 | US4 | Open readiness of "Gymnasium Nord" after removing the Chemieraum feature from B-104 | Reports Chemie without suitable room and Mathematik capacity shortfall |
| 14 | Edge | Delete subject Mathematik | Blocked; business message lists level 6 and Anna Schmidt by name |
| 14a | Edge | Delete time slot "8. Stunde" | Saved; confirmation states removed unavailabilities; teacher whose load now exceeds the grid appears in readiness |
| 15 | Edge | Open the same room in two tabs, save both | Second save shows conflict page |
| 16 | US5 | Create planner `mueller` assigned only to "Realschule Süd" and teacher account `lehrer1` (school "Realschule Süd"); log in as `mueller` | Only "Realschule Süd" listed; link to "Gymnasium Nord" answers "not found"; Anna Schmidt visible (teaches at both) |
| 16a | US5 | Log in as `lehrer1`, open `/teachers`; log out, open `/teachers` | 403 without data; redirect to login |
| 16c | US5 | Log in with wrong password | Generic error, no hint which field was wrong |
| 16d | US5 | As `mueller` (only Realschule Süd) open `/accounts` | Initial planner (assigned to both schools) not listed |
| 16e | US3 | As `mueller` remove Anna Schmidt; then try to delete her | Removed from Realschule Süd only, still present at Gymnasium Nord; full delete answers 403 |
| 16f | US5 | (run after 16e) As initial planner remove yourself from "Realschule Süd"; then as `mueller` try to deactivate your own account | Unassignment works (mueller remains); self-deactivation rejected because "Realschule Süd" would have no active planner (FR-033) |
| 17 | Security | `grep -i "schmidt" ` over the application log after all scenarios | No match |
