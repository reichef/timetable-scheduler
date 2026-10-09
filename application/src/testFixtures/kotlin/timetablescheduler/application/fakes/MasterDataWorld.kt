package timetablescheduler.application.fakes

import timetablescheduler.application.MasterDataPorts
import timetablescheduler.application.MasterDataUseCases
import timetablescheduler.application.SchedulingPorts
import timetablescheduler.application.account.InstallationUseCase
import timetablescheduler.application.scheduling.SchedulingProblemBuilder
import timetablescheduler.domain.scheduling.evaluation.ObjectiveEvaluator
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.room.RoomFeature
import timetablescheduler.domain.school.School
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.school.TimeSlot
import timetablescheduler.domain.teacher.Teacher
import timetablescheduler.domain.teacher.Unavailability
import timetablescheduler.domain.validation.ValidationResult
import java.time.DayOfWeek
import java.time.LocalTime

private fun <T> ValidationResult<T>.valid(): T = (this as ValidationResult.Valid).value

/** In-memory installation with the production wiring and helpers to arrange test data directly. */
class MasterDataWorld {
    val transactions = InMemoryTransactionRunner()
    val schools = InMemorySchoolRepository()
    val accounts = InMemoryUserAccountRepository()
    val credentials = InMemoryPasswordCredentialRepository()
    val hasher = PlainTextTestPasswordHasher()
    val rooms = InMemoryRoomRepository()
    val roomFeatures = InMemoryRoomFeatureRepository()
    val teachers = InMemoryTeacherRepository()
    val timeSlotUsage = InMemoryTimeSlotUsage(teachers)
    val subjects = InMemorySubjectRepository()
    val classLevels = InMemoryClassLevelRepository()
    val schoolClasses = InMemorySchoolClassRepository(classLevels)

