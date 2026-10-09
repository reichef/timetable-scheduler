# Feature Specification: Scheduling Master Data Management

**Feature Branch**: `001-scheduling-master-data` (single-school variant on branch `one-school`)

**Created**: 2026-10-08

**Status**: Draft

**Input**: User description: "As a user, i want to be able to create all information necessary for the scheduling. Define a School in which a subject is held; a room in the school with an identificator; a subject with a name and optional constraints/properties (e.g., chemistry only in a chemistry room with certain equipment); a class level with the subjects the level is expected to be taught, the number of hours per subject and an identifier (like 6th level); teachers with a name as identifier, the subjects they are able to teach, the amount of hours they teach and other teacher-specific constraints (e.g., a teacher teaches in different schools and requires commute time); classes which have a class level and a name, identified by class level + running identifier (5a, 5b, 6a, ...); the time table itself with the time slots (from time to time), the days, subjects, classes, rooms and teachers. Consider the purpose of the application defined in docs/APP_CONTEXT.md"

## Context

The application exists to generate Pareto-optimal school timetables: every class's timetable should be filled as completely as possible without ever violating a hard constraint, and the planner compares trade-offs between alternative solutions. The scheduler can only do this if it knows *what* has to be scheduled (classes and their required subject hours), *who* can teach it (teachers and their qualifications and limits), *where* it can take place (rooms and their equipment) and *when* (the weekly time grid). This feature lets the school planner capture, maintain and check this information. Generating, editing and publishing timetables are separate features.

**Scope of this variant**: As a quick start, an installation manages exactly **one school**. Managing several schools (school groups, teachers working at several schools, commute times, school-scoped access) is a planned later extension; this feature must be built so that the extension can be added without re-entering existing data (FR-036).

## Clarifications

### Session 2026-10-08

- Q: Where and for whom does the application run — a server accessed by several people via browser, or locally on one computer for a single planner? → A: Central server (in the school network or hosted), used concurrently by several planners via browser, later also by teachers, each with a login.
- Q: Where do login and user accounts come from — a simple login built into this feature, or single sign-on via an existing school account? → A: Built-in login with account management by planners now (create, deactivate, reset password); prepared so that single sign-on via an external identity provider can be added later.
- Q: How should a teacher's weekly load apply when scheduling — upper limit, exact target, or range? → A: Hard upper limit; reaching the load is an optimization objective that is part of the Pareto trade-off.
- Q: May planners access all schools of the installation or only the schools assigned to them? → A: Only assigned schools (one or more); teachers working at several schools are visible to every planner responsible for one of those schools. *(Deferred to the multi-school extension by the scope change below.)*
- Q: Should planners already be able to fix individual lessons in this feature that the scheduler must not move? → A: No; fixed lessons come with the later feature for editing timetables.
- Q: Whose user accounts may a planner manage (create, deactivate, reset passwords)? → A: Only accounts whose schools are all assigned to the planner; teacher accounts are also assigned to schools and follow the same rule. *(Deferred to the multi-school extension; with one school every planner manages all accounts.)*
- Q: May user accounts exist without an assigned school, and if so, what may they do? → A: No; the first school is created together with the initial planner during installation, so every account always has at least one school. *(With one school, every account belongs to it.)*
- Q: What happens when a planner deletes a teacher who also teaches at schools not assigned to the planner? → A: Full deletion only if all of the teacher's schools are assigned to the planner; otherwise the planner can only remove the teacher from their own schools. *(Deferred to the multi-school extension.)*
- Q: What happens when a planner shrinks the time grid (removes slots or teaching days) so that a teacher's weekly load exceeds the new limit? → A: The change is saved; excessive loads appear as a readiness issue that must be fixed before scheduling; unavailabilities and day exclusions for removed days are kept but ignored.
- Q: What happens to teacher unavailabilities when a time slot is removed? → A: They are removed with the slot; the planner sees their number before confirming.
- Scope change: Should the first version manage several schools or only one? → A: Exactly one school per installation as a quick start; multi-school support is prepared for (FR-036) but not built.

### Session 2026-10-09

