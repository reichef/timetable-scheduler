# Implementation Plan: Scheduling Master Data Management

**Branch**: `one-school` (single-school variant of `001-scheduling-master-data`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md) | **Constitution**: v1.1.0

**Input**: Feature specification from `specs/001-scheduling-master-data/spec.md`

## Summary

Planners log in to a central web server that manages exactly one school (R17) and manage the user accounts. They capture the input of the timetable scheduler: the school, rooms with features, the weekly time grid, subjects with required room features, class levels with their curriculum, classes with priority weights, and teachers with qualifications, weekly load and unavailability. A readiness overview reports inconsistencies before scheduling.

Technical approach: a Kotlin web application (Ktor, server-rendered HTML via `kotlinx.html`) structured as Gradle modules along the clean-architecture circles. Validation rules, constraint semantics and the readiness check live in a framework-free `domain` module. PostgreSQL is accessed through Exposed and migrated with Flyway. A built-in login with account management protects all data; single sign-on can be added later (R6). All data stays owned by the school and access goes through one policy, so a multi-school extension is additive (R17, FR-036). Each class resolves its curriculum through a domain resolver so per-class deviations can be added later (R9). Development is test-driven (R11); a GitHub Actions pipeline builds, tests and runs CodeQL and dependency submission for GitHub Advanced Security (R16).

## Technical Context

**Language/Version**: Kotlin 2.x (current stable), JVM toolchain 21

**Primary Dependencies**: Ktor 3 (server, auth, sessions, status pages, call id, `kotlinx.html`), Exposed (DSL), HikariCP, Flyway, PostgreSQL JDBC driver, `at.favre.lib:bcrypt`, SLF4J + Logback — see [research.md](./research.md) R1–R7, R10

**Storage**: PostgreSQL, least-privilege application user, schema via Flyway (R3, R5)

**Testing**: Test-first with `kotlin.test` / JUnit 5, Ktor `testApplication`, Testcontainers PostgreSQL; manual UI verification via [quickstart.md](./quickstart.md) (R11)

**CI/CD & Security Testing**: GitHub Actions; CodeQL (`java-kotlin`, default security suite); Gradle dependency submission for GitHub Advanced Security (Dependabot alerts); scanners exchange results via SARIF (R16)

**Target Platform**: JVM 21 on a Linux server; modern desktop browsers

**Project Type**: Web application (server-rendered, single deployable)

**Performance Goals**: Every page and form submission answers in < 1 s (R15)

**Constraints**: Kotlin/Gradle only for production code; no confidential data in logs or technical error output; secrets only via environment; every schema change ships with a Flyway migration

**Scale/Scope**: 1 school, ≤ 200 teachers, ≤ 100 classes, ≤ 10 concurrent planners per installation; ~12 entity types, ~45 routes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.* Checked against constitution v1.1.0.

