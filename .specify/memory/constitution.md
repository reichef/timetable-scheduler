# Timetable Scheduler Constitution

## Core Principles

### I. Kotlin Clean Code Standards
All code is written in Kotlin and we follow clean code standard. 
For examples, functions, parameters, and variables MUST use speaking, domain-specific names (e.g.
`teacherAvailability`, `classWeighting`) instead of general-purpose names (e.g. `data`, `payload`, `value`). Functionality MUST also be decomposed into
small, single-purpose functions rather than large multi-purpose ones. Every function whose logic is complex but compact (e.g. constraint checks, scheduling/optimization routines) MUST carry a comment explaining what it does and why.


### II. Clean Architecture
We use a clean architecture to structure the system. So we use the circles of the clean architecture where outer circles only depend on inner ones and not vice versa. 

### III. Tool and Library Use Where Appropriate
Use existing open-source tools and libraries when creating  functionality.
However, introduce dependencies as minimal as possible (i.e, do not use libraries for trivial functionality)

### IV. Confidentiality & Data Protection (NON-NEGOTIABLE)
The application stores and processes confidential information, e.g., about teachers and school classes. Access to this data MUST be protected by authentication, and operations on it MUST be authorized against the requesting user's role. Confidential data MUST NOT appear in logs, stack traces, or diagnostic output, and error messages surfaced to users or logs MUST be scrubbed of such data. Data persisted data must be protected by access control or by encryption. 
Any export, report, or API response MUST include only the minimum data necessary for its purpose.


### V. Pareto-Optimal Scheduling Integrity
The purpose of the application is to create Pareto-optimal solutions across the competing objectives of filling every class's timetable as well as possible, without ever violating a defined hard constraint (e.g. a teacher double-booked, a class scheduled outside available slots). A produced solution set MUST allow the user to compare trade-offs between pareto-optimal alternatives rather than silently collapsing them into a single "best" answer (pareto-curve).



## Technology & Architecture Constraints

- Language & build: Kotlin on Gradle is the only supported language/build
  toolchain for production code.
- Layering: the codebase MUST maintain a clear separation between the UI
  layer, the scheduling/domain layer, and the persistence layer, with
  dependencies pointing from UI and persistence toward the domain, never the
  reverse.
- Database: any relational or embedded database is acceptable provided it
  supports transactional writes and access control sufficient to satisfy
  Principle IV. The specific engine is an implementation detail owned by the data-access layer.
- Secrets and credentials (database connection strings, auth tokens) MUST NOT be committed to the repository and MUST be supplied via configuration
  external to source control.

## Development Workflow & Quality Gates

- Code review MUST verify: descriptive naming, function size/single-purpose decomposition, presence of explanatory comments on complex-but-compact
  functions, and absence of confidential data in logs or error output.
- Changes to scheduling constraints or weighting logic MUST include tests demonstrating hard constraints are never violated and that pareto
  comparisons behave as intended.
- Changes to persisted entities MUST include the corresponding database migration in the same change set.
- UI changes MUST be verified against a running instance of the application before being reported as complete.
- New features, fixes, and improvements will be developed in a separate branch. NO work will be directly performed on the main-branch

## Governance

This constitution supersedes all other project practices and guidelines, including AGENTS.md, where a conflict exists; AGENTS.md continues to govern day-to-day style details not covered here. Amendments require: (1) a documented rationale for the change, (2) an update to this file including
the Sync Impact Report, and (3) a version bump per the policy below. Versioning follows semantic versioning: MAJOR for backward-incompatible principle removals or redefinitions, MINOR for new principles or materially expanded guidance, PATCH for clarifications and wording fixes. All pull
requests and reviews MUST verify compliance with this constitution; unjustified complexity or deviation from a principle MUST be called out and either resolved or explicitly justified in the review.

**Version**: 1.0.0 | **Ratified**: 2026-09-21 | **Last Amended**: 2026-09-21
