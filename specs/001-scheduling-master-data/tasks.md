---

description: "Task list for Scheduling Master Data Management"
---

# Tasks: Scheduling Master Data Management

**Input**: Design documents from `specs/001-scheduling-master-data/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/web-routes.md](./contracts/web-routes.md), [quickstart.md](./quickstart.md)

**Tests**: Mandatory. Constitution v1.1.0 Principle V (Test-Driven Development, NON-NEGOTIABLE) requires meaningful tests for every non-trivial function, written before the implementation; the quality gate additionally requires tests showing hard constraints are never violated. Within each phase, write the test tasks first and make sure they fail before implementing (R11).

**Organization**: Tasks are grouped by user story. Story order follows priority: US1 (P1, MVP), US5 (P1), US2 (P2), US3 (P3), US4 (P4). Login, sessions, installation (first school + initial planner) and the school access policy are foundational because every story needs them; account *management* is US5.

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

## Phase 4: User Story 5 - Log in and manage accounts and school access (Priority: P1)

**Goal**: Planners manage the accounts of their schools (create, role, schools, deactivate, reset password); every user changes their own password.

**Independent Test**: Initial planner creates a planner for "Realschule Süd" only; that planner sees neither "Gymnasium Nord" nor the initial planner's account (quickstart 16, 16a, 16c, 16d, 16f).

### Tests for User Story 5 (write first, must fail)

- [ ] T062 [P] [US5] Write `APP_T/access/AccountManagementPolicyTest.kt`: "account `a` is manageable by planner `p` iff `a.schoolIds ⊆ p.schoolIds`" (FR-032a); own account is manageable; teacher accounts follow the same rule
- [ ] T063 [P] [US5] Write `APP_T/account/AccountManagementUseCasesTest.kt`: create account with role and ≥ 1 of own schools (FR-031a, FR-032); assigning a school not in `p.schoolIds` → `AccessDenied`; removing an account's last school → `Invalid(LastSchoolRemoved)`; password "≥ 10 characters" (FR-034); deactivate/reactivate; reset password; non-manageable account → `NotFound`; downgrading/deactivating/unassigning the only active planner of a school → `Invalid(NoActivePlannerLeft)` (FR-033); username uniqueness case-insensitive; own password change requires correct current password
- [ ] T064 [P] [US5] Write `WEB_T/account/AccountRoutesTest.kt`: `TEACHER` session on `/accounts` → 403 without data; list shows only manageable accounts; `/accounts/{id}` of non-manageable account → 404; FR-031a/FR-033 violation → 422; `/account/password` works for teacher accounts

### Implementation for User Story 5

- [ ] T065 [US5] Implement `AccountManagementPolicy` (commented subset check) in `APP/access/AccountManagementPolicy.kt`
- [ ] T066 [US5] Implement `CreateAccount`, `UpdateAccountRoleAndSchools`, `DeactivateAccount`, `ActivateAccount`, `ResetPassword`, `ChangeOwnPassword`, `ListManageableAccounts` in `APP/account/`
- [ ] T067 [US5] Extend `ExposedUserAccountRepository` with queries needed by T066 (accounts whose schools are a subset of a given set, active planners of a school) in `PER/account/ExposedUserAccountRepository.kt`; extend `InMemoryUserAccountRepository` in `APP_F/`
- [ ] T068 [US5] Implement routes and views `/accounts`, `/accounts/{accountId}` (+ `/deactivate`, `/activate`, `/password`) and `/account/password` in `WEB/account/AccountRoutes.kt` and `WEB/account/AccountViews.kt` per contracts "User accounts"
- [ ] T069 [US5] Wire US5 use cases and routes in `BOOT/Main.kt`

**Checkpoint**: Quickstart scenarios 16, 16a, 16c, 16d pass; US1 still works

---

## Phase 5: User Story 2 - Define subjects, class levels and classes (Priority: P2)

**Goal**: Subjects with required room features, class levels with curriculum, classes with suffix proposal and priority weight; class curriculum resolved from its level.

**Independent Test**: Subjects Mathematik, Deutsch, Chemie (requires "Chemieraum"); level 6 with 4/4/2 periods; classes 6a, 6b each show 10 required periods (quickstart 5–9).

### Tests for User Story 2 (write first, must fail)

- [ ] T070 [P] [US2] Write `DOM_T/curriculum/ClassLevelTest.kt` and `DOM_T/curriculum/SubjectTest.kt`: subject name "String (1–80)"; required features from the same school (FR-009); level identifier "String (1–10)"; "`periodsPerWeek: Int ≥ 1`"; "At most one entry per subject"; curriculum subject from same school
- [ ] T071 [P] [US2] Write `DOM_T/curriculum/SchoolClassTest.kt` and `DOM_T/curriculum/SuffixProposalTest.kt`: suffix "String (1–5)" unique per level case-insensitive; display name = level identifier + suffix ("6b"); `priorityWeight` "Int (1–100)", "Default 10"; proposal = "smallest letter `a..z` not used in the level; if all used, no proposal" (FR-013)
- [ ] T072 [P] [US2] Write `DOM_T/curriculum/ClassCurriculumResolverTest.kt`: `curriculumOf(class)` equals its level's curriculum and reflects later level changes (FR-014, edge case "curriculum is changed after classes exist")
- [ ] T073 [P] [US2] Write `DOM_T/readiness/LevelExceedsGridRuleTest.kt`: `LEVEL_EXCEEDS_GRID` when "Σ periods of a level > teachingSlotsPerWeek" (acceptance 2.5), with excess amount
- [ ] T074 [P] [US2] Write `APP_T/curriculum/CurriculumUseCasesTest.kt`: subject name unique per school; level identifier unique per school; class "6a" duplicate rejected (acceptance 2.4); saving an over-full level succeeds and returns the warning (FR-025); deletion blocked with named dependants: subject used in curriculum, level with classes, room feature required by a subject (FR-023)
- [ ] T075 [P] [US2] Write `PER_T/curriculum/CurriculumRepositoryIT.kt` and `WEB_T/curriculum/CurriculumRoutesTest.kt` (new-class form pre-filled with proposed suffix; class page shows resolved curriculum read only)

### Implementation for User Story 2

- [ ] T076 [P] [US2] Create `Subject` (name "String (1–80)", `requiredFeatureIds`, `version`) in `DOM/curriculum/Subject.kt`
- [ ] T077 [P] [US2] Create `ClassLevel` (identifier "String (1–10)", `curriculum: List<CurriculumEntry>`, `version`) and `CurriculumEntry(subjectId, periodsPerWeek ≥ 1)` with `weeklyPeriods()` in `DOM/curriculum/ClassLevel.kt`
- [ ] T078 [P] [US2] Create `SchoolClass` (suffix "String (1–5)", `priorityWeight` "Int (1–100)" default 10, `version`, `displayName(level)`) and `SuffixProposal` in `DOM/curriculum/SchoolClass.kt` and `DOM/curriculum/SuffixProposal.kt`
- [ ] T079 [US2] Create `ClassCurriculumResolver` (only place that reads `ClassLevel.curriculum` for a class; KDoc explains it is the extension point for later per-class deviations, R9) in `DOM/curriculum/ClassCurriculumResolver.kt`
- [ ] T080 [US2] Create `ReadinessIssue` sealed type (all codes from data-model "Readiness issues", incl. `TEACHER_LOAD_EXCEEDS_GRID`) and the `LEVEL_EXCEEDS_GRID` rule in `DOM/readiness/ReadinessIssue.kt` and `DOM/readiness/LevelExceedsGridRule.kt`
- [ ] T081 [US2] Create ports `SubjectRepository`, `ClassLevelRepository`, `SchoolClassRepository` (each with `findDependants`) in `APP/port/`; extend `RoomFeatureRepository.findDependants` to include subjects requiring the feature
- [ ] T082 [US2] Implement use cases `CreateSubject`, `UpdateSubject`, `DeleteSubject`, `ListSubjects`, `CreateClassLevel`, `UpdateClassLevelCurriculum` (returns warnings), `DeleteClassLevel`, `ListClassLevels`, `ProposeClassSuffix`, `CreateSchoolClass`, `UpdateSchoolClass`, `DeleteSchoolClass`, `GetSchoolClassWithCurriculum` in `APP/curriculum/`
- [ ] T083 [P] [US2] Add in-memory fakes for the US2 repositories in `APP_F/`
- [ ] T084 [US2] Create migration `PER_R/V3__curriculum.sql`: `subject` (unique `(school_id, lower(name))`), `subject_requires_feature`, `class_level` (unique `(school_id, lower(identifier))`), `curriculum_entry` (pk `(class_level_id, subject_id)`, check `periods_per_week >= 1`), `school_class` (unique `(class_level_id, lower(suffix))`, check `priority_weight between 1 and 100`, default 10)
- [ ] T085 [US2] Implement Exposed tables and repositories for subjects, class levels and classes in `PER/curriculum/`
- [ ] T086 [US2] Implement routes and views for subjects in `WEB/subject/SubjectRoutes.kt` and `WEB/subject/SubjectViews.kt` per contracts "Subjects"
- [ ] T087 [US2] Implement routes and views for class levels (curriculum editor with weekly total and grid warning) and classes in `WEB/classlevel/ClassLevelRoutes.kt` and `WEB/classlevel/ClassLevelViews.kt` per contracts "Class levels and classes"
- [ ] T088 [US2] Wire US2 in `BOOT/Main.kt`

**Checkpoint**: Quickstart scenarios 5–9 pass; US1 and US5 still work

---

## Phase 6: User Story 3 - Define teachers and their constraints (Priority: P3)

**Goal**: Teachers with short code, weekly load, schools, qualifications, commute times and unavailability; cross-school visibility, removal and deletion per FR-028b/c.

**Independent Test**: Teacher "Anna Schmidt" (SCHA, 24 periods, two schools, Mathematik + Chemie, 30 min commute, Friday afternoon unavailable) is stored and shown unchanged; a planner of only one school removes her from that school without affecting the other (quickstart 10–12, 16e).

### Tests for User Story 3 (write first, must fail)

- [ ] T089 [P] [US3] Write `DOM_T/teacher/TeacherTest.kt` covering every hard constraint: name "String (1–120)" "Not unique"; shortCode "String (2–8, upper case letters/digits)"; `schoolIds` "≥ 1"; weeklyLoad "Int ≥ 0" and "≤ Σ teachingSlotsPerWeek over the teacher's schools" when saved (FR-018); qualifications "Each subject must belong to one of the teacher's schools"; commute time "stored with `schoolAId < schoolBId`", "both schools must be in `schoolIds`", `minutes ≥ 0`; unavailability "Slot belongs to one of the teacher's schools and exists on that day when entered"; incomplete while a school pair lacks a commute time (acceptance 3.5); `removeSchools(own)` drops related qualifications, unavailabilities and commute times
- [ ] T090 [P] [US3] Write `APP_T/teacher/TeacherUseCasesTest.kt`: shortCode "Unique in installation" (acceptance 3.2); planner sees teacher if assigned to ≥ 1 of the teacher's schools (FR-028b); planner may edit general data and own-school qualifications/unavailability/commute times, but not those of other schools (→ `AccessDenied`); full delete only with all of the teacher's schools, otherwise `AccessDenied` (FR-028c); `RemoveTeacherFromMySchools` keeps the teacher unchanged at other schools (acceptance 3.6); `DeleteTimeSlot` removes teacher unavailabilities referencing the slot and returns their count; `DeleteSubject` blocked by qualification with the teacher named
- [ ] T091 [P] [US3] Write `PER_T/teacher/TeacherRepositoryIT.kt` and `WEB_T/teacher/TeacherRoutesTest.kt` (other-school data shown read-only/hidden; duplicate short code → 422; incomplete teacher page shows the missing commute time prompt; `/delete` without all schools → 403; `/remove-from-my-schools` → 303)

### Implementation for User Story 3

- [ ] T092 [US3] Create `Teacher` aggregate with `CommuteTime`, `Unavailability(dayOfWeek, slotId)`, commented checks for load limit and commute pair normalisation, `isComplete()` and `removeSchools(...)` in `DOM/teacher/Teacher.kt`, `DOM/teacher/CommuteTime.kt`, `DOM/teacher/Unavailability.kt`
- [ ] T093 [US3] Extend `SchoolAccessPolicy` with `requireTeacherAccess`, per-section edit checks and `requireTeacherDeletion` (all schools assigned) per data-model "Access rules" in `APP/access/SchoolAccessPolicy.kt`
- [ ] T094 [US3] Create port `TeacherRepository` (incl. `findByShortCode`, `findBySchool`, `deleteUnavailabilitiesOfSlot`) in `APP/port/TeacherRepository.kt`; extend `SchoolRepository`/`SubjectRepository` dependant queries with teacher references
- [ ] T095 [US3] Implement use cases `CreateTeacher`, `UpdateTeacherGeneralData`, `UpdateTeacherSchools`, `UpdateQualifications`, `UpdateCommuteTimes`, `UpdateUnavailability`, `DeleteTeacher`, `RemoveTeacherFromMySchools`, `ListVisibleTeachers`, `GetTeacherForPlanner` (filters data of unassigned schools) in `APP/teacher/`; extend `DeleteTimeSlot` in `APP/school/` to remove referencing unavailabilities and report their count; extend `PreviewTimeSlotDeletion` with the unavailability count
- [ ] T096 [P] [US3] Add `InMemoryTeacherRepository` in `APP_F/`
- [ ] T097 [US3] Create migration `PER_R/V4__teachers.sql`: `teacher` (unique `short_code`, check `weekly_load >= 0`, `version`), `teacher_school`, `teacher_qualification`, `commute_time` (pk `(teacher_id, school_a_id, school_b_id)`, check `school_a_id < school_b_id` and `minutes >= 0`), `teacher_unavailability` (pk `(teacher_id, day_of_week, slot_id)`, fk `slot_id` on delete cascade)
- [ ] T098 [US3] Implement Exposed tables and `ExposedTeacherRepository` in `PER/teacher/`
- [ ] T099 [US3] Implement routes and views `/teachers`, `/teachers/{teacherId}` with sections qualifications, commute times (prominent prompt while incomplete, acceptance 3.5), unavailability grid, delete and remove-from-my-schools in `WEB/teacher/TeacherRoutes.kt` and `WEB/teacher/TeacherViews.kt` per contracts "Teachers"
- [ ] T100 [US3] Wire US3 in `BOOT/Main.kt`

**Checkpoint**: Quickstart scenarios 10–12, 14, 14a (first part), 16e, 16f pass; earlier stories still work

---

## Phase 7: User Story 4 - Check readiness of the data for scheduling (Priority: P4)

**Goal**: Per-school readiness overview listing all issues from FR-024 with links to affected entries.

**Independent Test**: Seed Chemie without suitable room and Physik without qualified teacher; overview reports both (quickstart 13, 14a).

### Tests for User Story 4 (write first, must fail)

- [ ] T101 [P] [US4] Write `DOM_T/readiness/ReadinessCheckTest.kt` with one test per code: `SUBJECT_NO_SUITABLE_ROOM` (names missing features), `SUBJECT_NO_QUALIFIED_TEACHER`, `SUBJECT_TEACHER_CAPACITY_SHORTFALL` (required periods computed via `ClassCurriculumResolver`; acceptance 4.2: 40 required vs 30 offered → shortfall 10), `LEVEL_EXCEEDS_GRID`, `TEACHER_WITHOUT_QUALIFICATION`, `TEACHER_ZERO_LOAD`, `TEACHER_LOAD_EXCEEDS_GRID` ("weeklyLoad > Σ teachingSlotsPerWeek over the teacher's schools", e.g. after a grid reduction), `TEACHER_MISSING_COMMUTE_TIME`; consistent data → no issues (acceptance 4.3)
- [ ] T102 [P] [US4] Write `WEB_T/readiness/ReadinessRoutesTest.kt`: issues grouped by code with links; "Keine blockierenden Probleme gefunden" when empty; unassigned school → 404

### Implementation for User Story 4

- [ ] T103 [US4] Implement `ReadinessCheck` with one commented function per rule (reusing `LevelExceedsGridRule`; KDoc notes capacity is an upper-bound estimate) in `DOM/readiness/ReadinessCheck.kt`
- [ ] T104 [US4] Implement `GetSchoolReadiness` use case (loads one school's data incl. its teachers' other schools' slot totals, runs `ReadinessCheck`) in `APP/readiness/GetSchoolReadiness.kt`
- [ ] T105 [US4] Implement route and view `/schools/{schoolId}/readiness` and a readiness summary on the school dashboard in `WEB/readiness/ReadinessRoutes.kt`, `WEB/readiness/ReadinessViews.kt`, `WEB/school/SchoolViews.kt`
- [ ] T106 [US4] Wire US4 in `BOOT/Main.kt`

**Checkpoint**: Quickstart scenarios 13 and 14a pass; all stories work together

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T107 [P] Write `WEB_T/LogConfidentialityTest.kt`: capture log output while creating teacher "Anna Schmidt", provoking a validation error, a deletion-blocked business error naming her and an unexpected exception; assert no log line contains the name, form values or password, and the 500 page contains only the generic text + correlation id (R10, FR-029, constitution III)
- [ ] T108 [P] Write `WEB_T/ConcurrentEditTest.kt`: two saves of the same room with the same `version` → second gets 409 conflict page with reload link (FR-027, quickstart 15)
- [ ] T109 [P] Write `PER_T/PerformanceSmokeIT.kt`: seed SC-001 volume (40 rooms, 15 subjects, 6 levels, 25 classes, 60 teachers, 5 × 10 grid) and assert school dashboard, teacher list and readiness use cases complete in < 1 s each (R15)
- [ ] T110 [P] Update `README.md` with prerequisites, configuration variables, run/test commands and CI workflows (link to quickstart.md)
- [ ] T111 Review all modules against constitution I, III and V (naming, function size, comments on compact constraint/access checks, test-first coverage of non-trivial functions, no confidential data in logs or technical error output) and fix findings
- [ ] T112 Push the branch, open a pull request and confirm workflows Build and CodeQL succeed without new security alerts; fix findings
- [ ] T113 Run all quickstart.md scenarios against `./gradlew :bootstrap:run` with a fresh database and record the result (constitution: UI verified against a running instance)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → user story phases → **Polish (Phase 8)**

### User Story Dependencies

- **US1 (P1)**: after Foundational. No story dependencies.
- **US5 (P1)**: after Foundational; its independent test needs a second school, created with US1's `CreateSchool`.
- **US2 (P2)**: after US1 (subjects reference room features; the warning needs the time grid).
- **US3 (P3)**: after US1 (schools, time slots) and US2 (subjects for qualifications).
- **US4 (P4)**: after US1, US2, US3 (checks all of their data).

```text
Setup → Foundational → US1 ─┬─→ US5
                            └─→ US2 → US3 → US4 → Polish
