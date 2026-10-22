# Quickstart: Timetable Generation with Planner-Defined Constraints

**Feature**: [spec.md](./spec.md) | **Routes**: [contracts/web-routes.md](./contracts/web-routes.md) | **Data model**: [data-model.md](./data-model.md)

Validation guide proving the feature works end-to-end against a running instance (constitution: UI changes verified on a running instance).

## Prerequisites and start

Same as [feature 001 quickstart](../001-scheduling-master-data/quickstart.md): JDK 21, Docker, `.env` with the `TTS_*` variables, a **fresh database**, then:

```sh
docker compose up -d db
set -a; . ./.env; set +a
./gradlew build            # unit, route, Testcontainers and solver tests
./gradlew :bootstrap:run   # http://localhost:8080 – Flyway applies V1…V6
```

## Test data

Run feature 001 quickstart scenarios 0–14 first (school, rooms incl. chemistry lab, 5-day grid with 6 teaching slots and one break, subjects, class levels 5 and 6 with curricula, classes 5a, 5b, 6a, 6b, teachers incl. Anna Schmidt (SCHA) with Mathematics, unavailabilities). Fix any blocking readiness issues.

## Scenarios

| # | Story | Steps | Expected |
|---|-------|-------|----------|
| 0 | US1/US2 | Give 5a the home room A-101; try to give 5b the same room; open *Bedingungen* | 5a saved; 5b rejected naming 5a; list contains the two school-wide defaults (subject ≤ 2 per day, no free periods for classes), soft, medium, active |
| 1 | US1 | *Stundenplanläufe* → start with default time limit 2 min | Generation page shows "läuft", elapsed time and alternatives found, refreshes itself; after ≤ 2 min status "abgeschlossen" |
| 2 | US1 | Open the result | ≥ 1 alternative; table with class fill, teacher load usage, other soft constraints in %; no row is ≤ another in all columns and < in one |
| 3 | US1 | Open an alternative, select 6b, then teacher SCHA, then the chemistry room | Weekly grids with subject, class, short code, room; break row shown as break; SCHA has no lesson in their unavailable slots; chemistry only in the lab |
| 4 | US1 | Raise a class level's weekly periods above the teaching slots per week (001 readiness `LEVEL_EXCEEDS_GRID`), try to start | Start refused, issue named, link to readiness; undo the change afterwards |
| 4a | US1 | In an alternative open 5a | All lessons of subjects without required features in A-101; Chemistry in the lab |
| 5 | US1 | Start with limit 10 min, press *Stoppen* after first alternative | Status "vorzeitig gestoppt", alternatives kept |
| 6 | US1 | Start while another generation is running (second browser) | Refused with link to the running generation |
| 7 | US2 | *Bedingungen* → new "Klasse: höchstens Stunden pro Tag": 5a, 4, hard; generate | Listed as "Klasse 5a: höchstens 4 Stunden pro Tag – Muss"; no alternative has more than 4 lessons on a day for 5a (unplaced lessons listed instead if needed) |
| 8 | US2 | New soft "Klasse ohne Freistunden" for 6a, weight high; and soft "Doppelstunde" Mathematics 6b | Result shows per alternative the violated soft constraints with counts |
| 9 | US2 | Start with "6a ohne Freistunden" selected as separate objective | Result table has an additional column for it; comparison offers it as axis |
| 10 | US2 | Select 4 soft constraints as separate objectives | `422`: at most 3 |
| 11 | US2 | Constraint with limit 0, or with a slot that is a break | `422` naming the problem |
| 12 | US2 | Teacher fixation Mathematics 6b → SCHA; generate | Every Mathematics lesson of 6b in every alternative is taught by SCHA; other subjects of 6b by teachers chosen by the scheduler |
| 13 | US2 | Fixation with a teacher not qualified for the subject; fixations above a teacher's load | `422` with named problem |
| 14 | US2 | Remove SCHA's Mathematics qualification in master data | Saved; fixation marked invalid; readiness shows `INVALID_TEACHER_FIXATION`; start refused; restore qualification |
| 15 | US2 | Delete class 5a while a constraint refers to it | `409` listing the constraint |
| 16 | US2 | Switch the 5a constraint off, generate | Constraint ignored, still listed as switched off |
| 16a | US2 | Add "Mathematik in 6b höchstens 1 Stunde pro Tag"; generate | 6b Mathematics follows the specific rule, other subjects/classes the school-wide default |
| 17 | US2 | Hard "Klasse: mindestens Stunden pro Tag" 5a = 6 together with hard maximum 4 | Rejected on save (min > max); with max removed and 6 > lessons possible: start refused naming the constraint (FR-017) |
| 18 | US3 | *Vergleichen*, choose axes class fill / teacher load | Sortable table, SVG chart with one point per alternative, points link to alternatives |
| 19 | US3 | Compare two alternatives for 6b | Differing cells highlighted |
| 20 | US3 | Choose an alternative; then start a new generation | *Gewählter Stundenplan* shows it; it stays chosen after the new generation |
| 20a | US3 | Choose an alternative with unplaced lessons | Confirmation page lists unplaced lessons per class; cancel changes nothing; after confirming, *Gewählter Stundenplan* shows them as "nicht verplant" |
| 20b | US3 | Make a teacher unavailable in a slot of the chosen timetable; start with "Vom gewählten Stundenplan ausgehen" | Result has the additional objective "geänderte Stunden"; at least one alternative changes few lessons besides the conflicting one; without a chosen timetable the option is not offered |
| 20c | US3 | Make a teacher unavailable in a slot used by a not-chosen alternative, then try to choose that alternative | Refused; the conflicting lesson and rule are named with a hint to start a new generation; the chosen timetable is unchanged |
| 20d | US3 | Make a teacher unavailable in a slot used by the chosen timetable, open *Gewählter Stundenplan* | Conflicting lesson highlighted with the rule; hint that the timetable no longer fits with a link to a new generation from the chosen timetable; the chosen timetable itself unchanged |
| 20e | US3 | After choosing, create class 5c and reduce Mathematics in level 6 by one period, open *Gewählter Stundenplan* | 5c's lessons listed as "nicht verplant", one Mathematics lesson per level-6 class marked "entfällt", "passt nicht mehr" hint with link; chosen timetable otherwise unchanged; undo both changes |
| 21 | US3 | Delete a teacher used in the chosen timetable | `409` listing "Gewählter Stundenplan" |
| 22 | US4 | Change a teacher's unavailability to a slot used in an alternative | Generation list marks the generation outdated; the alternative highlights the now conflicting lesson |
| 23 | US4 | Delete the generation of the chosen timetable; delete another generation | First refused, second removed after confirmation |
| 24 | US4 | Stop the server during a running generation, start it again | Generation status "fehlgeschlagen", found alternatives still shown |
| 25 | all | Log in as a teacher account, open `/schools/{s}/generations`, `/constraints`, `/timetable` | `403`, no data |
| 26 | all | Switch to English on generation, alternative, comparison and constraint pages | All texts English, names unchanged |
| 27 | all | Inspect the application log after scenarios 1–24 | Only ids, statuses, counts and durations; no names of teachers, classes, subjects, rooms |