- Scope addition: Should users be able to choose the interface language? → A: Yes, German and English, chosen by every user for themselves (User Story 6, FR-037 – FR-048).
- Q: Which language should a user see before they have chosen one themselves? → A: The browser's preferred language, switchable in the interface at any time (German if the browser prefers neither German nor English).
- Q: Should the chosen language be saved with the account or only in the current browser? → A: Saved with the account; after login the account language takes precedence over the browser language.
- Q: Should the language shown at login be saved to the account automatically if the user never chose one? → A: No; only an active choice (pressing a language button, also on the login page) is saved. Until then the browser language applies on every login.
- Q: What happens to unsaved form entries when the user switches the language? → A: The page is reloaded in the new language and unsaved entries are lost; if the page has unsaved changes, a hint warns about this first and the user can cancel the switch.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Set up the school, rooms and the weekly time grid (Priority: P1)

A planner maintains the name of their school, records every room with a unique identifier and the equipment or features it offers (e.g., "chemistry lab", "gym", "computers"), and defines the weekly time grid: the teaching days and the ordered list of time slots per day with start and end times, including breaks between slots.

**Why this priority**: Rooms and the time grid are the physical and temporal frame every other piece of scheduling information refers to. Without them nothing can be scheduled.

**Independent Test**: In the school created at installation, add three rooms with different features, define a Monday–Friday grid with 8 slots and two breaks, then reopen the data in a new session and confirm everything is shown unchanged.

**Acceptance Scenarios**:

1. **Given** the school "Gymnasium Nord" created at installation, **When** the planner renames it to "Gymnasium am Nordpark", **Then** the new name is shown everywhere.
2. **Given** the school exists, **When** the planner adds room "B-104" with the feature "chemistry lab", **Then** the room is listed with its feature.
3. **Given** room "B-104" already exists, **When** the planner adds another room "B-104", **Then** the system rejects the entry and explains that the identifier is already in use.
4. **Given** the school exists, **When** the planner defines teaching days Monday–Friday and slots "1st period 08:00–08:45", "2nd period 08:50–09:35", a break 09:35–09:55 and further periods, **Then** the grid is shown in chronological order with breaks visibly distinguished from teaching slots.
5. **Given** a slot 08:00–08:45 exists, **When** the planner adds a slot 08:30–09:15 on the same day, **Then** the system rejects it because slots must not overlap.

---

### User Story 2 - Define subjects, class levels and classes (Priority: P2)

A planner defines the subjects taught at the school, optionally stating which room features a subject requires (e.g., Chemistry requires "chemistry lab"). They define class levels (e.g., "Level 6") together with the curriculum: which subjects a class of this level must receive and how many periods per week each. Finally they create the actual classes of each level, named by the level plus a running suffix (5a, 5b, 5c, 6a, ...), and optionally give a class a priority weight that tells the scheduler how important it is to fill this class's timetable completely.

**Why this priority**: The curriculum per class is the demand side of the scheduling problem; it defines what "a fully filled timetable" means for each class.

**Independent Test**: Create subjects Mathematics, German and Chemistry (requiring "chemistry lab"), create Level 6 with Mathematics 4, German 4 and Chemistry 2 periods per week, create classes 6a and 6b, and confirm both classes show the 10 required periods inherited from Level 6.

**Acceptance Scenarios**:

1. **Given** the school exists, **When** the planner creates subject "Chemistry" with required room feature "chemistry lab", **Then** the subject is listed with that requirement.
2. **Given** subjects exist, **When** the planner creates class level "6" with Mathematics 4 and German 4 periods per week, **Then** the level shows a weekly total of 8 required periods.
3. **Given** level "6" exists with classes 6a and 6b, **When** the planner adds a new class to level 6, **Then** the system proposes the next free suffix "c" and the class is identified as "6c".
4. **Given** class "6a" exists, **When** the planner tries to create another "6a", **Then** the system rejects it as a duplicate.
5. **Given** level "6" requires 30 periods per week but the school's time grid only offers 28 teaching slots per week, **When** the planner saves the level, **Then** the system saves it but warns that the curriculum cannot fit into the weekly grid.

---

### User Story 3 - Define teachers and their constraints (Priority: P3)

A planner records each teacher of the school with their name, the subjects they are qualified to teach, their weekly teaching load and the times they are unavailable.

**Why this priority**: Teachers are the supply side of the scheduling problem and the source of most hard constraints (no double booking, availability, qualification).

