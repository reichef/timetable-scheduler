# Feature Specification: Scheduling Master Data Management

**Feature Branch**: `001-scheduling-master-data`

**Created**: 2026-10-08

**Status**: Draft

**Input**: User description: "As a user, i want to be able to create all information necessary for the scheduling. Define a School in which a subject is held; a room in the school with an identificator; a subject with a name and optional constraints/properties (e.g., chemistry only in a chemistry room with certain equipment); a class level with the subjects the level is expected to be taught, the number of hours per subject and an identifier (like 6th level); teachers with a name as identifier, the subjects they are able to teach, the amount of hours they teach and other teacher-specific constraints (e.g., a teacher teaches in different schools and requires commute time); classes which have a class level and a name, identified by class level + running identifier (5a, 5b, 6a, ...); the time table itself with the time slots (from time to time), the days, subjects, classes, rooms and teachers. Consider the purpose of the application defined in docs/APP_CONTEXT.md"

## Context

The application exists to generate Pareto-optimal school timetables: every class's timetable should be filled as completely as possible without ever violating a hard constraint, and the planner compares trade-offs between alternative solutions. The scheduler can only do this if it knows *what* has to be scheduled (classes and their required subject hours), *who* can teach it (teachers and their qualifications and limits), *where* it can take place (rooms and their equipment) and *when* (the weekly time grid). This feature lets the school planner capture, maintain and check this information. Generating, editing and publishing timetables are separate features.

## Clarifications

### Session 2026-10-08

- Q: Where and for whom does the application run — a server accessed by several people via browser, or locally on one computer for a single planner? → A: Central server (in the school network or hosted), used concurrently by several planners via browser, later also by teachers, each with a login.
- Q: Where do login and user accounts come from — a simple login built into this feature, or single sign-on via an existing school account? → A: Built-in login with account management by planners now (create, deactivate, reset password); prepared so that single sign-on via an external identity provider can be added later.
- Q: How should a teacher's weekly load apply when scheduling — upper limit, exact target, or range? → A: Hard upper limit; reaching the load is an optimization objective that is part of the Pareto trade-off.
- Q: May planners access all schools of the installation or only the schools assigned to them? → A: Only assigned schools (one or more); teachers working at several schools are visible to every planner responsible for one of those schools.
- Q: Should planners already be able to fix individual lessons in this feature that the scheduler must not move? → A: No; fixed lessons come with the later feature for editing timetables.
- Q: Whose user accounts may a planner manage (create, deactivate, reset passwords)? → A: Only accounts whose schools are all assigned to the planner; teacher accounts are also assigned to schools and follow the same rule.
- Q: May user accounts exist without an assigned school, and if so, what may they do? → A: No; the first school is created together with the initial planner during installation, so every account always has at least one school.
- Q: What happens when a planner deletes a teacher who also teaches at schools not assigned to the planner? → A: Full deletion only if all of the teacher's schools are assigned to the planner; otherwise the planner can only remove the teacher from their own schools, together with the qualifications, unavailabilities and commute times related to those schools.
- Q: What happens when a planner shrinks the time grid (removes slots or teaching days) so that a teacher's weekly load exceeds the new limit? → A: The change is saved; excessive loads appear as a readiness issue that must be fixed before scheduling; unavailabilities and day exclusions for removed days are kept but ignored.
- Q: What happens to teacher unavailabilities when a time slot is removed? → A: They are removed with the slot; the planner sees their number before confirming.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Set up schools, rooms and the weekly time grid (Priority: P1)

A planner registers their school (or several schools of the same group), records every room with a unique identifier and the equipment or features it offers (e.g., "chemistry lab", "gym", "computers"), and defines the weekly time grid: the teaching days and the ordered list of time slots per day with start and end times, including breaks between slots.

**Why this priority**: Rooms and the time grid are the physical and temporal frame every other piece of scheduling information refers to. Without them nothing can be scheduled.

**Independent Test**: Create a school, add three rooms with different features, define a Monday–Friday grid with 8 slots and two breaks, then reopen the data in a new session and confirm everything is shown unchanged.

**Acceptance Scenarios**:

1. **Given** a planner assigned to "Gymnasium Nord", **When** the planner creates a further school named "Realschule Süd", **Then** the school appears in their school list, they are assigned to it, and it can be selected for further data entry.
2. **Given** a school exists, **When** the planner adds room "B-104" with the feature "chemistry lab", **Then** the room is listed for that school with its feature.
3. **Given** room "B-104" already exists in a school, **When** the planner adds another room "B-104" to the same school, **Then** the system rejects the entry and explains that the identifier is already in use.
4. **Given** a school exists, **When** the planner defines teaching days Monday–Friday and slots "1st period 08:00–08:45", "2nd period 08:50–09:35", a break 09:35–09:55 and further periods, **Then** the grid is shown in chronological order with breaks visibly distinguished from teaching slots.
5. **Given** a slot 08:00–08:45 exists, **When** the planner adds a slot 08:30–09:15 on the same day, **Then** the system rejects it because slots must not overlap.

---

### User Story 2 - Define subjects, class levels and classes (Priority: P2)

A planner defines the subjects taught at the school, optionally stating which room features a subject requires (e.g., Chemistry requires "chemistry lab"). They define class levels (e.g., "Level 6") together with the curriculum: which subjects a class of this level must receive and how many periods per week each. Finally they create the actual classes of each level, named by the level plus a running suffix (5a, 5b, 5c, 6a, ...), and optionally give a class a priority weight that tells the scheduler how important it is to fill this class's timetable completely.

**Why this priority**: The curriculum per class is the demand side of the scheduling problem; it defines what "a fully filled timetable" means for each class.

**Independent Test**: Create subjects Mathematics, German and Chemistry (requiring "chemistry lab"), create Level 6 with Mathematics 4, German 4 and Chemistry 2 periods per week, create classes 6a and 6b, and confirm both classes show the 10 required periods inherited from Level 6.

**Acceptance Scenarios**:

1. **Given** a school exists, **When** the planner creates subject "Chemistry" with required room feature "chemistry lab", **Then** the subject is listed with that requirement.
2. **Given** subjects exist, **When** the planner creates class level "6" with Mathematics 4 and German 4 periods per week, **Then** the level shows a weekly total of 8 required periods.
3. **Given** level "6" exists with classes 6a and 6b, **When** the planner adds a new class to level 6, **Then** the system proposes the next free suffix "c" and the class is identified as "6c".
4. **Given** class "6a" exists in a school, **When** the planner tries to create another "6a" in the same school, **Then** the system rejects it as a duplicate.
5. **Given** level "6" requires 30 periods per week but the school's time grid only offers 28 teaching slots per week, **When** the planner saves the level, **Then** the system saves it but warns that the curriculum cannot fit into the weekly grid.

---

### User Story 3 - Define teachers and their constraints (Priority: P3)

A planner records each teacher with their name, the subjects they are qualified to teach, their weekly teaching hours, the schools they teach at, the times they are unavailable, and—for teachers working at more than one school—the commute time needed between those schools.

**Why this priority**: Teachers are the supply side of the scheduling problem and the source of most hard constraints (no double booking, availability, commuting).

**Independent Test**: Create teacher "Anna Schmidt" qualified for Mathematics and Chemistry with 24 weekly periods, unavailable Friday afternoons, teaching at "Gymnasium Nord" and "Realschule Süd" with 30 minutes commute; reopen and confirm all values.

**Acceptance Scenarios**:

1. **Given** subjects exist, **When** the planner creates teacher "Anna Schmidt" with subjects Mathematics and Chemistry and 24 weekly periods, **Then** the teacher is listed with these qualifications and hours.
2. **Given** a teacher "Anna Schmidt" exists, **When** the planner creates another teacher with the same name, **Then** the system requires a distinguishing short code (e.g., "SCHA" vs "SCHM") so that every teacher remains uniquely identifiable.
3. **Given** a teacher is assigned to two schools, **When** the planner enters a commute time of 30 minutes between them, **Then** the commute time is stored for that teacher and that pair of schools.
4. **Given** a teacher exists, **When** the planner marks Friday slots 6–8 as unavailable, **Then** these slots are shown as blocked for that teacher.
5. **Given** a teacher is assigned to two schools, **When** the planner saves without a commute time, **Then** the system asks for the commute time (0 is allowed) before the teacher counts as complete.
6. **Given** "Anna Schmidt" teaches at "Gymnasium Nord" and "Realschule Süd", **When** a planner assigned only to "Gymnasium Nord" removes her, **Then** she is removed from "Gymnasium Nord" with her qualifications and unavailabilities there and the commute time between both schools, and remains unchanged at "Realschule Süd".

---