    val constraints = InMemorySchedulingConstraintRepository()
    val fixations = InMemoryTeacherFixationRepository()
    val generations = InMemoryGenerationRepository()
    val solver = FakeTimetableSolver()
    val executor = ImmediateGenerationExecutor()
    val generationLog = RecordingGenerationEventLog()
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC)

    val ports = MasterDataPorts(
        transactions, schools, accounts, credentials, hasher, rooms, roomFeatures, timeSlotUsage, subjects, classLevels, schoolClasses, teachers,
    )
    val schedulingPorts = SchedulingPorts(constraints, fixations, generations, solver, executor, generationLog, clock)
    val useCases = MasterDataUseCases(ports, schedulingPorts)

    val schoolUseCases get() = useCases.schools
    val roomUseCases get() = useCases.rooms
    val timeGridUseCases get() = useCases.timeGrid

    fun school(name: String): School = School.create(name).valid().also(schools::insert)

    fun account(username: String, role: Role, schoolIds: Set<SchoolId>, password: String = DEFAULT_PASSWORD): UserAccount =
        UserAccount.create(username, role, schoolIds).valid().also {
            accounts.insert(it)
            credentials.save(it.id, hasher.hash(password))
        }

    fun account(username: String, role: Role, schoolId: SchoolId): UserAccount = account(username, role, setOf(schoolId))

    fun planner(username: String, schoolId: SchoolId): UserAccount = account(username, Role.PLANNER, setOf(schoolId))

    fun planner(username: String, schoolIds: Set<SchoolId>): UserAccount = account(username, Role.PLANNER, schoolIds)

    fun assign(account: UserAccount, schoolId: SchoolId) {
        val stored = accounts.findById(account.id)!!
        accounts.update(stored.copy(schoolIds = stored.schoolIds + schoolId))
    }

    fun feature(schoolId: SchoolId, name: String): RoomFeature = RoomFeature.create(schoolId, name).valid().also(roomFeatures::insert)

    fun room(schoolId: SchoolId, identifier: String, featureIds: Set<RoomFeatureId> = emptySet()): Room =
        Room.create(schoolId, identifier, featureIds, featureIds).valid().also(rooms::insert)

    fun room(schoolId: SchoolId, identifier: String, featureId: RoomFeatureId): Room = room(schoolId, identifier, setOf(featureId))

    fun teachingDays(schoolId: SchoolId, vararg days: DayOfWeek) {
        schools.saveTimeGrid(schoolId, schools.findTimeGrid(schoolId).withTeachingDays(days.toSet()))
    }

    fun slot(schoolId: SchoolId, label: String, start: String, end: String, kind: SlotKind = SlotKind.TEACHING): TimeSlot {
        val grid = schools.findTimeGrid(schoolId).withSlotAdded(label, LocalTime.parse(start), LocalTime.parse(end), kind).valid()
        schools.saveTimeGrid(schoolId, grid)
        return grid.slots.single { it.label == label }
    }

    fun subject(schoolId: SchoolId, name: String, requiredFeatureIds: Set<RoomFeatureId> = emptySet()): Subject =
        Subject.create(schoolId, name, requiredFeatureIds, requiredFeatureIds).valid().also(subjects::insert)

    /** A level with the given (subject, periods per week) curriculum. */
    fun classLevel(schoolId: SchoolId, identifier: String, vararg curriculum: Pair<Subject, Int>): ClassLevel {
        val entries = curriculum.map { (subject, periods) -> CurriculumEntry(subject.id, periods) }
        return ClassLevel.create(schoolId, identifier).valid()
            .update(identifier, entries, entries.map { it.subjectId }.toSet()).valid()
            .also(classLevels::insert)
    }

    fun schoolClass(level: ClassLevel, suffix: String, priorityWeight: Int = SchoolClass.DEFAULT_PRIORITY_WEIGHT): SchoolClass =
        SchoolClass.create(level.id, suffix, priorityWeight).valid().also(schoolClasses::insert)

    fun excludeOn(schoolId: SchoolId, slot: TimeSlot, vararg days: DayOfWeek) {
        schools.saveTimeGrid(schoolId, schools.findTimeGrid(schoolId).withExclusions(slot.id, days.toSet()).valid())
    }

    /** A teacher stored directly; the load is not checked against the grid here. */
    fun teacher(
        schoolId: SchoolId,
        name: String,
        shortCode: String,
        weeklyLoad: Int = 0,
        qualifiedSubjects: Set<SubjectId> = emptySet(),
    ): Teacher =
        Teacher.create(schoolId, name, shortCode, weeklyLoad, teachingSlotsPerWeek = Int.MAX_VALUE).valid()
            .withQualifications(qualifiedSubjects, qualifiedSubjects).valid()
            .also(teachers::insert)

    /** Stores unavailability without checking it against the grid. */
    fun markUnavailable(teacher: Teacher, vararg entries: Unavailability) {
        val stored = teachers.findById(teacher.id)!!
        teachers.update(stored.copy(unavailability = stored.unavailability + entries))
    }

    /** The installation use case wired to this world; it seeds the default constraints (002 FR-013c). */
    fun installation() = InstallationUseCase(transactions, schools, accounts, credentials, hasher, constraints)

    /** The scheduling problem of the school's current data, as a generation would see it. */
    fun problemOf(schoolId: SchoolId): SchedulingProblem =
        SchedulingProblemBuilder(schools, rooms, subjects, classLevels, schoolClasses, teachers).build(schoolId)

    /** Stores [candidate] directly as an alternative of [generation], evaluated against the school's current data. */
    fun storeAlternative(generation: Generation, candidate: TimetableCandidate): Alternative {
        val evaluation = ObjectiveEvaluator.evaluate(problemOf(generation.schoolId), candidate)
        return Alternative(AlternativeId.new(), generation.id, evaluation.values, candidate.lessons, candidate.unplaced, evaluation.softViolations)
            .also(generations::addAlternative)
    }

    fun bumpSchoolVersion(schoolId: SchoolId) {
        schools.update(schools.findById(schoolId)!!)
    }

    companion object {
        const val DEFAULT_PASSWORD = "sehr-geheim-1"
    }
}