**Independent Test**: Create teacher "Anna Schmidt" qualified for Mathematics and Chemistry with 24 weekly periods, unavailable Friday afternoons; reopen and confirm all values.

**Acceptance Scenarios**:

1. **Given** subjects exist, **When** the planner creates teacher "Anna Schmidt" with subjects Mathematics and Chemistry and 24 weekly periods, **Then** the teacher is listed with these qualifications and load.
2. **Given** a teacher "Anna Schmidt" exists, **When** the planner creates another teacher with the same name, **Then** the system requires a distinguishing short code (e.g., "SCHA" vs "SCHM") so that every teacher remains uniquely identifiable.
3. **Given** a teacher exists, **When** the planner marks Friday slots 6–8 as unavailable, **Then** these slots are shown as blocked for that teacher.
4. **Given** the school offers 38 teaching slots per week, **When** the planner enters a weekly load of 40 periods, **Then** the entry is rejected.

---

### User Story 4 - Check readiness of the data for scheduling (Priority: P4)

Before starting a scheduling run, the planner opens an overview that reports whether the entered data is complete and consistent enough to schedule, and which gaps would prevent classes from being fully filled.

**Why this priority**: The scheduler can only produce good solutions from consistent input. Spotting gaps early saves failed or poor scheduling runs, but the data entry itself (P1–P3) must exist first.

**Independent Test**: Seed data in which Chemistry is required but no room has the "chemistry lab" feature and no teacher is qualified for Physics, open the overview and confirm both problems are reported with a reference to the affected entries.

**Acceptance Scenarios**:

1. **Given** a subject requires a room feature that no room offers, **When** the planner opens the readiness overview, **Then** the subject is listed as unschedulable with the missing feature named.
2. **Given** classes require 40 periods of Mathematics per week and qualified teachers offer only 30, **When** the planner opens the overview, **Then** a capacity shortfall of 10 periods for Mathematics is reported.
3. **Given** all data is consistent, **When** the planner opens the overview, **Then** it states that no blocking issues were found.

---

### User Story 5 - Log in and manage accounts (Priority: P1)

Users log in with username and password. Planners create accounts for colleagues (planner or teacher), deactivate and reactivate accounts and reset passwords.

**Why this priority**: The data is confidential (Constitution III); no other story may be used without login. It is listed last only to keep the numbering of the data stories stable.

**Independent Test**: Install with school "Gymnasium Nord"; as the initial planner create a second planner and a teacher account; log in as the second planner and see the school; log in as the teacher and see no master data.

**Acceptance Scenarios**:

1. **Given** a fresh installation with school "Gymnasium Nord", **When** the initial planner logs in with the credentials set during installation, **Then** they see that school, without any rooms, subjects, classes or teachers yet.
2. **Given** a login attempt with a wrong password, **When** it is submitted, **Then** access is denied with a message that does not reveal whether username or password was wrong.
3. **Given** a planner is logged in, **When** they create planner account "mueller", **Then** "mueller" can log in and work with the school's master data.
4. **Given** an account was deactivated, **When** its owner tries to log in, **Then** the login is refused.
5. **Given** planner "mueller" is the only active planner, **When** someone tries to deactivate or downgrade "mueller", **Then** the action is rejected.

---

### User Story 6 - Choose the interface language (Priority: P5)

Every user chooses whether the interface is shown in German or English. The choice can be changed from any page, is remembered for the user's account across logins and devices, and is also available on the login page. Data that users entered themselves (names, identifiers, short codes) is never translated.

**Why this priority**: The target users are mainly German-speaking, so the application is fully usable without this story; an English interface makes it usable for colleagues and schools who prefer English.

**Independent Test**: Log in as a planner, open the rooms page, switch to English and confirm that navigation, headings, labels, buttons and messages on this and all following pages are English while room identifiers stay unchanged; log out, log in from another browser and confirm the interface is still English; open the login page in a browser that prefers English and confirm it is English.

**Acceptance Scenarios**:

1. **Given** a planner works in German on the rooms page, **When** they select English, **Then** the rooms page is shown again with all interface texts in English and the room identifiers unchanged.
2. **Given** the interface is English, **When** the planner opens any other page (school, time grid, subjects, class levels, teachers, accounts, readiness), **Then** all interface texts on it are English.
3. **Given** the interface is English, **When** the planner submits an invalid entry (e.g., a duplicate room identifier) or a deletion is blocked, **Then** the explanation is in English and names of dependent entries are shown as entered.
4. **Given** the interface is English, **When** an unexpected technical error occurs, **Then** the generic error message is in English and still shows only the error reference, no data.
5. **Given** a user chose English, **When** they log out and log in again, also in another browser or on another device, **Then** the interface is English without choosing again.
6. **Given** two users share a computer and user A has chosen English and user B German, **When** each of them logs in, **Then** each sees their own language.
7. **Given** a browser that prefers English and no earlier choice, **When** the login page is opened, **Then** it is shown in English; for a browser preferring any other language than German or English it is shown in German.
8. **Given** the login page is shown, **When** the user switches the language there, **Then** the login page, including the message for failed logins, appears in the chosen language.
9. **Given** a user switched to English on the login page and their account has no saved choice, **When** they log in, **Then** English is kept and saved for their account; **Given** an account with English saved, **When** its owner logs in from a German login page, **Then** the interface after login is English.
10. **Given** a user never pressed a language button and their account has no saved choice, **When** they log in once from a browser preferring English and later from one preferring German, **Then** they see English the first time and German the second time, and their account still has no saved language.
11. **Given** a planner has typed a new room identifier but not saved it, **When** they press "English", **Then** a hint warns that the unsaved entry will be lost; **When** they cancel, **Then** the page stays German with the entry still in the field; **When** they confirm, **Then** the rooms page is shown in English without the entry.

---

### Edge Cases

- A subject, room feature, class level or room is deleted while other data still refers to it: the system blocks the deletion and lists the dependent entries.
- A class level's curriculum is changed after classes exist: all classes of the level reflect the change.
- A teacher's weekly load exceeds the school's teaching slots per week when the teacher is saved: the entry is rejected (FR-018).
- A planner removes slots or teaching days so that an existing teacher's weekly load now exceeds that limit: the grid change is saved, the teacher is reported in the readiness overview, and the load must be corrected before scheduling. Unavailabilities and day exclusions referring to removed days are kept but ignored. Removing a time slot also removes the teachers' unavailabilities entered for that slot; before confirming, the planner is told how many unavailabilities will be removed.
- A slot whose end time is not after its start time, or a day exclusion for a day that is not a teaching day: rejected.
- A teacher is qualified for no subject or has 0 weekly periods: allowed (e.g., on leave) but reported in the readiness overview.
- Two users edit the same entry at the same time: the later save must not silently overwrite the earlier one; the user is informed and can reload.
- A user without the planner role tries to view or change master data: access is denied and no data is revealed.
- A planner tries to deactivate their own account while being the only active planner: rejected (FR-033).
- A user switches the interface language while a form contains unsaved changes: a hint warns that the changes will be lost and the user can cancel; if they confirm, the page is shown again in the new language without the unsaved changes. Without unsaved changes the switch happens immediately, without a hint.
- Deactivating, reactivating, resetting the password of or changing the role of an account does not change its saved interface language.
- Two users share a browser: user A switches to English and logs out; user B, who never chose a language, logs in without pressing a language button. The login page is English (remembered in the browser), but nothing is saved for B; after login B sees their browser's preferred language.
- Weekday names, roles, slot kinds and readiness issue descriptions are interface texts and appear in the chosen language; texts entered by users (school name, room identifiers, feature, subject and level names, class suffixes, teacher names and short codes, usernames, slot labels) are never translated.

## Requirements *(mandatory)*

### Functional Requirements

**School**

- **FR-001**: The installation MUST manage exactly one school, created during installation (FR-031). Planners MUST be able to view and rename it. Creating further schools and deleting the school are not part of this feature (see FR-036).

**Rooms**

- **FR-002**: Planners MUST be able to create, view, edit and delete rooms; each room MUST have an identifier that is unique within the school.
- **FR-003**: Planners MUST be able to maintain a list of room features (e.g., "chemistry lab", "gym") and assign any number of them to a room.

**Time grid**