### User Story 4 - Check readiness of the data for scheduling (Priority: P4)

Before starting a scheduling run, the planner opens an overview that reports whether the entered data is complete and consistent enough to schedule, and which gaps would prevent classes from being fully filled.

**Why this priority**: The scheduler can only produce good solutions from consistent input. Spotting gaps early saves failed or poor scheduling runs, but the data entry itself (P1–P3) must exist first.

**Independent Test**: Seed data in which Chemistry is required but no room has the "chemistry lab" feature and no teacher is qualified for Physics, open the overview and confirm both problems are reported with a reference to the affected entries.

**Acceptance Scenarios**:

1. **Given** a subject requires a room feature that no room of the school offers, **When** the planner opens the readiness overview, **Then** the subject is listed as unschedulable with the missing feature named.
2. **Given** classes require 40 periods of Mathematics per week and qualified teachers offer only 30, **When** the planner opens the overview, **Then** a capacity shortfall of 10 periods for Mathematics is reported.
3. **Given** all data is consistent, **When** the planner opens the overview, **Then** it states that no blocking issues were found.

---

### User Story 5 - Log in and manage accounts and school access (Priority: P1)

Users log in with username and password. Planners create accounts for colleagues (planner or teacher), assign planner accounts to their schools, deactivate accounts and reset passwords. Each planner only sees the schools assigned to them.

**Why this priority**: The data is confidential (Constitution III); no other story may be used without login and school-scoped access. It is listed last only to keep the numbering of the data stories stable.

**Independent Test**: Install with first school "Gymnasium Nord"; as the initial planner create a second planner assigned to it and a third planner assigned to a new school "Realschule Süd"; log in as the third planner and confirm "Gymnasium Nord" is neither listed nor reachable via its link.

**Acceptance Scenarios**:

1. **Given** a fresh installation with first school "Gymnasium Nord", **When** the initial planner logs in with the credentials set during installation, **Then** they see exactly that school, without any rooms, subjects, classes or teachers yet.
2. **Given** a login attempt with a wrong password, **When** it is submitted, **Then** access is denied with a message that does not reveal whether username or password was wrong.
3. **Given** a planner assigned to "Gymnasium Nord", **When** they create planner account "mueller" assigned to "Gymnasium Nord", **Then** "mueller" can log in and sees exactly that school.
4. **Given** an account was deactivated, **When** its owner tries to log in, **Then** the login is refused.
5. **Given** planner "mueller" is the only active planner of a school, **When** someone tries to deactivate or unassign "mueller", **Then** the action is rejected.
6. **Given** planner "weber" is assigned to "Gymnasium Nord" and "Realschule Süd", **When** a planner assigned only to "Gymnasium Nord" opens the account list, **Then** "weber" is not listed and cannot be deactivated or have their password reset.

---

### Edge Cases

- A subject, room feature, class level, room or school is deleted while other data still refers to it: the system blocks the deletion and lists the dependent entries.
- A class level's curriculum is changed after classes exist: all classes of the level reflect the change.
- A teacher's weekly load exceeds the number of teaching slots per week of the teacher's schools combined when the teacher is saved: the entry is rejected (FR-018).
- A planner removes slots or teaching days so that an existing teacher's weekly load now exceeds that limit: the grid change is saved, the teacher is reported in the readiness overview, and the load must be corrected before scheduling. Unavailabilities and day exclusions referring to removed days are kept but ignored. Removing a time slot also removes the teachers' unavailabilities entered for that slot; before confirming, the planner is told how many unavailabilities will be removed.
- A slot whose end time is not after its start time, or a slot outside the teaching days: rejected.
- A teacher is qualified for no subject or has 0 weekly hours: allowed (e.g., on leave) but reported in the readiness overview.
- Two users edit the same entry at the same time: the later save must not silently overwrite the earlier one; the user is informed and can reload.
- A user without the planner role tries to view or change master data: access is denied and no data is revealed.
- A planner tries to deactivate their own account while being the only active planner of a school: rejected (FR-033).
- A school is deleted that is the only school of some user account: the deletion is blocked and the affected accounts are listed (FR-031a, FR-023).
- A planner opens a school, class or room of a school they are not assigned to (e.g., via a copied link): access is denied and no data is revealed (FR-028a).

## Requirements *(mandatory)*

### Functional Requirements

**Schools**

- **FR-001**: Planners MUST be able to create, view, rename and delete schools; school names MUST be unique within the installation.

