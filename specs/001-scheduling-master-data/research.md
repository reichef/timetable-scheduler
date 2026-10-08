# Research: Scheduling Master Data Management

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-10-08

All "NEEDS CLARIFICATION" items of the Technical Context are resolved below.

## R1 — Web framework

- **Decision**: Ktor (server, Netty engine).
- **Rationale**: Kotlin-native, small footprint, explicit wiring (no annotation magic) which keeps the outer clean-architecture ring thin and replaceable. Provides authentication, sessions, status pages and a test host (`testApplication`) out of the box.
- **Alternatives considered**: Spring Boot (mature, Spring Security included, but large dependency surface and framework-centric structure — conflicts with "dependencies as minimal as possible"); http4k (very small, but smaller ecosystem for sessions/auth).

## R2 — User interface technology

- **Decision**: Server-rendered HTML with `kotlinx.html` inside the web module; plain HTML forms with the Post/Redirect/Get pattern; one small CSS file. No client-side framework in this feature.
- **Rationale**: The constitution allows only Kotlin/Gradle for production code, which rules out a separate TypeScript/npm frontend. Master data entry is form-centric, so server rendering is sufficient and fully testable through the Ktor test host. htmx can be added later for interactivity (e.g., the timetable editor) without changing this structure.
- **Alternatives considered**: Kotlin/JS or Compose for Web (Kotlin-only, but a second compilation target and much higher complexity for CRUD forms); Thymeleaf/Freemarker templates (non-Kotlin template language, no type safety).

## R3 — Database

- **Decision**: PostgreSQL (current stable), run via Docker for development; integration tests use Testcontainers.
- **Rationale**: Transactional, mature role-based access control (dedicated least-privilege application user satisfies Principle III "persisted data protected by access control"), good fit for the later solution storage.
- **Alternatives considered**: H2/embedded (simple, but weak access control and dialect drift between test and production); SQLite (no real user/role model).

## R4 — Data access library

- **Decision**: JetBrains Exposed (DSL API, not DAO) + HikariCP connection pool.
- **Rationale**: Kotlin-idiomatic, typesafe SQL, lightweight. The DSL keeps persistence rows separate from domain types, so mapping stays inside the persistence adapter and the domain has no framework dependency.
- **Alternatives considered**: JPA/Hibernate (heavy, annotation-based entities leak into domain); jOOQ (excellent, but code generation step adds build complexity); plain JDBC (too much boilerplate for ~15 tables).

## R5 — Schema migrations

- **Decision**: Flyway with versioned SQL migrations in the persistence module (`db/migration/V<n>__<description>.sql`), executed at application start-up.
- **Rationale**: Constitution requires that changes to persisted entities ship with their migration in the same change set; plain SQL migrations make that reviewable.
- **Alternatives considered**: Liquibase (more features, more verbose); Exposed `SchemaUtils.create` (no versioned history — unacceptable).

## R6 — Authentication, accounts and access policies

- **Context**: Clarified in the spec (FR-030 – FR-035, FR-031a, FR-032a, FR-028a–c): built-in login and school-scoped account management now, single sign-on prepared for later.
- **Decision**:
  - **Accounts separated from credentials**: `user_account` holds identity and authorization (username, role, active flag, school assignments). Credentials live in a separate `password_credential` table (one row per account). A later SSO adapter adds an `external_identity` table (`provider`, `subject`) linked to the same account — accounts and roles stay untouched (FR-035).
  - **Port**: application port `CredentialAuthenticator` returns an `AuthenticatedUser(accountId)`; the password adapter is the only implementation now, an OIDC adapter can be added beside it.
  - **Session**: Ktor form login with a server-side session cookie (`HttpOnly`, `SameSite=Strict`, `Secure` outside local dev). Role, active flag and school assignments are re-read on every request so deactivation and re-assignment take effect immediately.
  - **Installation**: On start-up with an empty database, the school (`TTS_INITIAL_SCHOOL_NAME`) and the initial planner (`TTS_INITIAL_PLANNER_USER` / `_PASSWORD`) assigned to it are created in one transaction (FR-031). Afterwards the variables are ignored.
  - **Data access**: `SchoolAccessPolicy` in the application layer checks every use case: active planner assigned to the school. With one school this is always the school of the installation; any other `schoolId` answers `404`. It is the single extension point for FR-028a–c (R17).
  - **Account management**: every active planner manages all accounts; new accounts of both roles are assigned to the school automatically (`account_school`, FR-036). Own password change is always allowed. FR-033 (≥ 1 active planner) is checked on deactivate and downgrade. Per-school manageability (FR-032a) is deferred (R17).
