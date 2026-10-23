# timetable-scheduler

Web application for school planners that captures the master data a timetable scheduler needs — the school,
rooms and their features, the weekly time grid, subjects, class levels with their curriculum, classes and teachers —
checks whether the data is ready for scheduling and generates several equally optimal timetables to choose from. The
purpose of the application is described in [docs/APP_CONTEXT.md](docs/APP_CONTEXT.md); the features in
[specs/001-scheduling-master-data](specs/001-scheduling-master-data/spec.md) and
[specs/002-timetable-generation](specs/002-timetable-generation/spec.md).

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
./gradlew :adapters:solver:performanceTest    # optional, ~10 min: SC-001-sized school (not part of build)
./gradlew :bootstrap:testSchoolTest           # optional, ~10 min: test school end to end (not part of build)
```

Log in at `http://localhost:8080/login` with the initial planner. Planners manage further accounts under *Konten* (*Accounts*).
The end-to-end validation scenarios are listed in the quickstarts of
[feature 001](specs/001-scheduling-master-data/quickstart.md) and [feature 002](specs/002-timetable-generation/quickstart.md).

## Code layout check

The layout of all Kotlin code (production, tests, test fixtures, Gradle scripts) is checked against the formatting
standard of the constitution (principle I): **ktlint** with all non-experimental standard rules in its default code
style (`ktlint_official`, line length 140, configured in [.editorconfig](.editorconfig)) plus two project rules from
`tooling/layout-rules`:

- `layout:control-flow-body` — the body of `if`/`else`/`for`/`while`/`do` is a block in braces starting on its own
  line (a one-line value `if … else …`, `when` branches and Elvis exits such as `?: return` stay allowed);
- `layout:blank-line-between-steps` — a function or lambda body has no run of more than 5 statements without a blank
  line between steps (a body of assignments only, such as a column mapping, is one step).

The browser script `language-switch.js` is checked for the same two rules by the test `BrowserScriptLayoutTest`.
`./gradlew build` runs the check, so a violation fails the build locally and in CI.

```sh
./gradlew layoutCheck                          # report violations: <path>:<line>:<column>: <message> (<rule id>)
./gradlew layoutFormat                         # apply all auto-fixes, then report what is left
./gradlew layoutFormat -PlayoutPath=domain     # limit either task to one directory
```

Findings without auto-fix are fixed by hand without changing behaviour or displayed texts: for
`standard:max-line-length` wrap the arguments or name a part of the expression (`val teacher = …`) instead of breaking
a string template; for `layout:blank-line-between-steps` put blank lines where a real step ends (load, check, compute,
store, answer). A rule that cannot be satisfied may be switched off only for one statement, one file or a defined group
of files, always with the reason in a comment, e.g.

```kotlin
@Suppress("ktlint:standard:max-line-length") // the URL must stay unbroken to remain clickable
```

For a group of files use a section in `.editorconfig` (`ktlint_standard_<rule> = disabled`) with a comment giving
the reason.

## Scheduling

- **Home room** — a class can get a home room; its lessons without required room features then take place there.
- **Constraints** (*Bedingungen*) — planners add hard (must) and soft (should, weight low / medium / high) rules from a
  fixed catalogue: maximum and minimum periods per day, no free periods, teacher limits and gaps, subject limits and
  double periods, excluded time slots for classes, teachers or subjects. Two soft defaults exist for every school.
  Teachers can be fixed for a subject of a class; all other courses get their teacher from the solver.
- **Generations** (*Stundenplanläufe*) — a generation runs in the background with a time limit (1–60 minutes) and keeps
  up to 10 valid timetables that are equally optimal: none is better in every objective (classes filled, teacher
  loads used, soft constraints met; up to three soft constraints can be compared as separate objectives). Every
  timetable is checked against all hard constraints before it is kept. Lessons that cannot be placed stay "not placed".
- **Comparing and choosing** — alternatives can be sorted by every objective, shown in a chart of two objectives and
  compared side by side per class, teacher or room. The chosen alternative becomes the school's timetable
  (*Gewählter Stundenplan*); a later generation can start from it and keep as many of its lessons as possible.