**Rooms**

- **FR-002**: Planners MUST be able to create, view, edit and delete rooms within a school; each room MUST have an identifier that is unique within its school.
- **FR-003**: Planners MUST be able to maintain a list of room features (e.g., "chemistry lab", "gym") and assign any number of them to a room.

**Time grid**

- **FR-004**: Planners MUST be able to define, per school, the teaching days of the week.
- **FR-005**: Planners MUST be able to define, per school, an ordered list of time slots with a start and end time; each slot MUST be marked either as a teaching slot or as a break.
- **FR-006**: The system MUST reject slots whose end time is not after their start time and slots that overlap another slot of the same school.
- **FR-007**: The same slot structure MUST apply to all teaching days of a school unless the planner removes individual slots for a specific day (e.g., a shorter Friday).

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
- **FR-018**: Planners MUST be able to record a teacher's weekly teaching load in periods; the value MUST NOT exceed the number of teaching slots per week of the teacher's schools combined. The load is a hard upper limit for scheduling; reaching it is an optimization objective that the scheduler includes in the Pareto trade-off (alongside how completely classes are filled).
- **FR-019**: Planners MUST be able to assign a teacher to one or more schools.
- **FR-020**: For teachers assigned to more than one school, planners MUST be able to record the commute time in minutes for each pair of those schools.
- **FR-021**: Planners MUST be able to mark individual time slots (per day) as unavailable for a teacher.

**Constraint semantics**

- **FR-022**: The following MUST be recorded as hard constraints that the scheduler is never allowed to violate: required room features of a subject (FR-009), teacher qualifications (FR-017), teacher weekly load as an upper limit (FR-018), teacher unavailability (FR-021), and teacher commute times between schools (FR-020).

**Integrity and readiness**

- **FR-023**: The system MUST prevent deletion of any entry that other entries depend on and MUST list the dependent entries to the planner.
- **FR-024**: The system MUST provide a readiness overview per school that reports at least: subjects whose required room features no room offers; subjects required by a class level for which no qualified teacher exists; subjects where required weekly periods across all classes exceed the weekly load of all qualified teachers; class levels whose weekly periods exceed the school's teaching slots; teachers with no qualifications or zero load; teachers whose weekly load exceeds the teaching slots per week of their schools combined (e.g., after the time grid was reduced); teachers at multiple schools without commute time.
- **FR-025**: Warnings from the readiness overview MUST NOT prevent saving data; only the validation rules in FR-001 to FR-021 block saving.
- **FR-026**: All master data MUST be stored persistently so that it is available unchanged in later sessions.
- **FR-027**: Concurrent edits to the same entry MUST be detected; a later save MUST NOT silently overwrite an earlier one.

**Access and confidentiality**

- **FR-028**: Only authenticated users with the planner role MUST be able to view, create, change or delete master data.
- **FR-028a**: Each planner account MUST be assigned to one or more schools and MUST only see and change data of those schools. A planner who creates a school is assigned to it automatically.
- **FR-028b**: A teacher MUST be visible to every planner assigned to at least one of the teacher's schools. Such a planner MAY change the teacher's general data (name, short code, weekly load) and the teacher's data related to the planner's own schools (qualifications for their subjects, unavailability in their time slots, commute times involving them); data related to other schools is shown read-only or hidden.
- **FR-028c**: A planner MUST only be able to delete a teacher if all of the teacher's schools are assigned to the planner. Otherwise the planner MUST be able to remove the teacher from their own schools, which also removes the teacher's qualifications, unavailabilities and commute times related to those schools; the teacher remains unchanged for the other schools.
- **FR-029**: Teacher data (names, availability, workload, school assignments) MUST NOT appear in logs, error messages or diagnostic output.

**User accounts**

- **FR-030**: Users MUST log in with a username and password before accessing the application and MUST be able to log out; failed logins MUST NOT reveal whether the username or the password was wrong.
- **FR-031**: During installation, the first school and an initial planner account assigned to it MUST be created so that the first planner can log in.
- **FR-031a**: Every user account MUST be assigned to at least one school at all times; creating an account without a school or removing an account's last school MUST be rejected.
- **FR-032**: Planners MUST be able to create user accounts with a role (planner or teacher), assign accounts of both roles to their own schools, deactivate and reactivate accounts, and reset an account's password; a deactivated account MUST NOT be able to log in.
- **FR-032a**: A planner MUST only be able to manage (change role or schools, deactivate, reactivate, reset password) accounts whose assigned schools are all assigned to the planner as well; other accounts are not listed. Changing their own password is always possible for every user.
- **FR-033**: The system MUST prevent deactivating, downgrading or unassigning a planner if this would leave a school without an active planner.
- **FR-034**: Passwords MUST have at least 10 characters and MUST never be shown or stored in readable form.
- **FR-035**: Accounts and roles MUST be kept independent of how a user proves their identity, so that single sign-on via an external identity provider can be added later without re-creating accounts or re-assigning roles.

