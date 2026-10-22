# Test Set: Fictitious G9 Gymnasium (Baden-Württemberg style), Grades 5–10

> **TEST DATA — NOT A REAL SCHOOL.** Every school, teacher, class and room in this set is invented. The subject
> table imitates the structure of a Baden-Württemberg G9 Gymnasium but is an **assumption**, not the official
> *Kontingentstundentafel*; schools distribute the contingents themselves. The set exists only to check that
> timetable generation works in principle on a school of realistic size. A realistic test with a teacher's data
> follows in a later feature.

Used by the extended validation of feature 002 (tasks T110–T117). Every name carries a test marker so that the data
cannot be mistaken for real data, in the database, in the user interface or in screenshots.

## Simplifications (accepted for this test)

- Pupil numbers (≥ 25 per class) and room capacities are not modelled by the application; each class is one group.
- Groups that a real school teaches in parallel across classes are one subject per class:
  *Religion/Ethik* (instead of ev./kath./Ethik), *2. Fremdsprache* (instead of Französisch/Latein),
  *Profilfach* (instead of NwT/Spanisch/Sport profile).
- No A/B weeks; every period takes place every week.
- All 18 classes have a home room (in reality often only the lower grades).

## School and time grid

- School: **TESTDATEN – Testgymnasium Musterstadt**
- Teaching days: Monday–Friday

| Slot | Time | Kind |
|------|------|------|
| 1. Stunde | 07:45–08:30 | teaching |
| 2. Stunde | 08:35–09:20 | teaching |
| Große Pause | 09:20–09:40 | break |
| 3. Stunde | 09:40–10:25 | teaching |
| 4. Stunde | 10:30–11:15 | teaching |
| Pause | 11:15–11:30 | break |
| 5. Stunde | 11:30–12:15 | teaching |
| 6. Stunde | 12:20–13:05 | teaching |
| Mittagspause | 13:05–13:50 | break |
| 7. Stunde | 13:50–14:35 | teaching |
| 8. Stunde | 14:40–15:25 | teaching |
| 9. Stunde | 15:30–16:15 | teaching |
| 10. Stunde | 16:20–17:05 | teaching |

10 teaching slots per day, 50 per week.

## Room features and rooms (31)

| Feature | Rooms |
|---------|-------|
| Naturwissenschaftsraum | TEST-NW1 … TEST-NW4 |
| Sporthalle | TEST-SH1, TEST-SH2 |
| Musikraum | TEST-MU1, TEST-MU2 |
| Kunstraum | TEST-KU1, TEST-KU2 |
| Computerraum | TEST-PC1 |
| — (classrooms) | TEST-R101 … TEST-R118 (home rooms of 5a … 10c in this order), TEST-R119, TEST-R120 |

## Subjects (18)

| Subject | Required feature |
|---------|------------------|
| Deutsch, Mathematik, Englisch, 2. Fremdsprache, Geschichte, Geographie, Gemeinschaftskunde, Religion/Ethik | — (home room) |
| BNT (Biologie, Naturwissenschaft und Technik), Biologie, Physik, Chemie, Profilfach | Naturwissenschaftsraum |
| Sport | Sporthalle |
| Musik | Musikraum |
| Bildende Kunst | Kunstraum |
| Medienbildung, Informatik | Computerraum |

## Weekly periods per class level (assumption)

| Subject | 5 | 6 | 7 | 8 | 9 | 10 |
|---------|---|---|---|---|---|----|
| Deutsch | 5 | 4 | 4 | 4 | 4 | 4 |
| Mathematik | 5 | 4 | 4 | 4 | 4 | 4 |
| Englisch | 5 | 4 | 4 | 3 | 3 | 3 |
| 2. Fremdsprache | – | 4 | 4 | 3 | 3 | 3 |
| Profilfach | – | – | – | 4 | 4 | 4 |
| BNT | 4 | 3 | – | – | – | – |
| Biologie | – | – | 2 | 2 | 2 | 2 |
| Physik | – | – | 2 | 2 | 2 | 2 |
| Chemie | – | – | – | 2 | 2 | 2 |
| Geschichte | – | 2 | 2 | 2 | 2 | 2 |
| Geographie | 2 | 2 | 1 | 1 | 1 | 1 |
| Gemeinschaftskunde | – | – | – | – | 1 | 2 |
| Religion/Ethik | 2 | 2 | 2 | 2 | 2 | 2 |
| Sport | 3 | 3 | 2 | 2 | 2 | 2 |
| Musik | 2 | 2 | 2 | 1 | 1 | 1 |
| Bildende Kunst | 2 | 2 | 2 | 1 | 1 | – |
| Medienbildung | 1 | – | – | – | – | – |
| Informatik | – | – | 1 | – | – | – |
| **Total** | **31** | **32** | **32** | **33** | **34** | **34** |

Classes: 5a–5c, 6a–6c, …, 10a–10c (18 classes, priority weight 10). Total demand: 588 lessons per week.

## Teachers (32)

Names are *Testlehrkraft NN*, short codes *T01*–*T32*. Loads are weekly periods (full time = 25). Total capacity
705 periods, about 20 % above the demand.