- **FR-004**: Planners MUST be able to define the teaching days of the week.
- **FR-005**: Planners MUST be able to define an ordered list of time slots with a start and end time; each slot MUST be marked either as a teaching slot or as a break.
- **FR-006**: The system MUST reject slots whose end time is not after their start time and slots that overlap another slot.
- **FR-007**: The same slot structure MUST apply to all teaching days unless the planner removes individual slots for a specific day (e.g., a shorter Friday).

**Subjects**

- **FR-008**: Planners MUST be able to create, view, edit and delete subjects with a name unique within the school.
- **FR-009**: Planners MUST be able to state, per subject, zero or more required room features; a lesson of that subject may then only take place in a room that offers all of them.

**Class levels and classes**

- **FR-010**: Planners MUST be able to create class levels with an identifier unique within the school (e.g., "5", "6").
- **FR-011**: Planners MUST be able to define, per class level, the subjects to be taught and the number of periods per week for each subject (a positive whole number).
- **FR-012**: Planners MUST be able to create classes belonging to exactly one class level; a class MUST be identified by the level identifier plus a suffix (e.g., "6b"), unique within the school.
- **FR-013**: When creating a class, the system MUST propose the next unused suffix of that level in alphabetical order.
- **FR-014**: Each class MUST have its own curriculum (subjects and weekly periods); in this feature it is always identical to the curriculum of its class level and cannot be adjusted per class.
- **FR-015**: Planners MUST be able to assign each class a priority weight (default: equal weight for all classes) that the scheduler uses when trading off how completely different classes' timetables are filled.

**Teachers**

- **FR-016**: Planners MUST be able to create, view, edit and delete teachers with a name and a short code; the short code MUST be unique within the installation.
- **FR-017**: Planners MUST be able to record the subjects a teacher is qualified to teach.
- **FR-018**: Planners MUST be able to record a teacher's weekly teaching load in periods; the value MUST NOT exceed the school's teaching slots per week. The load is a hard upper limit for scheduling; reaching it is an optimization objective that the scheduler includes in the Pareto trade-off (alongside how completely classes are filled).
- **FR-019**: *Deferred to the multi-school extension* (assigning a teacher to several schools). In this feature every teacher belongs to the school.
- **FR-020**: *Deferred to the multi-school extension* (commute times between schools).
- **FR-021**: Planners MUST be able to mark individual time slots (per day) as unavailable for a teacher.

**Constraint semantics**

- **FR-022**: The following MUST be recorded as hard constraints that the scheduler is never allowed to violate: required room features of a subject (FR-009), teacher qualifications (FR-017), teacher weekly load as an upper limit (FR-018) and teacher unavailability (FR-021).

**Integrity and readiness**

- **FR-023**: The system MUST prevent deletion of any entry that other entries depend on and MUST list the dependent entries to the planner.
- **FR-024**: The system MUST provide a readiness overview that reports at least: subjects whose required room features no room offers; subjects required by a class level for which no qualified teacher exists; subjects where required weekly periods across all classes exceed the weekly load of all qualified teachers; class levels whose weekly periods exceed the school's teaching slots; teachers with no qualifications or zero load; teachers whose weekly load exceeds the school's teaching slots per week (e.g., after the time grid was reduced).
- **FR-025**: Warnings from the readiness overview MUST NOT prevent saving data; only the validation rules in FR-001 to FR-021 block saving.
- **FR-026**: All master data MUST be stored persistently so that it is available unchanged in later sessions.
- **FR-027**: Concurrent edits to the same entry MUST be detected; a later save MUST NOT silently overwrite an earlier one.

**Access and confidentiality**

- **FR-028**: Only authenticated users with the planner role MUST be able to view, create, change or delete master data. In this feature every active planner has access to the school's master data.
- **FR-028a**: *Deferred to the multi-school extension* (planners restricted to assigned schools).
- **FR-028b**: *Deferred to the multi-school extension* (visibility and editing of teachers working at several schools).
- **FR-028c**: *Deferred to the multi-school extension* (removing a teacher from one school only).
- **FR-029**: Teacher data (names, availability, workload) MUST NOT appear in logs, technical error messages or diagnostic output; business messages may name entries only to users allowed to see them (Constitution III).

**User accounts**