| Principle / Constraint | How the plan complies | Pre | Post |
|------------------------|-----------------------|-----|------|
| I. Kotlin Clean Code | All code in Kotlin. Domain names from the spec (`teachingSlotsPerWeek`, `requiredFeatureIds`, `priorityWeight`, `ClassCurriculumResolver`). Each validation rule and readiness rule is its own function; compact constraint checks (slot overlap, load limit, capacity shortfall, access rules) carry explanatory comments. | ✅ | ✅ |
| II. Tools/Libraries where appropriate | Established libraries for HTTP, SQL, migrations, pooling, password hashing, logging, CI security scanning; no library for trivial logic (validation, readiness and access rules hand-written). | ✅ | ✅ |
| III. Confidentiality (NON-NEGOTIABLE) | Login required for every data route; role and active flag checked in every use case via `SchoolAccessPolicy` (FR-028); a foreign `schoolId` answers 404. bcrypt password hashes in a separate credential table. **Error messages by kind (R10)**: business errors (validation, deletion blocked) may name entries the planner is authorized to see (all entries of the school) and are never logged with that content; technical/unexpected errors show only a generic text + correlation id. Logs contain only UUIDs and event types. Least-privilege DB user. Pages show only the minimum fields needed. CSRF tokens, `HttpOnly`/`SameSite` session cookies. | ✅ | ✅ |
| IV. Pareto-Optimal Scheduling Integrity | Hard constraints recorded explicitly (FR-022, data-model). Class `priorityWeight` and teacher `weeklyLoad` (as target) are recorded as Pareto objective inputs (FR-015, FR-018). No scheduling in this feature. | ✅ | ✅ |
| V. Test-Driven Development (NON-NEGOTIABLE) | Tests are written before the code they cover and must fail first; every non-trivial function gets meaningful tests (domain rules, use cases incl. access rules, repositories, routes). No coverage target (R11). | ✅ | ✅ |
| Tech: Kotlin + Gradle, CI/CD on GitHub | Server-rendered HTML in Kotlin, no npm/TypeScript toolchain (R2); Gradle wrapper (R13); GitHub Actions workflow builds and tests every push/PR (R16). | ✅ | ✅ |
| Tech: Clean architecture | Modules `domain` (entities) ← `application` (use cases) ← `adapters:persistence` / `adapters:web` (interface adapters) ← `bootstrap` (frameworks & drivers, composition root). The compiler enforces that dependencies only point inward (R12). | ✅ | ✅ |
| Tech: Transactional DB with access control | PostgreSQL; one transaction per use case; least-privilege application user. | ✅ | ✅ |
| Tech: Secrets not in repo | All credentials and keys via `TTS_*` environment variables; `.env` git-ignored; CI uses GitHub secrets only where needed (none for tests — Testcontainers generates its own). | ✅ | ✅ |
| Tech: Security testing | CodeQL workflow with default security suite; dependency submission for GitHub Advanced Security SCA; build invoked through plain Gradle tasks and results exchanged as SARIF so further scanners can be added as separate jobs (R16). | ✅ | ✅ |
| Gate: Review criteria | Naming, function size, comments, no confidential data in logs and technical error output — checked in review; log-leak test (R10). | ✅ | ✅ |
| Gate: Tests for constraint logic | Domain tests for every hard-constraint validation (FR-006, FR-009, FR-018, FR-021) and every readiness rule (FR-024). Pareto comparison not touched in this feature. | ✅ | ✅ |
| Gate: Migrations with entity changes | One Flyway migration per story (`V1__schools_and_accounts.sql`, `V2__rooms_time_grid.sql`, `V3__curriculum.sql`, `V4__teachers.sql`), each created together with its entities. | ✅ | ✅ |
| Gate: UI verified on running instance | All quickstart scenarios run against `:bootstrap:run`. | ✅ | ✅ |
| Gate: Feature branch | Work happens on `one-school` (branched from `001-scheduling-master-data`). | ✅ | ✅ |

**Result**: No violations. Complexity Tracking not required.

**Clarifications applied (spec session 2026-10-08)**: central multi-user server; built-in login with SSO prepared (R6); weekly load = hard upper limit + optimization target; fixed lessons out of scope; grid reductions saved with a readiness issue for excessive loads; slot deletion removes its unavailabilities after confirmation. **Scope change**: exactly one school per installation; multi-school rules (FR-019, FR-020, FR-028a–c, FR-031a, FR-032a) deferred, preparation per FR-036 / R17.

## Project Structure

### Documentation (this feature)

