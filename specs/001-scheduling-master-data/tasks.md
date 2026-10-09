---

description: "Task list for Scheduling Master Data Management"
---

# Tasks: Scheduling Master Data Management

**Input**: Design documents from `specs/001-scheduling-master-data/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/web-routes.md](./contracts/web-routes.md), [quickstart.md](./quickstart.md)

**Tests**: Mandatory. Constitution v1.1.0 Principle V (Test-Driven Development, NON-NEGOTIABLE) requires meaningful tests for every non-trivial function, written before the implementation; the quality gate additionally requires tests showing hard constraints are never violated. Within each phase, write the test tasks first and make sure they fail before implementing (R11).

**Organization**: Tasks are grouped by user story. Story order follows priority: US1 (P1, MVP), US5 (P1), US2 (P2), US3 (P3), US4 (P4), US6 (P5). Login, sessions, installation (school + initial planner) and the school access policy are foundational because every story needs them; account *management* is US5. **Single-school variant (branch `one-school`)**: Phases 1–3 were built before the scope change and are kept as done; Phase 3a reworks US1, later phases follow the single-school spec (see Notes).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story from spec.md (US1–US5)

## Path Conventions

Gradle multi-module build per plan.md. Kotlin base package `timetablescheduler`. Abbreviations used below:

- `DOM` = `domain/src/main/kotlin/timetablescheduler/domain`
- `DOM_T` = `domain/src/test/kotlin/timetablescheduler/domain`
- `APP` = `application/src/main/kotlin/timetablescheduler/application`
- `APP_T` = `application/src/test/kotlin/timetablescheduler/application`
- `APP_F` = `application/src/testFixtures/kotlin/timetablescheduler/application`
- `PER` = `adapters/persistence/src/main/kotlin/timetablescheduler/adapters/persistence`
- `PER_R` = `adapters/persistence/src/main/resources/db/migration`
- `PER_T` = `adapters/persistence/src/test/kotlin/timetablescheduler/adapters/persistence`
- `WEB` = `adapters/web/src/main/kotlin/timetablescheduler/adapters/web`
- `WEB_T` = `adapters/web/src/test/kotlin/timetablescheduler/adapters/web`
- `BOOT` = `bootstrap/src/main/kotlin/timetablescheduler/bootstrap`

**Conventions for every task** (constitution I, III):

- Speaking domain names, small single-purpose functions, explanatory comment on every compact constraint or access check.
- **Business errors** (`Invalid`, `DeletionBlocked`, `VersionConflict`, `AccessDenied`) may name entries the planner is authorized to see; they are logged only as error type + UUIDs. **Technical/unexpected errors** show a generic German text + correlation id and are logged only as exception class + correlation id (R10).
- Never log names, form values, passwords or exception messages. User-facing texts in German via the messages objects, code in English.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Reproducible multi-module Gradle build, local database, CI/CD with security testing

- [X] T001 Add the Gradle wrapper pinned to the current stable Gradle release (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`); `gradle` is not on the PATH, so generate it with a temporary Gradle distribution or IntelliJ's bundled Gradle (R13)
- [X] T002 Create version catalog `gradle/libs.versions.toml` with current stable versions of: Kotlin JVM plugin 2.x, Ktor 3 (`server-core`, `server-netty`, `server-auth`, `server-sessions`, `server-status-pages`, `server-html-builder`, `server-call-id`, `server-test-host`), Exposed (`core`, `jdbc`, `java-time`), HikariCP, Flyway (`flyway-core`, `flyway-database-postgresql`), PostgreSQL JDBC driver, `at.favre.lib:bcrypt`, SLF4J API, Logback Classic, Testcontainers (`postgresql`, `junit-jupiter`), `kotlin-test-junit5`
- [X] T003 Rewrite `settings.gradle.kts` to include `domain`, `application`, `adapters:persistence`, `adapters:web`, `bootstrap`, and rewrite root `build.gradle.kts` to only hold shared config (Kotlin JVM plugin applied to subprojects, `jvmToolchain(21)`, `useJUnitPlatform()`, `kotlin-test` for all test source sets); remove the root `application` plugin block
- [X] T004 [P] Create `domain/build.gradle.kts` with no dependencies besides Kotlin stdlib and test libraries
- [X] T005 [P] Create `application/build.gradle.kts` depending only on `:domain`, with the `java-test-fixtures` plugin (fixtures depend on `:domain`)
- [X] T006 [P] Create `adapters/persistence/build.gradle.kts` depending on `:application`, `:domain`, Exposed, HikariCP, Flyway, PostgreSQL driver, bcrypt, SLF4J; test deps Testcontainers PostgreSQL + JUnit Jupiter and `testFixtures(project(":application"))`
- [X] T007 [P] Create `adapters/web/build.gradle.kts` depending on `:application`, `:domain`, Ktor server modules from T002, SLF4J; test deps `ktor-server-test-host`, `testFixtures(project(":application"))`
- [X] T008 [P] Create `bootstrap/build.gradle.kts` with the `application` plugin (`mainClass = "timetablescheduler.bootstrap.MainKt"`) depending on all four other modules plus Logback Classic and Ktor Netty
- [X] T009 Delete the empty `src/timetablescheduler/timetablescheduler.kt` and `src/test/kotlin/MainTest.kt`; verify `./gradlew build` succeeds on the empty modules
- [X] T010 [P] Create `docker-compose.yml` with a PostgreSQL service `db` (port 5432, database `timetable`, owner user and a separate least-privilege application user created by `docker/postgres-init/01-app-user.sql` with only `CONNECT`, `USAGE`, DML and `CREATE` on schema `public`; all passwords read from env vars `POSTGRES_PASSWORD` and `TTS_DB_PASSWORD`)
- [X] T011 [P] Create `.env.example` listing (names only, no values) `TTS_DB_URL`, `TTS_DB_USER`, `TTS_DB_PASSWORD`, `TTS_INITIAL_SCHOOL_NAME`, `TTS_INITIAL_PLANNER_USER`, `TTS_INITIAL_PLANNER_PASSWORD`, `TTS_SESSION_SIGN_KEY`, `TTS_DEV_MODE`, `POSTGRES_PASSWORD`; add `.env` to `.gitignore`
- [X] T012 [P] Create `.github/workflows/build.yml` (R16): on `push` and `pull_request`; job `build` with `permissions: contents: read`: checkout, `actions/setup-java` (Temurin 21), `gradle/actions/setup-gradle`, `./gradlew build`; job `dependency-submission` with `permissions: contents: write` running `gradle/actions/dependency-submission` (feeds GitHub Advanced Security / Dependabot alerts); actions pinned to major versions
- [X] T013 [P] Create `.github/workflows/codeql.yml` (R16): on push to `main`, `pull_request` and a weekly `schedule`; `permissions: security-events: write, contents: read, actions: read`; `github/codeql-action/init` with `languages: java-kotlin`, `build-mode: manual`, default security query suite; build step `./gradlew assemble`; `github/codeql-action/analyze` (SARIF upload)
- [X] T014 [P] Create `.github/dependabot.yml` with weekly update checks for ecosystems `gradle` (directory `/`) and `github-actions` (directory `/`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Error/validation model, transactions, database + migrations, installation, login with sessions, school access policy, web skeleton, composition root

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundation (write first, must fail)

- [X] T015 [P] Write `DOM_T/account/UserAccountTest.kt`: username "String (3–60)", "Unique, case-insensitive" (via use case); role `PLANNER` | `TEACHER`; `schoolIds` "≥ 1 at all times, for both roles (FR-031a)"; FR-033 invariant "every school with at least one assigned planner keeps ≥ 1 *active* assigned planner; deactivating, downgrading to `TEACHER` or unassigning that planner is rejected"
- [X] T016 [P] Write `DOM_T/school/SchoolTest.kt`: school name "String (1–120)" trimmed
- [X] T017 [P] Write `APP_T/access/SchoolAccessPolicyTest.kt`: inactive account denied; `TEACHER` denied for all master data; `PLANNER` allowed only for schools in its `schoolIds`; access to an unassigned school yields `NotFound` (not `AccessDenied`) so existence is not revealed (FR-028, FR-028a)
- [X] T018 [P] Write `APP_T/account/LoginUseCaseTest.kt` and `APP_T/account/InstallationUseCaseTest.kt`: correct credentials → `AuthenticatedUser(accountId)`; wrong username and wrong password produce the identical failure (FR-030); deactivated account cannot log in (FR-032); installation with empty database creates the first school (`TTS_INITIAL_SCHOOL_NAME`) and an initial `PLANNER` assigned to it in one transaction, and does nothing if any account exists (FR-031); initial password ≥ 10 characters (FR-034)
- [X] T019 [P] Write `WEB_T/auth/AuthRoutesTest.kt` with `testApplication`: unauthenticated `GET /schools` → 302 `/login`; failed `POST /login` → 401 with the same generic German message for wrong user or password; successful planner login → 303 `/schools`; successful teacher login → 303 `/home` showing no master data; `POST /logout` invalidates the session; `POST` without valid CSRF token → 403; unexpected exception → 500 page with correlation id only
- [X] T020 [P] Write `PER_T/account/UserAccountRepositoryIT.kt` (extends `PostgresTestBase`): save/load account with `account_school` assignments, optimistic locking (update with stale `version` → `VersionConflict`), case-insensitive username uniqueness, password stored only as bcrypt hash in `password_credential`

### Implementation for Foundation

- [X] T021 [P] Create typed identifiers as `@JvmInline value class` wrapping `UUID` (`SchoolId`, `RoomId`, `RoomFeatureId`, `TimeSlotId`, `SubjectId`, `ClassLevelId`, `SchoolClassId`, `TeacherId`, `AccountId`) in `DOM/common/Identifiers.kt`
- [X] T022 [P] Create `ValidationResult<T>` (success value or non-empty list of `ValidationError`) and sealed `ValidationError` hierarchy (e.g. `BlankValue(field)`, `TooLong(field, max)`, `OutOfRange(field, min, max)`, `DuplicateValue(field)`, `Overlap`, `InvalidReference(field)`, `LastSchoolRemoved`, `NoActivePlannerLeft(schoolId)`) in `DOM/validation/ValidationResult.kt` and `DOM/validation/ValidationError.kt`
- [X] T023 [P] Create sealed application errors `NotFound`, `AccessDenied`, `VersionConflict`, `DeletionBlocked(visibleDependants: List<Dependant(type, displayName)>, hiddenDependantCount: Int)`, `Invalid(errors: List<ValidationError>)` and a `UseCaseResult<T>` type in `APP/error/ApplicationError.kt` (business errors per R10)
- [X] T024 [P] Create port `TransactionRunner` with `fun <T> inTransaction(block: () -> T): T` in `APP/port/TransactionRunner.kt`
- [X] T025 [P] Create `School` (`id`, name "String (1–120)", `version`) in `DOM/school/School.kt` (depends on T021, T022)
- [X] T026 Create `UserAccount` (`id`, `username` "String (3–60)", `role`, `active`, `schoolIds` ≥ 1, `version`), `Role` enum and the FR-033 check `wouldLeaveSchoolWithoutActivePlanner(...)` in `DOM/account/UserAccount.kt` (depends on T021, T022)
- [X] T027 [P] Create ports `SchoolRepository` (foundation part: `save`, `findById`, `findByNameIgnoreCase`, `existsAny`), `UserAccountRepository` (incl. `findActivePlannersOfSchool`, `existsAny`), `PasswordCredentialRepository`, `PasswordHasher`, `CredentialAuthenticator` (returns `AuthenticatedUser(accountId)` or failure) in `APP/port/`
- [X] T028 Implement `SchoolAccessPolicy` (`requirePlanner(accountId)`, `requireSchoolAccess(accountId, schoolId)` → `NotFound` for unassigned school) in `APP/access/SchoolAccessPolicy.kt`; re-reads role, active flag and assignments on every call (R6) (depends on T026, T027)
- [X] T029 Implement `LoginUseCase` and `InstallationUseCase` (creates first school + initial planner in one transaction only if no account exists; password ≥ 10 characters, FR-031, FR-034) in `APP/account/LoginUseCase.kt` and `APP/account/InstallationUseCase.kt`
- [X] T030 [P] Create in-memory fakes `InMemoryTransactionRunner`, `InMemorySchoolRepository`, `InMemoryUserAccountRepository`, `InMemoryPasswordCredentialRepository`, `PlainTextTestPasswordHasher` in `APP_F/`
- [X] T031 Implement `DatabaseFactory` (HikariCP from `TTS_DB_URL`/`TTS_DB_USER`/`TTS_DB_PASSWORD`, Flyway `migrate()` on start) and `ExposedTransactionRunner` in `PER/DatabaseFactory.kt` and `PER/ExposedTransactionRunner.kt`
- [X] T032 Create migration `PER_R/V1__schools_and_accounts.sql`: `school` (`id uuid pk`, `name varchar(120)` with unique index on `lower(name)`, `version int`), `user_account` (`id uuid pk`, `username varchar(60)` with unique index on `lower(username)`, `role varchar(16)` check in (`PLANNER`,`TEACHER`), `active boolean`, `version int`), `account_school` (`account_id` fk, `school_id` fk, pk both), `password_credential` (`account_id uuid pk fk`, `password_hash varchar(100)`)
- [X] T033 Implement Exposed tables and `ExposedSchoolRepository` (foundation part), `ExposedUserAccountRepository`, `ExposedPasswordCredentialRepository`, `BcryptPasswordHasher` (cost 12, R7) and `PasswordCredentialAuthenticator` in `PER/school/` and `PER/account/`
- [X] T034 [P] Create `PostgresTestBase` (Testcontainers PostgreSQL, runs Flyway, truncates tables between tests) in `PER_T/PostgresTestBase.kt`
- [X] T035 Create Ktor web module `WEB/WebModule.kt`: install `CallId` (correlation id), `StatusPages` mapping business errors `NotFound`→404, `AccessDenied`→403, `VersionConflict`/`DeletionBlocked`→409, `Invalid`→422 to German business pages, and any other exception → 500 technical page showing only a generic text + correlation id, logging only exception class + correlation id (R10); session cookie `HttpOnly`, `SameSite=Strict`, `Secure` unless `TTS_DEV_MODE=true`, signed with `TTS_SESSION_SIGN_KEY`
- [X] T036 [P] Create CSRF support (per-session token, hidden form field helper, `POST` interceptor rejecting invalid tokens with 403) in `WEB/layout/Csrf.kt`
- [X] T037 [P] Create page layout (navigation: Schulen, Lehrkräfte, Konten, Passwort ändern, Abmelden), form/error rendering helpers (business error list, deletion-blocked list with "und N Einträge anderer Schulen") and German message objects in `WEB/layout/PageLayout.kt`, `WEB/layout/FormComponents.kt`, `WEB/layout/Messages.kt`, and stylesheet `adapters/web/src/main/resources/static/app.css`
- [X] T038 Implement `GET/POST /login` (redirect by role), `POST /logout`, `GET /home` (teacher start page) and an `authenticatedPlanner` route wrapper (session → `AuthenticatedUser`, unauthenticated → 302 `/login`) in `WEB/auth/AuthRoutes.kt` and `WEB/auth/AuthenticatedRoute.kt` per contracts/web-routes.md "Authentication"
- [X] T039 Implement `AppConfig` reading all `TTS_*` variables (fail fast with a message naming the missing variable, never its value) in `BOOT/AppConfig.kt`
- [X] T040 Implement composition root `BOOT/Main.kt`: build `DatabaseFactory`, run migrations, run `InstallationUseCase` (log "first school and initial planner created" without names), wire repositories → use cases → web module, start Netty on port 8080
- [X] T041 [P] Create `bootstrap/src/main/resources/logback.xml` (pattern: timestamp, level, logger, correlation id, message; no MDC dumps)

**Checkpoint**: `./gradlew test` green; app starts; initial planner logs in and sees exactly the first school (quickstart 0); CI workflows green

---

## Phase 3: User Story 1 - Set up schools, rooms and the weekly time grid (Priority: P1) 🎯 MVP

**Goal**: Planner creates further schools (auto-assigned), room features, rooms and the weekly time grid.

**Independent Test**: In the first school add three rooms with different features, define a Monday–Friday grid with 8 slots and two breaks, reopen in a new session and confirm everything is unchanged (quickstart 1–4).

### Tests for User Story 1 (write first, must fail)

- [X] T042 [P] [US1] Write `DOM_T/school/TimeGridTest.kt`: "`endTime > startTime`" (FR-006); "Slots of one school MUST NOT overlap (touching boundaries allowed)"; ordering by `startTime`; `SlotDayExclusion` "`dayOfWeek` must be a teaching day of the school"; `teachingSlotsPerWeek` = "Σ over teaching days of TEACHING slots not excluded that day" (FR-007); removing a teaching day keeps exclusions for it but ignores them in `teachingSlotsPerWeek`
- [X] T043 [P] [US1] Write `DOM_T/room/RoomTest.kt`: room identifier "String (1–40)"; room feature name "String (1–80)"; room features "must belong to the same school"
- [X] T044 [P] [US1] Write `APP_T/school/SchoolUseCasesTest.kt`: school name "Unique in installation, trimmed, case-insensitive"; creator is assigned to the new school (FR-028a); planner not assigned → `NotFound`; stale `version` → `VersionConflict`; deleting a school with rooms/features/slots → `DeletionBlocked` listing dependants; deleting a school that is "the only school of some user account" → `DeletionBlocked` listing those accounts (FR-031a, FR-023)
- [X] T045 [P] [US1] Write `APP_T/room/RoomUseCasesTest.kt` and `APP_T/school/TimeGridUseCasesTest.kt`: room identifier "Unique per school, case-insensitive" (acceptance 1.3 "B-104"/"b-104"); feature name unique per school; deleting a feature used by a room → `DeletionBlocked` naming the room; overlapping slot rejected (acceptance 1.5); deleting a time slot is never blocked and removes its exclusions
- [X] T046 [P] [US1] Write `PER_T/school/SchoolRepositoryIT.kt` and `PER_T/room/RoomRepositoryIT.kt`: round-trip of school + time grid + exclusions, rooms with features, optimistic locking, dependant queries
- [X] T047 [P] [US1] Write `WEB_T/school/SchoolRoutesTest.kt`, `WEB_T/room/RoomRoutesTest.kt`, `WEB_T/timegrid/TimeGridRoutesTest.kt`: create → 303; duplicate room → 422 with entered values kept; overlapping slot → 422; school of another planner → 404; grid page lists slots chronologically with breaks marked; delete confirmation page shows the number of exclusions (and, from US3 on, unavailabilities) that will be removed

### Implementation for User Story 1

- [X] T048 [P] [US1] Create `TimeGrid`, `TimeSlot` (`label` "String (1–40)", `startTime`/`endTime` `LocalTime`, `kind` `TEACHING` | `BREAK`), teaching days as `Set<DayOfWeek>`, `SlotDayExclusion`, with commented overlap check and `teachingSlotsPerWeek()` in `DOM/school/TimeGrid.kt` and `DOM/school/TimeSlot.kt`
- [X] T049 [P] [US1] Create `RoomFeature` (name "String (1–80)") and `Room` (identifier "String (1–40)", `featureIds`, `version`) in `DOM/room/RoomFeature.kt` and `DOM/room/Room.kt`
- [X] T050 [US1] Extend port `SchoolRepository` with time grid persistence and `findDependants` (incl. accounts for which it is the only school); create ports `RoomRepository`, `RoomFeatureRepository` (incl. `findDependants`) in `APP/port/`
- [X] T051 [US1] Implement a `DependantVisibilityFilter` in `APP/access/DependantVisibilityFilter.kt` that splits dependants into names visible to the acting planner and a count of hidden ones (R10), used by every delete use case
- [X] T052 [US1] Implement school use cases `CreateSchool` (assigns creator), `RenameSchool`, `DeleteSchool`, `ListAccessibleSchools`, `GetSchoolDashboard` in `APP/school/`, each guarded by `SchoolAccessPolicy`
- [X] T053 [US1] Implement room use cases `CreateRoomFeature`, `DeleteRoomFeature`, `CreateRoom`, `UpdateRoom`, `DeleteRoom`, `ListRooms` in `APP/room/`
- [X] T054 [US1] Implement time grid use cases `SetTeachingDays`, `AddTimeSlot`, `UpdateTimeSlot`, `DeleteTimeSlot` (never blocked; removes exclusions), `PreviewTimeSlotDeletion` (counts for the confirmation page), `SetSlotExclusions`, `GetTimeGrid` in `APP/school/`
- [X] T055 [P] [US1] Add in-memory fakes for the extended `SchoolRepository`, `RoomRepository`, `RoomFeatureRepository` in `APP_F/`
- [X] T056 [US1] Create migration `PER_R/V2__rooms_time_grid.sql`: `room_feature` (unique `(school_id, lower(name))`), `room` (unique `(school_id, lower(identifier))`, `version`), `room_has_feature`, `teaching_day`, `time_slot` (check `end_time > start_time`, `kind` check), `slot_day_exclusion` (on delete cascade from `time_slot`)
- [X] T057 [US1] Implement Exposed tables and extend `ExposedSchoolRepository`; implement `ExposedRoomRepository`, `ExposedRoomFeatureRepository` in `PER/school/` and `PER/room/`
- [X] T058 [US1] Implement routes and kotlinx.html views for schools (`/schools`, `/schools/{schoolId}`, rename, delete) in `WEB/school/SchoolRoutes.kt` and `WEB/school/SchoolViews.kt` per contracts/web-routes.md "Schools"
- [X] T059 [US1] Implement routes and views for room features and rooms in `WEB/room/RoomRoutes.kt` and `WEB/room/RoomViews.kt` per contracts "Room features and rooms"
- [X] T060 [US1] Implement routes and views for the time grid (days × slots table, breaks visually distinct, per-day exclusions, slot deletion confirmation) in `WEB/timegrid/TimeGridRoutes.kt` and `WEB/timegrid/TimeGridViews.kt` per contracts "Time grid"
- [X] T061 [US1] Wire US1 repositories, use cases and routes in `BOOT/Main.kt`

**Checkpoint**: Quickstart scenarios 0–4 pass against the running app

---
## Phase 3a: User Story 1 - Rework for a single school (Priority: P1) 🎯 MVP

**Goal**: Adapt the already built US1 to the scope change (spec FR-001, FR-036, plan "Rework of already built code", research R17): exactly one school, renamed but never created or deleted in the UI.

**Independent Test**: First login lands on the dashboard of the installation's school; no school list, no create or delete; rename works (quickstart 0–4a).

### Tests for the rework (change first, must fail)

- [X] T062 [P] [US1] Update `APP_T/school/SchoolUseCasesTest.kt`: remove the tests for creating and deleting schools and the test `renaming to another school's name is rejected` (no name uniqueness check in this version); keep rename (version check, "String (1–120)") and dashboard tests; add a test that `ListAccessibleSchools` returns exactly the installation's school for every active planner
- [X] T063 [P] [US1] Update `WEB_T/school/SchoolRoutesTest.kt`: `GET /schools` → 303 to `/schools/{schoolId}` of the installation's school; dashboard shows no create or delete form; `POST /schools` and `POST /schools/{schoolId}/delete` are not routed (404/405); keep rename and foreign-`schoolId` → 404 tests
- [X] T064 [P] [US1] Update `PER_T/school/SchoolRepositoryIT.kt`: remove the test for accounts with only this school and school deletion; keep time grid, name lookup and optimistic locking tests

### Implementation of the rework

- [X] T065 [US1] Remove `CreateSchool`, `DeleteSchool`, `requireUniqueSchoolName` and its call in `RenameSchool` (with one school no name uniqueness check is needed in this version; renaming only validates "String (1–120)" and the version) and the fields `create`/`delete` from `SchoolUseCases` in `APP/school/SchoolUseCases.kt`; remove `schoolDependantSources` from `application/src/main/kotlin/timetablescheduler/application/MasterDataUseCases.kt`
- [X] T066 [US1] Remove `findAccountsWithOnlySchool` and `removeSchoolAssignments` from `APP/port/AccountPorts.kt`, `PER/account/ExposedUserAccountRepository.kt` and `APP_F/fakes/FoundationFakes.kt`; remove `SchoolRepository.delete` from `APP/port/SchoolRepository.kt`, `PER/school/ExposedSchoolRepository.kt` (incl. `TimeGridStore.deleteAll`) and the fake
- [X] T067 [US1] In `WEB/school/SchoolRoutes.kt` and `WEB/school/SchoolViews.kt`: `GET /schools` redirects (303) to the first school returned by `ListAccessibleSchools`; remove `POST /schools`, `POST /schools/{schoolId}/delete`, the school list page and the create/delete forms; keep dashboard and rename; rename the navigation entry "Schulen" to "Schule" in `WEB/layout/PageLayout.kt`
- [X] T068 [US1] Run `./gradlew build`, reset to a fresh database (`docker compose down -v`, then `docker compose up -d db`), then run quickstart scenarios 0–4a against `./gradlew :bootstrap:run` (values with spaces quoted in `.env`) and record the result

**Checkpoint**: All tests green; quickstart 0–4a pass

---

## Phase 4: User Story 5 - Log in and manage accounts (Priority: P1)

**Goal**: Planners manage all accounts (create, role, deactivate/activate, reset password); every user changes their own password. New accounts are assigned to the school automatically (FR-036).

**Independent Test**: As initial planner create planner `mueller` and teacher `lehrer1`; `mueller` works with the school; `lehrer1` sees no master data; the last active planner cannot be deactivated (quickstart 16, 16a, 16c, 16f).

### Tests for User Story 5 (write first, must fail)

- [X] T069 [P] [US5] Write `APP_T/account/AccountManagementUseCasesTest.kt`: create account with role, assigned to the acting planner's school automatically; username "String (3–60)" and "Unique, case-insensitive"; initial password "≥ 10 characters" (FR-034); change role; deactivate/reactivate; reset password; deactivated account cannot log in; deactivating or downgrading the last active planner → `Invalid(NoActivePlannerLeft)` (FR-033); stale `version` → `VersionConflict`; teacher account calling any of these → `AccessDenied`; own password change requires the correct current password and works for teachers
- [X] T070 [P] [US5] Write `WEB_T/account/AccountRoutesTest.kt`: `TEACHER` session on `/accounts` → 403 without data; list shows username, role, active; create → 303 and listed; FR-033 violation → 422 with business message; `/account/password` works for a teacher account; unknown `accountId` → 404

### Implementation for User Story 5

- [X] T071 [US5] Implement `CreateAccount`, `UpdateAccountRole`, `DeactivateAccount`, `ActivateAccount`, `ResetPassword`, `ChangeOwnPassword`, `ListAccounts`, `GetAccount` and an `AccountUseCases` bundle in `APP/account/AccountManagementUseCases.kt` (FR-033 via `PlannerCoverage`; new accounts get `schoolIds` of the acting planner = the school)
- [X] T072 [US5] Add `findBySchool(schoolId)` to `UserAccountRepository` in `APP/port/AccountPorts.kt`, `PER/account/ExposedUserAccountRepository.kt` and `APP_F/fakes/FoundationFakes.kt`; wire `AccountUseCases` into `MasterDataUseCases`
- [X] T073 [US5] Implement routes and views `/accounts`, `/accounts/{accountId}` (+ `/deactivate`, `/activate`, `/password`) and `/account/password` in `WEB/account/AccountRoutes.kt` and `WEB/account/AccountViews.kt` per contracts "User accounts"; register in `WEB/WebModule.kt`

**Checkpoint**: Quickstart scenarios 16, 16a, 16c, 16f pass; US1 still works

---

## Phase 5: User Story 2 - Define subjects, class levels and classes (Priority: P2)

**Goal**: Subjects with required room features, class levels with curriculum, classes with suffix proposal and priority weight; class curriculum resolved from its level.

**Independent Test**: Subjects Mathematik, Deutsch, Chemie (requires "Chemieraum"); level 6 with 4/4/2 periods; classes 6a, 6b each show 10 required periods (quickstart 5–9).

### Tests for User Story 2 (write first, must fail)

- [X] T074 [P] [US2] Write `DOM_T/curriculum/ClassLevelTest.kt` and `DOM_T/curriculum/SubjectTest.kt`: subject name "String (1–80)"; required features from the same school (FR-009); level identifier "String (1–10)"; "`periodsPerWeek: Int ≥ 1`"; "At most one entry per subject"; curriculum subject from the same school
- [X] T075 [P] [US2] Write `DOM_T/curriculum/SchoolClassTest.kt` and `DOM_T/curriculum/SuffixProposalTest.kt`: suffix "String (1–5)" unique per level case-insensitive; display name = level identifier + suffix ("6b"); `priorityWeight` "Int (1–100)", "Default 10"; proposal = "smallest letter `a..z` not used in the level; if all used, no proposal" (FR-013)
- [X] T076 [P] [US2] Write `DOM_T/curriculum/ClassCurriculumResolverTest.kt`: `curriculumOf(class)` equals its level's curriculum and reflects later level changes (FR-014)
- [X] T077 [P] [US2] Write `DOM_T/readiness/LevelExceedsGridRuleTest.kt`: `LEVEL_EXCEEDS_GRID` when "Σ periods of a level > teachingSlotsPerWeek" (acceptance 2.5), with excess amount
- [X] T078 [P] [US2] Write `APP_T/curriculum/CurriculumUseCasesTest.kt`: subject name unique per school; level identifier unique per school; class "6a" duplicate rejected (acceptance 2.4); saving an over-full level succeeds and returns the warning (FR-025); deletion blocked with named dependants: subject used in a curriculum, level with classes, room feature required by a subject (FR-023)
- [X] T079 [P] [US2] Write `PER_T/curriculum/CurriculumRepositoryIT.kt` and `WEB_T/curriculum/CurriculumRoutesTest.kt` (new-class form pre-filled with proposed suffix; class page shows resolved curriculum read only)

### Implementation for User Story 2

- [X] T080 [P] [US2] Create `Subject` (name "String (1–80)", `requiredFeatureIds`, `version`) in `DOM/curriculum/Subject.kt`
- [X] T081 [P] [US2] Create `ClassLevel` (identifier "String (1–10)", `curriculum: List<CurriculumEntry>`, `version`) and `CurriculumEntry(subjectId, periodsPerWeek ≥ 1)` with `weeklyPeriods()` in `DOM/curriculum/ClassLevel.kt`
- [X] T082 [P] [US2] Create `SchoolClass` (suffix "String (1–5)", `priorityWeight` "Int (1–100)" default 10, `version`, `displayName(level)`) and `SuffixProposal` in `DOM/curriculum/SchoolClass.kt` and `DOM/curriculum/SuffixProposal.kt`
- [X] T083 [US2] Create `ClassCurriculumResolver` (only place that reads `ClassLevel.curriculum` for a class; KDoc explains it is the extension point for later per-class deviations, R9) in `DOM/curriculum/ClassCurriculumResolver.kt`
- [X] T084 [US2] Create `ReadinessIssue` sealed type (codes from data-model "Readiness issues") and the `LEVEL_EXCEEDS_GRID` rule in `DOM/readiness/ReadinessIssue.kt` and `DOM/readiness/LevelExceedsGridRule.kt`
- [X] T085 [US2] Create ports `SubjectRepository`, `ClassLevelRepository`, `SchoolClassRepository` in `APP/port/CurriculumPorts.kt`; add a room-feature dependant source for subjects requiring the feature in `MasterDataUseCases`
- [X] T086 [US2] Implement use cases `CreateSubject`, `UpdateSubject`, `DeleteSubject`, `ListSubjects`, `GetSubject`, `CreateClassLevel`, `UpdateClassLevel` (identifier + curriculum, returns warnings), `DeleteClassLevel`, `ListClassLevels`, `GetClassLevel`, `ProposeClassSuffix`, `CreateSchoolClass`, `UpdateSchoolClass`, `DeleteSchoolClass`, `GetSchoolClassWithCurriculum` and a `CurriculumUseCases` bundle in `APP/curriculum/`
- [X] T087 [P] [US2] Add in-memory fakes for the US2 repositories in `APP_F/fakes/CurriculumFakes.kt` and register them in `MasterDataWorld`/`MasterDataPorts`
- [X] T088 [US2] Create migration `PER_R/V3__curriculum.sql`: `subject` (unique `(school_id, lower(name))`, `version`), `subject_requires_feature`, `class_level` (unique `(school_id, lower(identifier))`, `version`), `curriculum_entry` (pk `(class_level_id, subject_id)`, check `periods_per_week >= 1`), `school_class` (unique `(class_level_id, lower(suffix))`, check `priority_weight between 1 and 100`, default 10, `version`)
- [X] T089 [US2] Implement Exposed tables and repositories for subjects, class levels and classes in `PER/curriculum/`
- [X] T090 [US2] Implement routes and views for subjects in `WEB/subject/SubjectRoutes.kt` and `WEB/subject/SubjectViews.kt` per contracts "Subjects"
- [X] T091 [US2] Implement routes and views for class levels (curriculum editor with weekly total and grid warning) and classes in `WEB/classlevel/ClassLevelRoutes.kt` and `WEB/classlevel/ClassLevelViews.kt` per contracts "Class levels and classes"; add dashboard links in `WEB/school/SchoolViews.kt`
- [X] T092 [US2] Wire US2 repositories in `BOOT/Main.kt` and register routes in `WEB/WebModule.kt`

**Checkpoint**: Quickstart scenarios 5–9 pass; US1 and US5 still work

---

## Phase 6: User Story 3 - Define teachers and their constraints (Priority: P3)

**Goal**: Teachers of the school with short code, weekly load, qualifications and unavailability.

**Independent Test**: Teacher "Anna Schmidt" (SCHA, 24 periods, Mathematik + Chemie, Friday afternoon unavailable) is stored and shown unchanged; a load above the school's teaching slots is rejected (quickstart 10–12).

### Tests for User Story 3 (write first, must fail)

- [X] T093 [P] [US3] Write `DOM_T/teacher/TeacherTest.kt` covering every hard constraint: name "String (1–120)" "Not unique"; shortCode "String (2–8, upper case letters/digits)"; weeklyLoad "Int ≥ 0" and "≤ teachingSlotsPerWeek of the school" when saved (FR-018); qualifications "Subjects of the same school" (FR-017); unavailability "Slot of the same school that exists on that day when entered" (FR-021)
- [X] T094 [P] [US3] Write `APP_T/teacher/TeacherUseCasesTest.kt`: shortCode "Unique in installation" (acceptance 3.2); load 40 with 38 teaching slots rejected (acceptance 3.4); qualifications and unavailability saved with version check; teacher of a foreign `schoolId` → `NotFound`; `DeleteSubject` blocked by a qualification naming the teacher; `DeleteTimeSlot` removes the teacher unavailabilities of the slot and reports their count; deleting a teacher removes its qualifications and unavailabilities
- [X] T095 [P] [US3] Write `PER_T/teacher/TeacherRepositoryIT.kt` (round trip incl. qualifications/unavailability, optimistic locking, unique short code, `ExposedTimeSlotUsage` count/delete) and `WEB_T/teacher/TeacherRoutesTest.kt` (create → 303; duplicate short code → 422; unavailability grid saved; delete → 303)

### Implementation for User Story 3

- [X] T096 [US3] Create `Teacher` aggregate (`schoolId`, name, shortCode, weeklyLoad, `qualifiedSubjectIds`, `unavailability`, `version`) with `Unavailability(dayOfWeek, slotId)` and a commented load-limit check in `DOM/teacher/Teacher.kt` and `DOM/teacher/Unavailability.kt`
- [X] T097 [US3] Create port `TeacherRepository` (incl. `findByShortCode`, `findBySchool`, `findQualifiedFor(subjectId)`) in `APP/port/TeacherRepository.kt`; add a subject dependant source for teacher qualifications in `MasterDataUseCases`
- [X] T098 [US3] Implement use cases `CreateTeacher`, `UpdateTeacher`, `UpdateQualifications`, `UpdateUnavailability`, `DeleteTeacher`, `ListTeachers`, `GetTeacher` and a `TeacherUseCases` bundle in `APP/teacher/`
- [X] T099 [P] [US3] Add `InMemoryTeacherRepository` in `APP_F/fakes/TeacherFakes.kt`; make `InMemoryTimeSlotUsage` count real unavailabilities of the fake teacher repository
- [X] T100 [US3] Create migration `PER_R/V4__teachers.sql`: `teacher` (`school_id` fk, unique `short_code`, check `weekly_load >= 0`, `version`), `teacher_qualification` (pk `(teacher_id, subject_id)`), `teacher_unavailability` (pk `(teacher_id, day_of_week, slot_id)`, fk `slot_id` on delete cascade)
- [X] T101 [US3] Implement Exposed tables, `ExposedTeacherRepository` and `ExposedTimeSlotUsage` in `PER/teacher/`; replace the placeholder `NoTeacherUnavailabilitiesYet` in `BOOT/Main.kt`
- [X] T102 [US3] Implement routes and views `/schools/{schoolId}/teachers` and `/schools/{schoolId}/teachers/{teacherId}` (general data, qualifications, unavailability grid days × teaching slots, delete) in `WEB/teacher/TeacherRoutes.kt` and `WEB/teacher/TeacherViews.kt` per contracts "Teachers"; add dashboard link
- [X] T103 [US3] Wire US3 in `BOOT/Main.kt` and `WEB/WebModule.kt`

**Checkpoint**: Quickstart scenarios 10–12, 14 and the confirmation part of 14a pass; earlier stories still work

---

## Phase 7: User Story 4 - Check readiness of the data for scheduling (Priority: P4)

**Goal**: Readiness overview of the school listing all issues from FR-024 with links to the affected entries.

**Independent Test**: Seed Chemie without suitable room and Physik without qualified teacher; overview reports both (quickstart 13, 14a).

### Tests for User Story 4 (write first, must fail)

- [X] T104 [P] [US4] Write `DOM_T/readiness/ReadinessCheckTest.kt` with one test per code: `SUBJECT_NO_SUITABLE_ROOM` (names missing features), `SUBJECT_NO_QUALIFIED_TEACHER`, `SUBJECT_TEACHER_CAPACITY_SHORTFALL` (required periods via `ClassCurriculumResolver`; acceptance 4.2: 40 required vs 30 offered → shortfall 10), `LEVEL_EXCEEDS_GRID`, `TEACHER_WITHOUT_QUALIFICATION`, `TEACHER_ZERO_LOAD`, `TEACHER_LOAD_EXCEEDS_GRID` ("weeklyLoad > teachingSlotsPerWeek"); consistent data → no issues (acceptance 4.3)
- [X] T105 [P] [US4] Write `WEB_T/readiness/ReadinessRoutesTest.kt`: issues grouped by code with links; "Keine blockierenden Probleme gefunden" when empty; foreign `schoolId` → 404

### Implementation for User Story 4

- [X] T106 [US4] Implement `ReadinessCheck` with one commented function per rule (reusing `LevelExceedsGridRule`; KDoc notes capacity is an upper-bound estimate) in `DOM/readiness/ReadinessCheck.kt`
- [X] T107 [US4] Implement `GetSchoolReadiness` in `APP/readiness/GetSchoolReadiness.kt` and add it to `MasterDataUseCases`
- [X] T108 [US4] Implement route and view `/schools/{schoolId}/readiness` and a readiness summary on the dashboard in `WEB/readiness/ReadinessRoutes.kt`, `WEB/readiness/ReadinessViews.kt`, `WEB/school/SchoolViews.kt`; register in `WEB/WebModule.kt`

**Checkpoint**: Quickstart scenarios 13 and 14a pass; all stories work together

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T109 [P] Write `WEB_T/LogConfidentialityTest.kt`: capture log output while creating teacher "Anna Schmidt", provoking a validation error, a deletion-blocked business error naming her and an unexpected exception; assert no log line contains the name, form values or password, and the 500 page contains only the generic text + correlation id (R10, FR-029, constitution III)
- [X] T110 [P] Write `WEB_T/ConcurrentEditTest.kt`: two saves of the same room with the same `version` → second gets 409 conflict page with reload link (FR-027, quickstart 15)
- [X] T111 [P] Write `PER_T/PerformanceSmokeIT.kt`: seed SC-001 volume (40 rooms, 15 subjects, 6 levels, 25 classes, 60 teachers, 5 × 10 grid) and assert school dashboard, teacher list and readiness use cases complete in < 1 s each (R15)
- [X] T112 [P] Update `README.md` with prerequisites, configuration variables (quote values with spaces), run/test commands and CI workflows (link to quickstart.md)
- [X] T113 Review all modules against constitution I, III and V (naming, function size, comments on compact constraint/access checks, test-first coverage of non-trivial functions, no confidential data in logs or technical error output) and fix findings
- [ ] T114 Push the branch, open a pull request and confirm workflows Build and CodeQL succeed without new security alerts; fix findings
- [X] T115 Run all quickstart.md scenarios against `./gradlew :bootstrap:run` with a fresh database and record the result (constitution: UI verified against a running instance)

## Phase 9: User Story 6 - Choose the interface language (Priority: P5)

**Goal**: Every user switches the interface between German and English from any page (with a hint when unsaved changes would be lost); an active choice is saved with the account and wins after login; before a choice the browser language applies (German if neither de nor en). Domain and use cases stay language-free; all texts come from `UiTexts` catalogs in the web adapter (R18, R19).

**Independent Test**: Quickstart 18–22: switch to English on the rooms page, walk all pages without German interface text, keep English after login in a second browser, login page follows the browser language, browser language alone is never saved, external `returnTo` is rejected.

### Tests for User Story 6 (write first, must fail)

- [X] T116 [P] [US6] Write `DOM_T/account/InterfaceLanguageTest.kt` and extend `DOM_T/account/UserAccountTest.kt`: `InterfaceLanguage` codes "de" / "en", `fromCode` case-insensitive, unknown or blank code → null; a new `UserAccount` has `preferredLanguage = null` ("Optional; null until the user chooses"); `withPreferredLanguage` keeps id, role, active, schoolIds and version
- [X] T117 [P] [US6] Write `APP_T/account/ChangeOwnLanguageTest.kt` and extend `APP_T/account/LoginUseCaseTest.kt`: planner and teacher save their own language; deactivated account → `AccessDenied`; saving does not change the account version (no conflict with a planner editing role/active flag at the same time); login with `chosenLanguage = ENGLISH` saves it only if the account has none; login without chosen language saves nothing (browser preference is never saved, FR-042); a remembered cookie language without `chosenLanguage` is never saved; `LoggedInUser` carries the account's language; role change, deactivation, reactivation and password reset keep the saved language
- [X] T118 [P] [US6] Extend `PER_T/account/UserAccountRepositoryIT.kt`: `preferred_language` round trip incl. null; `updatePreferredLanguage` changes neither `version` nor role/active; `update()` (role/active with version check) leaves the saved language untouched; database rejects codes other than "de"/"en"
- [X] T119 [P] [US6] Write `WEB_T/i18n/LanguageResolutionTest.kt`: resolution order session → cookie `TTS_LANG` → `Accept-Language` (highest quality of de/en, e.g. "fr, en;q=0.8" → en) → German; `safeReturnTo` accepts "/schools/x?y=1", rejects "//evil.org", "https://example.org", "javascript:…", "" (→ "/")
- [X] T120 [P] [US6] Write `WEB_T/i18n/LanguageRoutesTest.kt`: logged-in `POST /language` with CSRF saves the account language, updates the session, sets cookie `TTS_LANG` and answers 303 to `returnTo`; without CSRF token → 403; external `returnTo` → 303 to "/"; unknown language → 303 without change; logged-out switch sets only the cookie; `GET /login` with `Accept-Language: en` is English, with "fr" German, cookie beats header; login after an active English switch on the login page saves English for an account without a language, but not for one that has German saved; login without any switch saves nothing; every page has `<html lang="de">` or `lang="en"` and the switch with the current language marked; a cookie set by another user's switch is not saved at the next login without an active switch on the login page; logged-out switch answers 303 to `/login?chosen=1` and the login form then carries `chosenLanguage`; a later `GET /login` without `?chosen=1` in the same browser is shown in the chosen language (cookie) but without the hidden field (FR-040, scenario 8); after a switch the room, subject and teacher records and the account's version are unchanged (FR-047, SC-011)
- [X] T121 [P] [US6] Write `WEB_T/i18n/EnglishPagesTest.kt` (SC-009, FR-045): with the session language English render login, home, own password, school dashboard, room features, rooms, room edit, time grid, slot editor, slot deletion confirmation, subjects, subject, class levels, level, new class, class, teachers, teacher, accounts, account, readiness and the 403, 404, 409 (conflict and deletion blocked), 422 and 500 pages; assert none of the German interface words "Speichern", "Löschen", "Anlegen", "Zurück", "Räume", "Fächer", "Lehrkräfte", "Klassenstufe", "Zeitraster", "Montag", "Planer", "Bezeichnung", "Wochenstunden", "Probleme", "Anmelden", "Abmelden", "Fehlerkennung" appears, while user data (e.g. teacher "Anna Schmidt", room "B-104") is shown unchanged (FR-044)
- [X] T122 [P] [US6] Write `WEB_T/i18n/UiTextsTest.kt`: for both catalogs every `ValidationError` variant, `DependantType`, `ReadinessIssue` variant, `Role`, `SlotKind` and `DayOfWeek` yields a non-blank text, and the English text differs from the German one
- [X] T123 [P] [US6] Write `WEB_T/i18n/UnsavedChangesHintTest.kt`: every page with a form renders the language switch with `data-unsaved-warning` in the current language and includes `/static/language-switch.js`; the script file is served from the app's own origin; keep the script free of logic beyond "field differs from its initial value → confirm" (≤ 30 lines) and verify the confirm/cancel behaviour manually in quickstart 18b — recorded in the review of T137 as the accepted test for the script (no browser test tooling, constitution II)

### Implementation for User Story 6

- [X] T124 [US6] Create `InterfaceLanguage` enum (`GERMAN("de")`, `ENGLISH("en")`, `fromCode`) in `DOM/account/InterfaceLanguage.kt`; add `preferredLanguage: InterfaceLanguage? = null` and `withPreferredLanguage(language)` to `DOM/account/UserAccount.kt`
- [X] T125 [US6] Create migration `PER_R/V5__account_language.sql`: `ALTER TABLE user_account ADD COLUMN preferred_language VARCHAR(2) NULL CHECK (preferred_language IN ('de','en'))`; add the column to `UserAccountTable` in `PER/account/AccountTables.kt`; read/insert it in `PER/account/ExposedUserAccountRepository.kt` (`update()` must not write it); add `updatePreferredLanguage(accountId, language)` (no version check, version unchanged) to `APP/port/AccountPorts.kt`, the Exposed repository and `InMemoryUserAccountRepository` in `APP_F/fakes/FoundationFakes.kt`
- [X] T126 [US6] Implement `ChangeOwnLanguage(actor, language)` (any active account, else `AccessDenied`) in `APP/account/ChangeOwnLanguage.kt` and add it as `changeOwnLanguage` to `AccountUseCases`; extend `APP/account/LoginUseCase.kt`: `execute(username, password, chosenLanguage: InterfaceLanguage?)` saves `chosenLanguage` only if the account has no `preferredLanguage`, `LoggedInUser` gets `language: InterfaceLanguage?` (account language, else chosen language); wire in `application/src/main/kotlin/timetablescheduler/application/MasterDataUseCases.kt`
- [X] T127 [US6] Create `UiTexts` interface and `GermanTexts` in `WEB/i18n/UiTexts.kt` and `WEB/i18n/GermanTexts.kt` (R18): move every text of `WEB/layout/Messages.kt` (incl. `describe(ValidationError)`, `describe(DependantType)`, field labels) and of `WEB/layout/FormComponents.kt`, `WEB/layout/PageLayout.kt`, `WEB/WebModule.kt` (error pages), `WEB/auth/AuthRoutes.kt` and `WEB/account/AccountViews.kt` unchanged into `GermanTexts`; views get the texts from the call (temporarily always German) and contain no literal interface text; delete `Messages.kt`; all existing German route tests must stay green
- [X] T128 [US6] Move the interface texts of `WEB/school/SchoolViews.kt`, `WEB/room/RoomViews.kt` and `WEB/timegrid/TimeGridViews.kt` into `UiTexts`/`GermanTexts`; replace `germanName()` of `WEB/layout/RouteSupport.kt` by `texts.dayName(day)` and the slot-kind names by `texts.slotKindName(kind)`; existing tests stay green
- [X] T129 [US6] Move the interface texts of `WEB/subject/SubjectViews.kt`, `WEB/classlevel/ClassLevelViews.kt`, `WEB/teacher/TeacherViews.kt` and `WEB/readiness/ReadinessViews.kt` (incl. one text function per `ReadinessIssue` variant and the group headings) into `UiTexts`/`GermanTexts`; existing tests stay green
- [X] T130 [US6] Implement `EnglishTexts` in `WEB/i18n/EnglishTexts.kt` with an English text for every `UiTexts` member (school terms: room feature, time grid, teaching slot, class level, weekly periods, teaching load, readiness)
- [X] T131 [US6] Implement language resolution in `WEB/i18n/LanguageResolution.kt`: `ApplicationCall.uiLanguage()` (session → cookie `TTS_LANG` → `Accept-Language` → German), `ApplicationCall.texts`, `safeReturnTo(raw)`; extend `WEB/auth/UserSession.kt` and its serializer with the language ("accountId|csrf|lang", old two-part values read as no language); set it at login in `WEB/auth/AuthRoutes.kt`, which passes the hidden form field `chosenLanguage` (rendered only after `?chosen=1`) to the login use case; the cookie is never passed
- [X] T132 [US6] Implement `POST /language` in `WEB/i18n/LanguageRoutes.kt` (contracts "Authentication": cookie `TTS_LANG` path "/", `SameSite=Strict`, one year; with session CSRF check, `changeOwnLanguage`, session update, 303 to `safeReturnTo`; without session cookie only and 303 to `/login?chosen=1`) and register it in `WEB/WebModule.kt`; render `<html lang>` and the language switch (one button per language, current one marked, `returnTo` = current path and query, CSRF field when logged in, `data-unsaved-warning` text) and include `/static/language-switch.js` from T133 in `WEB/layout/PageLayout.kt` and on the login page; error pages in `WebModule.kt` use the resolved texts without database access
- [X] T133 [P] [US6] Create `adapters/web/src/main/resources/static/language-switch.js` (no library): remember the initial values of all form fields, and on submit of the language switch ask `confirm(<data-unsaved-warning>)` only if a field changed; cancelling keeps the page; style the switch in `adapters/web/src/main/resources/static/app.css` (including the script in `PageLayout.kt` is part of T132, which owns that file)
- [X] T134 [US6] Run `./gradlew build`, then quickstart scenarios 18–22 against `./gradlew :bootstrap:run` (V5 applied on the existing database) and record the result; note the time of one switch on the rooms page in the result

**Checkpoint**: Quickstart 18–22 pass; all earlier scenarios still pass in German

### Polish for User Story 6

- [X] T135 [P] Extend `WEB_T/LogConfidentialityTest.kt` to run the same steps with the English interface (FR-048) and assert the 500 page shows the English generic text + correlation id only
- [X] T136 [P] Update `README.md`: interface languages, how the language is chosen and remembered, cookie `TTS_LANG`
- [X] T137 Run all quickstart.md scenarios (0–22) against `./gradlew :bootstrap:run` with a fresh database and record the result

---

## Dependencies & Execution Order

### Phase Dependencies

- Phases 1–3 are done (built before the scope change, see Notes).
- **Phase 3a (US1 rework)** → user story phases → **Polish (Phase 8)** → **US6 (Phase 9)**, added after the scope addition of 2026-10-09

### User Story Dependencies

- **US1 rework (P1)**: first; removes code later phases must not build on.
- **US5 (P1)**: after 3a.
- **US2 (P2)**: after 3a (subjects reference room features; the warning needs the time grid). Can run in parallel with US5.
- **US3 (P3)**: after US2 (subjects for qualifications).
- **US4 (P4)**: after US1, US2, US3.
- **US6 (P5)**: after all other stories, because it touches every page; T127–T129 move texts and must keep all existing tests green.

```text
Setup ✓ → Foundational ✓ → US1 ✓ → US1 rework ─┬─→ US5
                                               └─→ US2 → US3 → US4 → Polish → US6
```

### Within Each Phase

- Tests first and failing (Principle V) → domain → ports → use cases → fakes → migration → repositories → routes/views → wiring
- `BOOT/Main.kt`, `MasterDataUseCases.kt` and `WebModule.kt` are shared files: tasks touching them run sequentially

### Parallel Opportunities

- 3a: T062–T064 together
- US5: T069, T070 together
- US2: T074–T079 together; T080–T082 together
- US3: T093–T095 together
- US4: T104, T105 together
- Polish: T109–T112 together
- US6: T116–T123 together; T133 parallel to T127–T132; T135, T136 together
- US6 sequential chain (shared `UiTexts`, `PageLayout.kt`, `WebModule.kt`): T127 → T128 → T129 → T130 → T131 → T132
- After 3a: US5 and US2 in parallel

---

## Parallel Example: User Story 2

```text
# Tests together: T074, T075, T076, T077, T078, T079
# Domain together: T080 Subject.kt, T081 ClassLevel.kt, T082 SchoolClass.kt/SuffixProposal.kt
```

## Parallel Example: User Story 3

```text
# Tests together: T093 TeacherTest, T094 TeacherUseCasesTest, T095 repository IT + route tests
```

---

## Parallel Example: User Story 6

```text
# Tests together: T116 domain, T117 use cases, T118 repository IT, T119–T123 web tests
# Meanwhile: T133 language-switch.js + CSS
```

---

## Implementation Strategy

### MVP First

1. Phase 3a: finish the single-school US1 → validate with quickstart 0–4a → demo
2. + US5 → several planners and teacher accounts

### Incremental Delivery

1. + US2 → curriculum demand side
2. + US3 → teacher supply side and hard constraints
3. + US4 → readiness overview
4. Polish → log-leak, concurrency, performance, CI security results, full quickstart run
5. + US6 → German/English interface: first move all texts into `GermanTexts` without visible change (T127–T129), then add English and the switch (T130–T133)

---

## Notes

- **Scope change (single school, R17)**: T001–T061 were implemented on branch `001-scheduling-master-data` before the change and remain valid except the parts reworked in Phase 3a (school creation/deletion, school list, school-deletion checks). Their descriptions are kept as history; where they mention several schools, Phase 3a and the current data-model take precedence. The former multi-school tasks for US5 and US3 (account school assignment, `AccountManagementPolicy`, commute times, remove-from-my-schools) were dropped.
- Migrations: V1 schools + accounts, V2 rooms + time grid (both existing), V3 curriculum, V4 teachers, V5 account language (US6) — each story ships its schema change (constitution quality gate).
- **Scope addition (US6, 2026-10-09)**: interface language German/English, developed on `one-school` by decision of the project owner. Decided in clarification: browser language until an active choice, choice saved with the account, browser language alone never saved, hint before losing unsaved changes. A language column in the planner's account list is not part of US6.
- Commit after each task or logical group on branch `one-school`.
- Stop at any checkpoint to validate the story independently.