- **Outdated results** — when master data or constraints change, generations are marked outdated, and lessons that now
  violate a hard constraint are highlighted, also in the chosen timetable.

The solver is [Timefold Solver](https://timefold.ai) (Apache 2.0), used only in the `adapters:solver` module.

## Test school (test data)

A fictitious G9 Gymnasium in the style of Baden-Württemberg (grades 5–10, three classes each, 32 teachers, 20
constraints) checks timetable generation on a school of realistic size. **It is test data, not a real school:** the
school is called "TESTDATEN – Testgymnasium Musterstadt", teachers "Testlehrkraft NN", rooms "TEST-…", and the weekly
periods are an assumption. Description and accepted simplifications:
[test-set-bw-gymnasium.md](specs/002-timetable-generation/test-set-bw-gymnasium.md).

- `./gradlew :bootstrap:testSchoolTest` seeds it into a PostgreSQL container, runs one generation with the default
  time limit and checks the results (Docker required).
- A variant with a teacher shortage (28 teachers, 574 periods for 588 lessons) checks that lessons which cannot be
  served are reported as "not placed" without breaking any rule; `testSchoolTest` runs both scenarios (~20 minutes).
- `./gradlew :bootstrap:seedTestSchool` (or `-PtestSchool=understaffed` for the variant) seeds it into the database of
  the `TTS_*` variables to look at it with `:bootstrap:run`. Use a **separate, empty database**; the seeder refuses a school that already has master data.

The test set lives in `bootstrap/src/test/.../testschool` and is never part of the application. Later features extend
it there (and in the description) when they need further data.

## Interface language

The interface is available in German and English. Every page has a language switch in the header (also on the
login page):

- Before a user chooses, the browser's preferred language is used (German if it is neither German nor English).
- A choice made while logged in is saved with the account and applies on every later login, on any device.
- A choice on the login page is remembered in the browser (cookie `TTS_LANG`, only for showing the login page) and
  saved with the account at the next login if the account has no language yet.
- If a form has unsaved input, a small script asks before switching (it is the only browser script; without
  JavaScript the switch works without that warning).

Data entered by users (names, identifiers, short codes) is never translated. Texts live in
`adapters/web/.../i18n/GermanTexts.kt` and `EnglishTexts.kt`; a missing text in either language fails the build.

## Project structure

Gradle modules along the clean-architecture circles; dependencies point inwards only.

| Module | Content |
|--------|---------|
| `domain` | Entities, validation rules, readiness check, scheduling core (hard-constraint checker, objectives, Pareto archive) — no framework dependencies |
| `application` | Use cases, ports, access policy; in-memory fakes in `testFixtures` |
| `adapters:persistence` | PostgreSQL via Exposed, Flyway migrations (`db/migration`), bcrypt |
| `adapters:solver` | Timefold model and constraint streams; weighted solver passes report candidates to the application |
| `adapters:web` | Ktor, server-rendered HTML (`kotlinx.html`, the trade-off chart as inline SVG), sessions, CSRF |
| `bootstrap` | Composition root, configuration, logging |
| `tooling:layout-rules` | The project rules of the layout check (ktlint rule set, build tooling only) |

## CI/CD and security testing

GitHub Actions in [.github/workflows](.github/workflows):

- **Build** (`build.yml`) — `./gradlew build` on every push and pull request, plus Gradle dependency submission for
  GitHub Advanced Security (Dependabot alerts).
- **CodeQL** (`codeql.yml`) — static security analysis (`java-kotlin`, default security suite) on pull requests,
  pushes to `main` and weekly; results are uploaded as SARIF, so further scanners can be added the same way.
- **Dependabot** (`dependabot.yml`) — weekly update checks for Gradle and GitHub Actions.

## Confidentiality

Teacher and class data is confidential (constitution, principle III). Logs contain only identifiers, event types,
statuses, counts and durations — also for generations, never lessons or names;
technical errors show a generic message and a correlation id. Business messages (e.g. why a deletion is blocked) may
name entries, but only to planners, who are allowed to see them.