- **FR-030**: Users MUST log in with a username and password before accessing the application and MUST be able to log out; failed logins MUST NOT reveal whether the username or the password was wrong.
- **FR-031**: During installation, the school and an initial planner account MUST be created so that the first planner can log in.
- **FR-031a**: *Deferred to the multi-school extension* (school assignment of accounts). In this feature every account belongs to the school.
- **FR-032**: Planners MUST be able to create user accounts with a role (planner or teacher), change an account's role, deactivate and reactivate accounts, and reset an account's password; a deactivated account MUST NOT be able to log in. Every user MUST be able to change their own password.
- **FR-032a**: *Deferred to the multi-school extension* (managing only accounts of one's own schools).
- **FR-033**: The system MUST prevent deactivating or downgrading a planner if this would leave the installation without an active planner.
- **FR-034**: Passwords MUST have at least 10 characters and MUST never be shown or stored in readable form.
- **FR-035**: Accounts and roles MUST be kept independent of how a user proves their identity, so that single sign-on via an external identity provider can be added later without re-creating accounts or re-assigning roles.

**Preparation for multiple schools**

- **FR-036**: All master data (rooms, room features, time grid, subjects, class levels, classes, teachers) and all user accounts MUST be recorded as belonging to the school, so that a later extension can add further schools, school-scoped access for planners, teachers working at several schools and commute times without re-entering or manually re-assigning existing data.

**Interface language**

- **FR-037**: The system MUST offer the user interface in German and in English.
- **FR-038**: Every logged-in user MUST be able to switch the interface language from every page with a single action; the current page MUST then be shown again in the chosen language. If the page has unsaved changes, the system MUST first warn that they will be lost and let the user cancel the switch; unsaved changes are not carried over. The warning relies on the browser running the application's scripts; with scripts disabled the switch still works but without the warning (Constitution, Technology & Architecture Constraints).
- **FR-039**: The system MUST store the language a logged-in user chooses with their account and MUST apply it after every later login, on any browser or device.
- **FR-040**: Users who are not logged in MUST be able to switch the language on the login page; this choice MUST be remembered in that browser for later visits of the login page.
- **FR-041**: When the user has not chosen a language yet (neither for the account nor in the browser), the system MUST show the browser's preferred language if it is German or English, and German otherwise; the user can switch at any time (FR-038, FR-040). The language remembered in the browser only decides how the login page is shown; after login without a saved or actively chosen language (FR-042) the browser's preferred language applies.
- **FR-042**: After login, the account's saved language MUST take precedence. If the account has no saved language yet, a language the user actively chose on the login page during this visit, before submitting the login form, MUST be used and saved for the account; without such a choice the browser's preferred language (FR-041) is shown and nothing is saved. A language remembered in the browser from an earlier visit or another user is never saved to an account.
- **FR-043**: All interface texts MUST appear in the selected language, including navigation, page titles, headings, field labels, buttons, hints, confirmation pages, validation messages, business messages (e.g., deletion blocked, version conflict, access denied), the readiness overview, weekday names, role and slot-kind names, and the generic message for technical errors.
- **FR-044**: Data entered by users MUST be shown exactly as entered, regardless of the selected language.
- **FR-045**: No page may mix languages in its interface texts; every interface text MUST exist in both languages.
- **FR-046**: Each page MUST state its language so that browsers and assistive technologies (e.g., screen readers) can handle it correctly.
- **FR-047**: The language selection MUST NOT change stored master data, validation rules or access rights; it only changes how the interface is presented.
- **FR-048**: The confidentiality rules MUST hold in both languages: business messages show only data the user may see, technical error messages show only the generic text and the error reference, and logs contain no confidential data (FR-029, Constitution III).

### Key Entities

- **School**: The school whose timetables are planned; exactly one per installation in this feature. Has a name and owns rooms, a time grid, subjects, class levels, classes and teachers.
- **Room Feature**: A named property or equipment a room may offer (e.g., "chemistry lab"). Used to match subjects to suitable rooms.
- **Room**: A place within the school where lessons are held. Identified by an identifier unique within the school; offers zero or more room features.
- **Time Grid**: The weekly structure of the school: teaching days and an ordered list of time slots with start and end times, each either a teaching slot or a break.
- **Time Slot**: One entry in the time grid (e.g., "3rd period, 09:55–10:40"); the unit in which lessons are scheduled.
- **Subject**: Something that is taught (e.g., Mathematics). Has a name and zero or more required room features.
- **Class Level**: A grade (e.g., "6") with a curriculum: a list of subjects and the number of periods per week for each.
- **Class**: A group of students that receives the curriculum of its class level. Identified by level + suffix (e.g., "6b"); has a priority weight.
- **Teacher**: A person who teaches at the school. Has a name, a unique short code, qualified subjects, a weekly teaching load and unavailable time slots.
- **User Account**: A person who may log in. Has a unique username, a role (planner or teacher), an active/deactivated status, login credentials and an optional preferred interface language set by the user (FR-039); belongs to the school (FR-036).
- **Interface Language**: One of the supported languages, German or English; determines how all interface texts are shown (FR-037).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A planner can enter the complete master data of a typical school (40 rooms, 15 subjects, 6 class levels, 25 classes, 60 teachers, a 5-day grid with 10 slots) within one working day.
- **SC-002**: A planner can create a class level with a full curriculum of 12 subjects in under 5 minutes.
- **SC-003**: 100% of invalid entries covered by the validation rules (duplicate identifiers, overlapping slots, invalid times, excessive teacher load) are rejected with a message that names the problem.
- **SC-004**: The readiness overview detects 100% of the inconsistencies listed in FR-024 in a seeded test data set.
- **SC-005**: 90% of first-time planners can set up rooms, a time grid and one class without help or documentation.
- **SC-006**: 0 master data records are visible to users without the planner role.
- **SC-007**: All master data saved in one session is available unchanged in the next session (0 data loss).
- **SC-008**: A user can switch the interface language from any page in one action; the page is shown again in the new language within the general response time of every page (< 1 s, SC-001/R15) and at most 2 seconds.
- **SC-009**: In a walk-through of every page and every message in both languages, 0 interface texts appear in the wrong language.
- **SC-010**: After choosing a language, 100% of later logins of that user (including from another device) show the interface in the chosen language without a further choice; a first-time visitor whose browser prefers English sees the login page in English without any action.
- **SC-011**: Switching the language changes 0 stored master data records.

## Assumptions

- The application runs as a central server (in the school network or hosted) that several planners use concurrently via a web browser; teachers will access it later through the same server.
- The primary user of this feature is the school planner. Teachers only view timetables (separate feature) and have no access to master data.
- One installation manages exactly one school. Multiple schools per installation (school groups, planners restricted to their schools, teachers at several schools with commute times, managing only accounts of one's own schools) are expected to be introduced by a later feature; FR-036 ensures existing data can be kept when that happens. The previously clarified multi-school rules (FR-019, FR-020, FR-028a–c, FR-031a, FR-032a) are the starting point for that extension.
- "Hours" means periods (lessons) per week, not clock hours.
- All classes of a level receive the same curriculum. Per-class deviations (e.g., elective languages, split groups) are out of scope for this feature but are expected to be introduced by a later feature; the master data must be structured so that this can be added without re-entering existing class or class level data.
- Teacher preferences that may be traded off (soft constraints such as "prefer no first period") are out of scope; all teacher constraints in this feature are hard constraints. Optimization inputs recorded here are the class priority weights (FR-015) and the teachers' weekly loads as targets to reach (FR-018).
- Creating, editing and publishing actual timetables (assignments of lessons to slots, rooms and teachers) is out of scope; this feature provides the input data for them. This includes fixing individual lessons in advance (e.g., "Sport 6a always Tuesday 3rd/4th period"), which comes with the later timetable editing feature.
- Import from external school administration systems is out of scope; data is entered manually.
- No access management exists yet; this feature provides the built-in login and account management (FR-030 – FR-035). Single sign-on is out of scope but prepared for (FR-035). Teacher accounts can be created but have no screens yet.
- The interface is offered in German and English (User Story 6); German remains the reference language and English texts are added for every German text. More languages are expected later, so adding one should only require providing its texts. Every user chooses their own language; there is no installation-wide language setting. Number, time and date formats (e.g., "08:00–08:45") stay the same in both languages; logs, technical identifiers and documents are not translated.
- Browsers run the application's small static scripts; they are only used for the unsaved-changes warning, everything else works without them.