### Key Entities

- **School**: A location at which lessons take place. Has a unique name, owns rooms, a time grid, subjects, class levels and classes.
- **Room Feature**: A named property or equipment a room may offer (e.g., "chemistry lab"). Used to match subjects to suitable rooms.
- **Room**: A place within a school where lessons are held. Identified by an identifier unique within the school; offers zero or more room features.
- **Time Grid**: The weekly structure of a school: teaching days and an ordered list of time slots with start and end times, each either a teaching slot or a break.
- **Time Slot**: One entry in the time grid (e.g., "3rd period, 09:55–10:40"); the unit in which lessons are scheduled.
- **Subject**: Something that is taught (e.g., Mathematics). Has a name and zero or more required room features.
- **Class Level**: A grade (e.g., "6") with a curriculum: a list of subjects and the number of periods per week for each.
- **Class**: A group of students that receives the curriculum of its class level. Identified by level + suffix (e.g., "6b"); has a priority weight.
- **Teacher**: A person who teaches. Has a name, a unique short code, qualified subjects, a weekly teaching load, one or more schools, unavailable time slots and commute times between their schools.
- **Commute Time**: The minutes a specific teacher needs to travel between two of their schools.
- **User Account**: A person who may log in. Has a unique username, a role (planner or teacher), an active/deactivated status, login credentials and the schools it is assigned to (for planners: the schools whose data they manage; for teachers: the schools they belong to).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A planner can enter the complete master data of a typical school (40 rooms, 15 subjects, 6 class levels, 25 classes, 60 teachers, a 5-day grid with 10 slots) within one working day.
- **SC-002**: A planner can create a class level with a full curriculum of 12 subjects in under 5 minutes.
- **SC-003**: 100% of invalid entries covered by the validation rules (duplicate identifiers, overlapping slots, invalid times, excessive teacher load) are rejected with a message that names the problem.
- **SC-004**: The readiness overview detects 100% of the inconsistencies listed in FR-024 in a seeded test data set.
- **SC-005**: 90% of first-time planners can create a school with rooms, a time grid and one class without help or documentation.
- **SC-006**: 0 master data records are visible to users without the planner role or to planners not assigned to the school the data belongs to.
- **SC-007**: All master data saved in one session is available unchanged in the next session (0 data loss).

## Assumptions

- The application runs as a central server (in the school network or hosted) that several planners use concurrently via a web browser; teachers will access it later through the same server.
- The primary user of this feature is the school planner. Teachers only view timetables (separate feature) and have no access to master data.
- One installation may contain several schools (e.g., a school group) so that teachers working at more than one school can be modelled; planners only access the schools assigned to them (FR-028a).
- Subjects, class levels, room features and time grids are defined per school, because curricula and bell schedules differ between schools.
- "Hours" means periods (lessons) per week, not clock hours.
- All classes of a level receive the same curriculum. Per-class deviations (e.g., elective languages, split groups) are out of scope for this feature but are expected to be introduced by a later feature; the master data must be structured so that this can be added without re-entering existing class or class level data.
- Teacher preferences that may be traded off (soft constraints such as "prefer no first period") are out of scope; all teacher constraints in this feature are hard constraints. Optimization inputs recorded here are the class priority weights (FR-015) and the teachers' weekly loads as targets to reach (FR-018).
- Creating, editing and publishing actual timetables (assignments of lessons to slots, rooms and teachers) is out of scope; this feature provides the input data for them. This includes fixing individual lessons in advance (e.g., "Sport 6a always Tuesday 3rd/4th period"), which comes with the later timetable editing feature.
- Import from external school administration systems is out of scope; data is entered manually.
- No access management exists yet; this feature provides the built-in login and account management (FR-030 – FR-035). Single sign-on is out of scope but prepared for (FR-035). Teacher accounts can be created but have no screens yet.