```text
specs/001-scheduling-master-data/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R17
├── data-model.md        # Phase 1: entities, rules, access rules, deletion rules, readiness issues
├── quickstart.md        # Phase 1: run and validation guide
├── contracts/
│   └── web-routes.md    # Phase 1: HTTP routes and form contracts
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
settings.gradle.kts                 # includes the modules below
build.gradle.kts                    # common Kotlin/JVM 21 config
gradle/libs.versions.toml           # version catalog
gradlew, gradlew.bat, gradle/wrapper/
docker-compose.yml                  # local PostgreSQL
.env.example                        # variable names only, no values
.github/
├── workflows/
│   ├── build.yml                   # build + test on push/PR; Gradle dependency submission
│   └── codeql.yml                  # CodeQL java-kotlin, default security suite
└── dependabot.yml                  # Gradle + GitHub Actions update checks

domain/                             # entities circle: pure Kotlin, no dependencies
└── src/
    ├── main/kotlin/timetablescheduler/domain/
    │   ├── common/                 # typed identifiers
    │   ├── account/                # UserAccount, Role, account invariants
    │   ├── school/                 # School, TimeGrid, TimeSlot, TeachingDay
    │   ├── room/                   # Room, RoomFeature
    │   ├── curriculum/             # Subject, ClassLevel, CurriculumEntry, SchoolClass,
    │   │                           # ClassCurriculumResolver, SuffixProposal
    │   ├── teacher/                # Teacher, Unavailability
    │   ├── readiness/              # ReadinessCheck, ReadinessIssue
    │   └── validation/             # ValidationResult, typed ValidationError
    └── test/kotlin/...

application/                        # use-case circle (depends on domain)
└── src/
    ├── main/kotlin/timetablescheduler/application/
    │   ├── port/                   # repositories, CredentialAuthenticator, TransactionRunner
    │   ├── school/ room/ curriculum/ teacher/ readiness/ account/   # one use case class per action
    │   ├── access/                 # SchoolAccessPolicy, DependantVisibilityFilter
    │   └── error/                  # NotFound, AccessDenied, VersionConflict, DeletionBlocked, Invalid
    ├── testFixtures/kotlin/...     # in-memory fake repositories shared with adapter tests
    └── test/kotlin/...             # use case tests

adapters/persistence/               # interface adapter: Exposed + Flyway
└── src/
    ├── main/kotlin/timetablescheduler/adapters/persistence/
    ├── main/resources/db/migration/   # V1__schools_and_accounts.sql … V4__teachers.sql (one per story)
    └── test/kotlin/...             # Testcontainers integration tests

adapters/web/                       # interface adapter: Ktor routes + kotlinx.html views
└── src/
    ├── main/kotlin/timetablescheduler/adapters/web/
    │   ├── auth/ account/ school/ room/ timegrid/ subject/ classlevel/ teacher/ readiness/   # all data routes under /schools/{schoolId}
    │   └── layout/                 # page layout, messages (German texts), CSRF
    ├── main/resources/static/      # app.css
    └── test/kotlin/...             # testApplication route tests

bootstrap/                          # frameworks & drivers: config, wiring, main()
└── src/main/kotlin/timetablescheduler/bootstrap/Main.kt
```

The existing empty `src/timetablescheduler/timetablescheduler.kt`, `src/test/kotlin/MainTest.kt` and the root `application` plugin configuration are removed.

**Structure Decision**: Gradle multi-module web application with one module per clean-architecture circle (R12). `bootstrap` is the only module that knows all others and produces the runnable application.

## Design Notes

- **Transactions**: Each use case runs inside one `TransactionRunner.inTransaction { }` call (port implemented by the persistence adapter), so a use case's reads and writes are atomic.
- **Validation flow**: Domain factories/`update` functions return `ValidationResult<T>` (success or list of typed errors). Uniqueness rules need repository lookups and are checked in the use case, then mapped to the same typed errors. The web layer renders errors as German business messages.
- **Hard vs. warning**: Rules in FR-001–FR-021 block saving; readiness issues (FR-024) never block (FR-025). The class-level grid warning (acceptance 2.5) is the readiness rule `LEVEL_EXCEEDS_GRID`, shown on the level page after saving. Reducing the time grid never blocks; excessive teacher loads become `TEACHER_LOAD_EXCEEDS_GRID`.
- **Per-class curriculum preparation (FR-014)**: Only `ClassCurriculumResolver` reads `ClassLevel.curriculum` for a class; readiness check and class page use the resolver.
- **Single school, prepared for more (R17)**: All data keeps its `schoolId`; routes keep `/schools/{schoolId}/…`; every use case asks `SchoolAccessPolicy` (active planner assigned to the school). The multi-school extension changes the policy, adds school management and the `teacher_school` table — existing data stays.
- **Installation**: On first start with an empty database, `bootstrap` creates the school (`TTS_INITIAL_SCHOOL_NAME`) and the initial planner assigned to it (FR-031). New accounts are assigned to the school automatically.
- **Rework of already built code** (US1 was implemented before the scope change): remove `CreateSchool`, `DeleteSchool`, the school list page, the school-deletion dependant sources and the account queries used only by them (`findAccountsWithOnlySchool`, `removeSchoolAssignments`) together with their tests; `/schools` redirects to the school.

## Complexity Tracking

Not required — the Constitution Check has no violations.