- **Rationale**: Smallest solution that satisfies Principle III and the clarified account management, while keeping SSO an additive change.
- **Alternatives considered**: OIDC only (rejected in clarification — needs extra infrastructure first); credentials as columns of `user_account` (SSO would later leave unused columns and mixed concerns); HTTP Basic auth (no logout, weak UX).

## R7 — Password hashing

- **Decision**: `at.favre.lib:bcrypt` (cost 12).
- **Rationale**: Small, dependency-free, well-maintained; hashing is security-critical and must not be hand-written.
- **Alternatives considered**: Argon2 via `argon2-jvm` (native library dependency); Spring Security Crypto (drags in Spring).

## R8 — Concurrent edit detection (FR-027)

- **Decision**: Optimistic locking with an integer `version` column on every aggregate root (School, Room, Subject, ClassLevel, SchoolClass, Teacher). Forms carry the version in a hidden field; an update affecting 0 rows returns a conflict page offering a reload.
- **Rationale**: Simple, no locks held across user think time.
- **Alternatives considered**: Pessimistic locks (unsuitable for web forms); last-write-wins (violates FR-027).

## R9 — Per-class curriculum, prepared for later deviations (FR-014)

- **Decision**: The domain exposes `ClassCurriculumResolver.curriculumOf(schoolClass)`. In this feature it returns the curriculum of the class's level. No curriculum rows are copied to classes. A later feature can add a `ClassCurriculumAdjustment` table and extend the resolver, without migrating or re-entering existing class or level data.
- **Rationale**: Everything downstream (readiness check now, scheduler later) asks for a *class's* curriculum, so introducing deviations later changes only the resolver. Copying rows now would create synchronisation problems with level changes (Edge Case: level change must reflect on all classes).
- **Alternatives considered**: Copy curriculum to each class on creation (sync problems); no abstraction (later change touches every consumer).

## R10 — Confidential data in logs and error messages (Principle III, FR-029)

- **Decision**: SLF4J + Logback. Two kinds of errors, as distinguished by constitution v1.1.0:
  - **Business errors** (`Invalid`, `DeletionBlocked`, `VersionConflict`, access denied): typed values from domain/application, rendered by the web layer as German messages. They MAY name entries the requesting planner is authorized to see, e.g. the teachers blocking a subject deletion — the dependants list is filtered through `SchoolAccessPolicy`, and entries of other schools are only counted ("und 2 Einträge anderer Schulen"). When logged, only the error type and entity UUIDs are written.
  - **Technical/unexpected errors** (exceptions, DB/infrastructure failures): a global Ktor `StatusPages` handler shows a generic German message with the correlation id and logs only exception class + correlation id — never the exception message or stack-trace text, which may contain data.
  - Generally: log only UUIDs and event types, never names or form contents. A test asserts that requests containing a teacher name produce no log line containing that name, including business-error and technical-error paths.
- **Alternatives considered**: Log masking filters (regex-based, brittle, false sense of safety).

## R11 — Testing (Principle V: Test-Driven Development)

- **Decision**: Test-first: for every non-trivial function the test is written before the implementation and must fail first; tests target behaviour (rules, use cases, access decisions), not a coverage number. Tools: `kotlin.test` on JUnit 5 for unit tests (domain, application), Ktor `testApplication` for web route tests (with fake repositories), Testcontainers PostgreSQL for persistence integration tests. Manual verification of the UI against a running instance per constitution, guided by [quickstart.md](./quickstart.md).
- **Rationale**: Uses tooling already present in the build; Testcontainers avoids dialect drift.
- **Alternatives considered**: Kotest (nicer DSL, additional dependency without clear need); H2 for persistence tests (dialect drift).

## R12 — Module structure

