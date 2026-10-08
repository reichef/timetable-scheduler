# timetable-scheduler

Web application for school planners that captures the master data a timetable scheduler needs — the school,
rooms and their features, the weekly time grid, subjects, class levels with their curriculum, classes and teachers —
and checks whether the data is ready for scheduling. The purpose of the application is described in
[docs/APP_CONTEXT.md](docs/APP_CONTEXT.md); the current feature in
[specs/001-scheduling-master-data](specs/001-scheduling-master-data/spec.md).

This version manages exactly **one school** per installation; it is built so that several schools can be added later
without re-entering data.

## Prerequisites

- JDK 21
- Docker (PostgreSQL for running the app, Testcontainers for the integration tests)
- Use the Gradle wrapper: `./gradlew` (Windows: `.\gradlew.bat`)

## Configuration

All settings come from environment variables; secrets are never committed. Copy [.env.example](.env.example) to a
local `.env` (git-ignored) and fill it in. **Quote values containing spaces**, e.g. `TTS_INITIAL_SCHOOL_NAME="Gymnasium Nord"`.

| Variable | Purpose |
|----------|---------|
| `TTS_DB_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/timetable` |
| `TTS_DB_USER`, `TTS_DB_PASSWORD` | Least-privilege application user (created by `docker/postgres-init`) |
| `POSTGRES_PASSWORD` | Password of the database owner, used by `docker-compose.yml` only |
| `TTS_INITIAL_SCHOOL_NAME` | Name of the school, created on the first start with an empty database |
| `TTS_INITIAL_PLANNER_USER`, `TTS_INITIAL_PLANNER_PASSWORD` | First planner account (password ≥ 10 characters); only used on the first start |
| `TTS_SESSION_SIGN_KEY` | Hex key for signing session cookies (e.g. `openssl rand -hex 32`) |
| `TTS_DEV_MODE` | `true` locally to allow non-`Secure` cookies over plain http |

## Run and test

```sh
docker compose up -d db                       # PostgreSQL with the application user
set -a; . ./.env; set +a                      # load the configuration into the shell
./gradlew build                               # compile, unit, route and Testcontainers tests
./gradlew :bootstrap:run                      # http://localhost:8080 – applies Flyway migrations on start
```

Log in at `http://localhost:8080/login` with the initial planner. Planners manage further accounts under *Konten*.
The end-to-end validation scenarios are listed in the
[quickstart](specs/001-scheduling-master-data/quickstart.md).

## Project structure

Gradle modules along the clean-architecture circles; dependencies point inwards only.

| Module | Content |
|--------|---------|
| `domain` | Entities, validation rules, readiness check — no framework dependencies |
| `application` | Use cases, ports, access policy; in-memory fakes in `testFixtures` |
| `adapters:persistence` | PostgreSQL via Exposed, Flyway migrations (`db/migration`), bcrypt |
| `adapters:web` | Ktor, server-rendered HTML (`kotlinx.html`), sessions, CSRF |
| `bootstrap` | Composition root, configuration, logging |

## CI/CD and security testing

GitHub Actions in [.github/workflows](.github/workflows):

- **Build** (`build.yml`) — `./gradlew build` on every push and pull request, plus Gradle dependency submission for
  GitHub Advanced Security (Dependabot alerts).
- **CodeQL** (`codeql.yml`) — static security analysis (`java-kotlin`, default security suite) on pull requests,
  pushes to `main` and weekly; results are uploaded as SARIF, so further scanners can be added the same way.
- **Dependabot** (`dependabot.yml`) — weekly update checks for Gradle and GitHub Actions.

## Confidentiality

Teacher and class data is confidential (constitution, principle III). Logs contain only identifiers and event types;
technical errors show a generic message and a correlation id. Business messages (e.g. why a deletion is blocked) may
name entries, but only to planners, who are allowed to see them.