```

### Within Each Phase

- Tests first and failing (Principle V) → domain → ports → use cases → fakes → migration → repositories → routes/views → wiring
- `BOOT/Main.kt` wiring tasks are sequential (same file)

### Parallel Opportunities

- Setup: T004–T008, T010–T014
- Foundational: tests T015–T020 together; then T021–T025, T027, T030, T034, T036, T037, T041 in parallel
- Per story: all test tasks together; domain entity tasks marked [P] together
- After US1: US5 and US2 can proceed in parallel
- Polish: T107–T110 in parallel

---

## Parallel Example: User Story 1

```text
# Tests together:
T042 TimeGridTest, T043 RoomTest, T044 SchoolUseCasesTest, T045 Room/TimeGrid use case tests,
T046 repository ITs, T047 route tests

# Domain together:
T048 TimeGrid.kt/TimeSlot.kt, T049 RoomFeature.kt/Room.kt
```

## Parallel Example: User Story 3

```text
# Tests together: T089 TeacherTest, T090 TeacherUseCasesTest, T091 repository IT + route tests
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup (incl. CI) → Phase 2 Foundational (installation + login work)
2. Phase 3 US1 → validate with quickstart 0–4 → demo

### Incremental Delivery

1. + US5 → several planners with separated schools (needed before real data from more than one school is entered)
2. + US2 → curriculum demand side
3. + US3 → teacher supply side and hard constraints
4. + US4 → readiness overview
5. Polish → log-leak, concurrency, performance, CI security results, full quickstart run

---

## Notes

- Migrations are split per story (V1 schools + accounts, V2 rooms + time grid, V3 curriculum, V4 teachers) so each story ships its schema change in the same change set (constitution quality gate). `school` is in V1 because installation creates the first school.
- Commit after each task or logical group on branch `001-scheduling-master-data`.
- Stop at any checkpoint to validate the story independently.
