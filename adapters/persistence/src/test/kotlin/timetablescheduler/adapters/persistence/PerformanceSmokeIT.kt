package timetablescheduler.adapters.persistence

import timetablescheduler.adapters.persistence.account.BcryptPasswordHasher
import timetablescheduler.adapters.persistence.account.ExposedPasswordCredentialRepository
import timetablescheduler.adapters.persistence.account.ExposedUserAccountRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedClassLevelRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedSchoolClassRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedSubjectRepository
import timetablescheduler.adapters.persistence.room.ExposedRoomFeatureRepository
import timetablescheduler.adapters.persistence.room.ExposedRoomRepository
import timetablescheduler.adapters.persistence.scheduling.ExposedChosenTimetableRepository
import timetablescheduler.adapters.persistence.scheduling.ExposedGenerationRepository
import timetablescheduler.adapters.persistence.scheduling.ExposedSchedulingConstraintRepository
import timetablescheduler.adapters.persistence.scheduling.ExposedTeacherFixationRepository
import timetablescheduler.adapters.persistence.school.ExposedSchoolRepository
import timetablescheduler.adapters.persistence.teacher.ExposedTeacherRepository
import timetablescheduler.adapters.persistence.teacher.ExposedTimeSlotUsage
import timetablescheduler.application.MasterDataPorts
import timetablescheduler.application.MasterDataUseCases
import timetablescheduler.application.SchedulingPorts
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.FakeTimetableSolver
import timetablescheduler.application.fakes.ImmediateGenerationExecutor
import timetablescheduler.application.fakes.RecordingGenerationEventLog
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.room.RoomFeature
import timetablescheduler.domain.school.School
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.teacher.Teacher
import timetablescheduler.domain.teacher.Unavailability
import timetablescheduler.domain.validation.ValidationResult
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/** R15 / SC-001: the main pages of a typical school answer within one second each on PostgreSQL. */
class PerformanceSmokeIT : PostgresTestBase() {
    private val schools = ExposedSchoolRepository()
    private val accounts = ExposedUserAccountRepository()
    private val features = ExposedRoomFeatureRepository()
    private val rooms = ExposedRoomRepository()
    private val subjects = ExposedSubjectRepository()
    private val levels = ExposedClassLevelRepository()
    private val classes = ExposedSchoolClassRepository()
    private val teachers = ExposedTeacherRepository()

    private val useCases =
        MasterDataUseCases(
            MasterDataPorts(
                transactions,
                schools,
                accounts,
                ExposedPasswordCredentialRepository(),
                BcryptPasswordHasher(),
                rooms,
                features,
                ExposedTimeSlotUsage(),
                subjects,
                levels,
                classes,
                teachers,
            ),
            SchedulingPorts(
                ExposedSchedulingConstraintRepository(),
                ExposedTeacherFixationRepository(),
                ExposedGenerationRepository(),
                ExposedChosenTimetableRepository(),
                FakeTimetableSolver(),
                ImmediateGenerationExecutor(),
                RecordingGenerationEventLog(),
                Clock.systemUTC(),
            ),
        )

    private fun <T> ValidationResult<T>.valid(): T = (this as ValidationResult.Valid).value

    /** 40 rooms, 15 subjects, 6 levels, 25 classes, 60 teachers, a 5-day grid with 10 slots (SC-001). */
    private fun seedTypicalSchool(): Pair<SchoolId, AccountId> {
        val school = School.create("Gymnasium Nord").valid()
        val planner = UserAccount.create("admin", Role.PLANNER, setOf(school.id)).valid()
        val weekdays = DayOfWeek.entries.filter { it <= DayOfWeek.FRIDAY }.toSet()
        val grid =
            (0 until 10).fold(TimeGrid.empty().withTeachingDays(weekdays)) { current, index ->
                val start = LocalTime.of(8, 0).plusMinutes(50L * index)
                current.withSlotAdded("${index + 1}. Stunde", start, start.plusMinutes(45), SlotKind.TEACHING).valid()
            }
        val roomFeatures = (1..5).map { RoomFeature.create(school.id, "Ausstattung $it").valid() }
        val featureIds = roomFeatures.map { it.id }.toSet()
        val schoolRooms = (1..40).map { Room.create(school.id, "R-$it", setOf(roomFeatures[it % 5].id), featureIds).valid() }
        val schoolSubjects = (1..15).map { Subject.create(school.id, "Fach $it", setOf(roomFeatures[it % 5].id), featureIds).valid() }
        val subjectIds = schoolSubjects.map { it.id }.toSet()
        val schoolLevels =
            (5..10).map { grade ->
                val curriculum = schoolSubjects.take(12).map { CurriculumEntry(it.id, 3) }
                ClassLevel
                    .create(school.id, "$grade")
                    .valid()
                    .update("$grade", curriculum, subjectIds)
                    .valid()
            }
        val schoolClasses = (0 until 25).map { index -> SchoolClass.create(schoolLevels[index % 6].id, "${'a' + index / 6}").valid() }
        val schoolTeachers =
            (1..60).map { number ->
                Teacher
                    .create(school.id, "Lehrkraft $number", "L$number", 24, grid.teachingSlotsPerWeek())
                    .valid()
                    .withQualifications(setOf(schoolSubjects[number % 15].id, schoolSubjects[(number + 7) % 15].id), subjectIds)
                    .valid()
                    .withUnavailability(setOf(Unavailability(DayOfWeek.FRIDAY, grid.slots[number % 10].id)), grid)
                    .valid()
            }
        inTransaction {
            schools.insert(school)
            schools.saveTimeGrid(school.id, grid)
            accounts.insert(planner)
            roomFeatures.forEach(features::insert)
            schoolRooms.forEach(rooms::insert)
            schoolSubjects.forEach(subjects::insert)
            schoolLevels.forEach(levels::insert)
            schoolClasses.forEach(classes::insert)
            schoolTeachers.forEach(teachers::insert)
        }
        return school.id to planner.id
    }

    /** Runs [action] once to warm up, then measures a second run in milliseconds. */
    private fun <T> timedMillis(action: () -> UseCaseResult<T>): Long {
        assertIs<UseCaseResult.Success<T>>(action())
        val (result, duration) = measureTimedValue(action)
        assertIs<UseCaseResult.Success<T>>(result)
        return duration.inWholeMilliseconds
    }

    @Test
    fun `dashboard, teacher list and readiness answer within one second for a typical school`() {
        val (schoolId, planner) = seedTypicalSchool()

        val durations =
            mapOf(
                "dashboard" to timedMillis { useCases.schools.dashboard.execute(planner, schoolId) },
                "teacher list" to timedMillis { useCases.teachers.list.execute(planner, schoolId) },
                "readiness" to timedMillis { useCases.readiness.execute(planner, schoolId) },
            )

        durations.forEach { (page, millis) -> assertTrue(millis < 1000, "$page took $millis ms") }
    }
}
