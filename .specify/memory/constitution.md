# Timetable Scheduler Constitution

## Core Principles

### I. Kotlin Clean Code Standards
We follow clean code standards for Kotlin. 
For example, functions, parameters, and variables MUST use speaking, domain-specific names (e.g.
`teacherAvailability`, `classWeighting`) instead of general-purpose names (e.g. `data`, `payload`, `value`). Functionality MUST be composed of smaller small, single-purpose functions rather than creating large multi-purpose functions. Every function whose logic is complex but compact (e.g. constraint checks, scheduling/optimization routines, usage of flow operators) MUST carry a comment explaining what it does and why.

### II. Tool and Library Use Where Appropriate
Use existing open-source tools and libraries when creating  functionality.
However, introduce dependencies as minimal as possible (i.e, do not use libraries for trivial functionai)

### III. Confidentiality & Data Protection (NON-NEGOTIABLE)
The application stores and processes confidential information, e.g., about teachers and school classes. Access to this data MUST be protected by authentication, and operations on it MUST be authorized against the requesting user's role. Confidential data MUST NOT appear in logs, stack traces, or diagnostic output. Data persisted data must be protected by access control or by encryption. 
Error messages are treated by kind:
- Business (domain) error messages that explain why a user action was rejected (e.g., "Subject cannot be deleted: still taught by teacher Anna Schmidt") MAY contain confidential data, but only data the requesting user is authorized to see (e.g., the planner of that school) and only as much as needed to resolve the problem. Such messages MUST NOT be written to logs with their confidential content.
- Technical and unexpected error messages (e.g., exceptions, database or infrastructure failures) MUST NOT contain confidential data, neither when surfaced to users nor in logs; they show a generic message and a correlation id only.

Any export, report, or API response MUST include only the minimum data necessary for its purpose.

### IV. Pareto-Optimal Scheduling Integrity
The purpose of the application is to create Pareto-optimal solutions across the competing objectives of filling every class's timetable as well as possible, without ever violating a defined hard constraint (e.g. a teacher double-booked, a class scheduled outside available slots). A produced solution set MUST allow the user to compare trade-offs between pareto-optimal alternatives rather than silently collapsing them into a single "best" answer (pareto-curve).

### V. Test-Driven Development (NON-NEGOTIABLE)
For ensuring maintainability we apply test-driven development.
Thus, for every function, non-trivial tests have to be created that have to succeed after the implementation. 
Do not aim for something like code-coverage but create meaningful tests, especially for complex functionality. 


## Technology & Architecture Constraints

- Language & build: Kotlin on Gradle is the only supported language/build toolchain for production code. We use a CI/CD pipeline on github for this purpose.
  Exception: small static browser scripts (plain JavaScript served as a static resource of the application) are allowed when an interaction cannot be realized with server-rendered HTML alone. Such scripts MUST NOT require a build tool, package manager, transpiler or third-party library, MUST NOT contain business, validation, scheduling or access logic (these stay in Kotlin), MUST NOT process or store confidential data, and the application MUST remain usable when they are disabled (progressive enhancement). Each script stays small and single-purpose and is checked in review like any other code. 
- Clean architecture: We use the clean architecture for structuring the application. Thus, we have circles like entities (most inner circle), the application specific business rules, interface adapters and the frameworks & drivers (most outer circle). 
Dependencies point only from outer circles to inner ones. 
- Database: any relational or embedded database is acceptable provided it supports transactional writes and access control sufficient to satisfy Principle III. The specific engine is an implementation detail.
- Secrets and credentials (database connection strings, auth tokens) MUST NOT be committed to the repository and MUST be supplied via configuration external to source control.
- Security testing: We use static security application testing tools to detect security issues. 
First, we use CodeQL with its basic security suit for basic security testing in the CI/CD pipleline. 
Second, we use GitHub Advanced Security as software composition analysis. 
Still, the CI/CD pipleine and the project structure has to be designed in a way, that also other tools can be used. 

## Development Workflow & Quality Gates

- Code review MUST verify: descriptive naming, function size/single-purpose decomposition, presence of explanatory comments on complex-but-compact functions, and absence of confidential data in logs and in technical/unexpected error output (business error messages only with data the user is authorized to see, per Principle III).
- Changes to scheduling constraints or weighting logic MUST include tests demonstrating hard constraints are never violated and that pareto comparisons behave as intended.
- Changes to persisted entities MUST include the corresponding database migration in the same change set.
- UI changes MUST be verified against a running instance of the application before being reported as complete.
- New features, fixes, and improvements will be developed in a separate branch. NO work will be directly performed on the main-branch

## Governance

This constitution supersedes all other project practices and guidelines, including AGENTS.md, where a conflict exists; AGENTS.md continues to govern day-to-day style details not covered here. Amendments require: (1) a documented rationale for the change, (2) an update to this file including
the Sync Impact Report, and (3) a version bump per the policy below. Versioning follows semantic versioning: MAJOR for backward-incompatible principle removals or redefinitions, MINOR for new principles or materially expanded guidance, PATCH for clarifications and wording fixes. All pull
requests and reviews MUST verify compliance with this constitution; unjustified complexity or deviation from a principle MUST be called out and either resolved or explicitly justified in the review.

**Version**: 1.2.0 | **Ratified**: 2026-09-21 | **Last Amended**: 2026-10-09