## Test school of realistic size (test data)

The fictitious test school of [test-set-bw-gymnasium.md](test-set-bw-gymnasium.md) — **test data, not a real
school** — repeats the generation on 18 classes and 32 teachers:

```sh
./gradlew :bootstrap:testSchoolTest            # container database; test school and teacher-shortage variant (~20 min)

# inspect it in the application, in a separate empty database (never the developer database)
docker compose exec -T db psql -U timetable_owner -d timetable -c "CREATE DATABASE timetable_testschool"
docker compose exec -T db psql -U timetable_owner -d timetable_testschool -c "GRANT USAGE, CREATE ON SCHEMA public TO timetable_app"
set -a; . ./.env; set +a
TTS_DB_URL=jdbc:postgresql://localhost:5432/timetable_testschool ./gradlew :bootstrap:seedTestSchool   # -PtestSchool=understaffed for the variant
TTS_DB_URL=jdbc:postgresql://localhost:5432/timetable_testschool ./gradlew :bootstrap:run
```

## Automated checks

- `./gradlew build` includes: domain tests for `HardConstraintChecker`, `ObjectiveEvaluator`, `ParetoArchive`, `InfeasibilityPreCheck`, every constraint type; solver adapter tests asserting 0 hard violations and non-dominance on seeded data sets (SC-002, SC-003) and full placement for a data set with a known complete solution (SC-004); route tests for every contract row; log confidentiality test (FR-026).
- `./gradlew :adapters:solver:performanceTest` (tagged, not part of `build`): generated SC-001-sized school, first alternative < 2 min, ≥ 3 alternatives within 10 min (or one alternative that fills every class and violates no constraint).