| Code | Subjects | Load | | Code | Subjects | Load |
|------|----------|------|-|------|----------|------|
| T01 | Deutsch, Geschichte | 25 | | T17 | 2. Fremdsprache, Gemeinschaftskunde | 25 |
| T02 | Deutsch, Geschichte | 25 | | T18 | 2. Fremdsprache, Deutsch | 15 |
| T03 | Deutsch, Geographie | 25 | | T19 | Biologie, Chemie, BNT | 25 |
| T04 | Deutsch, Englisch | 25 | | T20 | Biologie, BNT, Profilfach | 25 |
| T05 | Deutsch, Religion/Ethik | 18 | | T21 | Physik, Profilfach | 25 |
| T06 | Deutsch, Gemeinschaftskunde | 15 | | T22 | Chemie, Profilfach | 25 |
| T07 | Mathematik, Physik | 25 | | T23 | BNT, Geographie | 18 |
| T08 | Mathematik, Physik | 25 | | T24 | Religion/Ethik, Geschichte | 25 |
| T09 | Mathematik, Chemie | 25 | | T25 | Religion/Ethik, Englisch | 15 |
| T10 | Mathematik, Informatik, Medienbildung | 25 | | T26 | Sport, Biologie | 25 |
| T11 | Mathematik, Biologie | 18 | | T27 | Sport, Geographie | 25 |
| T12 | Mathematik, Geographie | 15 | | T28 | Musik, Deutsch | 25 |
| T13 | Englisch, 2. Fremdsprache | 25 | | T29 | Musik, Mathematik | 15 |
| T14 | Englisch, 2. Fremdsprache | 25 | | T30 | Bildende Kunst, Englisch | 25 |
| T15 | Englisch, Geschichte | 25 | | T31 | Bildende Kunst, Geschichte | 18 |
| T16 | Englisch, Sport | 18 | | T32 | Sport, Religion/Ethik | 15 |

Unavailability (master data): T05 Monday all day; T18 Tuesday 1.–4. Stunde; T25 Friday all day.

## Constraints (20) and teacher fixations (3)

| # | Constraint | Kind |
|---|------------|------|
| 1–2 | School-wide defaults: subject at most 2 periods per day; classes without free periods | soft, medium |
| 3 | All classes: at most 8 periods per day | hard |
| 4–9 | 5a, 5b, 5c, 6a, 6b, 6c: no lessons in the 9. and 10. Stunde | hard |
| 10–12 | 5a, 5b, 5c: BNT as double periods (not across breaks) | soft, medium |
| 13–15 | T06 no lessons Wednesday afternoon (7.–10.); T12 no lessons in the 1. Stunde; T29 no lessons Friday | hard |
| 16–17 | T07, T13: at most 6 periods per day | soft, low |
| 18–19 | T01, T20: at most 2 free periods per day | soft, medium |
| 20 | Sport (all classes) not in the 1. Stunde | soft, low |

Teacher fixations (class teachers): Deutsch 5a → T01, Mathematik 5b → T07, Englisch 5c → T13.

## Expected results of a generation (default time limit 10 minutes)

Asserted by the scenario test:

- The readiness check reports no blocking issue; the start is not refused.
- The first alternative meeting the minimum share of placed lessons (default 90 %) is available within 2 minutes;
  no alternative places fewer than 90 % of the best alternative's lessons, none places no lesson at all (SC-001,
  FR-007a; there is no fixed number of alternatives).
- No alternative violates a hard constraint, a fixation or an unavailability (re-checked independently); 5th and 6th
  graders have no lessons in the 9. and 10. Stunde; lab, sports, music, art and computer subjects take place only in
  rooms with the feature, all other subjects in the home room.
- No alternative is dominated by another (SC-003).

Recorded, not asserted (they decide whether the specification needs to be refined):

- time to the first alternative, number of alternatives, objective values of each;
- best class fill and the number of alternatives placing at least 90 % of the lessons ("useful" alternatives);
- unplaced lessons per class of the best alternative.

## Variant: teacher shortage

> **TEST DATA — NOT A REAL SCHOOL.** Same fictitious school, school name
> **TESTDATEN – Testgymnasium Musterstadt (Lehrkräftemangel)**.

Everything above stays the same — grid, rooms, subjects, weekly periods, classes, constraints, fixations and
unavailability — except the teachers (tasks T118–T121):

| Change | Teachers |
|--------|----------|
| removed | T16 (Englisch, Sport), T27 (Sport, Geographie), T29 (Musik, Mathematik), T31 (Bildende Kunst, Geschichte) |
| load reduced to 18 | T07, T08, T21, T28, T30 |
| load reduced to 15 | T19, T22 |

28 teachers with 574 periods of capacity face a demand of 588 lessons. The shortage is certain in every timetable:

| Subject | Demand | Qualified teachers (max. periods) | At least missing |
|---------|--------|-----------------------------------|------------------|
| Musik | 27 | T28 (18) | 9 |
| Bildende Kunst | 24 | T30 (18) | 6 |
| Sport | 42 | T26 (25), T32 (15) | 2 |

Expected results (asserted by the scenario test):

- The readiness check reports the capacity shortfalls of Musik, Bildende Kunst and Sport as warnings, not as blocking
  issues; the generation starts once the warnings are confirmed and ends normally.
- Every alternative leaves at least 17 lessons unplaced and lists them per class; no teacher exceeds the weekly load.
- All hard rules, room rules, fixations and unavailability of the first test set hold; no alternative is dominated.
- No alternative places fewer than 90 % of the best alternative's lessons (FR-007a); the number of alternatives is
  recorded, not asserted (SC-001).

Recorded: number of alternatives and their objective values, unplaced lessons per class and per subject of the best
alternative.