- **Decision**: Gradle multi-module build: `domain`, `application`, `adapters:persistence`, `adapters:web`, `bootstrap`. Dependencies point inward only (see plan).
- **Rationale**: The clean-architecture constraint (dependencies only point inward) is enforced by the compiler rather than by review discipline.
- **Alternatives considered**: Single module with packages (dependency rule only enforced by review); ArchUnit rules on a single module (extra dependency, checks only at test time).

## R13 — Build tooling

- **Decision**: Add the Gradle wrapper (pinned version) and a version catalog (`gradle/libs.versions.toml`); upgrade the Kotlin plugin to the current stable 2.x release; keep JVM toolchain 21.
- **Rationale**: The repository currently has no wrapper and no `gradle` on the PATH, so builds are not reproducible.

## R14 — UI language

- **Decision**: User-facing texts in German, code and identifiers in English. Texts kept in one message object per screen area so translation can be added later.
- **Rationale**: Target users are German school planners (Gymnasium/Realschule terminology).

## R15 — Performance and scale

- **Decision**: No special measures. Expected volume per installation: ≤ 5 schools, ≤ 200 teachers, ≤ 100 classes, ≤ 10 concurrent planners. Readiness check runs in memory over one school's data (< a few thousand rows).
- **Target**: Every page and form submission responds in under 1 s on a single small server.

## R16 — CI/CD pipeline and security testing

- **Context**: Constitution v1.1.0 requires a GitHub CI/CD pipeline, CodeQL (basic security suite) and GitHub Advanced Security for software composition analysis, and a setup that allows adding other tools.
- **Decision**:
  - `.github/workflows/build.yml`: on push and pull request — checkout, `actions/setup-java` (Temurin 21), `gradle/actions/setup-gradle`, `./gradlew build` (includes all tests; Testcontainers uses the runner's Docker). A second job runs `gradle/actions/dependency-submission` so GitHub's dependency graph sees the resolved Gradle dependencies (needed for Dependabot alerts of GitHub Advanced Security).
  - `.github/workflows/codeql.yml`: CodeQL `java-kotlin` with the default security query suite (`security-extended` not enabled), `build-mode: manual` running `./gradlew assemble` (Kotlin requires a traced build); on push to `main`, pull requests and weekly schedule. Results uploaded as SARIF.
  - `.github/dependabot.yml`: weekly update checks for `gradle` and `github-actions` ecosystems.
  - **Extensibility**: all jobs call plain Gradle tasks (`build`, `assemble`) instead of tool-specific build logic, and scan results use SARIF upload — another scanner (e.g. Semgrep, OWASP Dependency-Check, Trivy) is an additional job without changing the project structure.
  - Workflows use least-privilege `permissions:` per job (`contents: read`, `security-events: write` only for CodeQL/SARIF upload) and pin actions to a major version.
- **Rationale**: Uses GitHub-native tooling named by the constitution with minimal configuration.
- **Alternatives considered**: CodeQL default setup via repository settings (no versioned config in the repo, less transparent); `build-mode: none` (not supported for Kotlin).

## R17 — Single school per installation, prepared for multiple schools (FR-001, FR-036)

- **Context**: Scope change: the first version manages exactly one school; multi-school support must be addable later without re-entering data.
- **Decision**:
  - **Keep the school as owner of all data**: `school` table and `school_id` on rooms, room features, time slots, subjects, class levels and teachers stay. Teachers get a plain `school_id` column (no join table yet); the extension migrates it into `teacher_school` automatically.
  - **Keep `account_school`** (already built): every account is assigned to the one school when it is created. No schema change is needed when accounts may later belong to several schools.
  - **Keep `SchoolAccessPolicy` and `DependantVisibilityFilter`**: trivially satisfied with one school; they are the places the extension adapts.
  - **Keep the URL scheme `/schools/{schoolId}/…`**: `/schools` redirects to the one school instead of listing schools.
  - **Remove from the built code** (implemented before the scope change): creating and deleting schools, the school list page, the school-deletion dependant checks and the account queries used only for them; school name uniqueness becomes moot but the unique index stays harmless.
  - **Not built**: commute times, teachers at several schools, account school selection, `AccountManagementPolicy`.
- **Rationale**: Smallest product for a quick start while every future change is additive (new tables/columns, broader policy), never a data re-entry.
- **Alternatives considered**: Removing `school` entirely (extension would need to re-key every table); keeping the full multi-school UI hidden behind a flag (unused code that must still be tested and maintained).
