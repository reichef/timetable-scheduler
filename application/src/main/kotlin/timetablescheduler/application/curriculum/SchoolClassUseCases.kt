package timetablescheduler.application.curriculum

import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.port.ClassLevelRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.application.port.SchoolClassRepository
import timetablescheduler.application.port.SubjectRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.curriculum.ClassCurriculumResolver
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.validation.ValidationError

/** A class with the curriculum it receives (read only, FR-014) and the subjects to name its entries. */
data class SchoolClassDetails(
    val schoolClass: SchoolClass,
    val level: ClassLevel,
    val curriculum: List<CurriculumEntry>,
    val subjects: List<Subject>,
    val rooms: List<Room> = emptyList(),
) {
    val displayName: String get() = schoolClass.displayName(level)
}

/** Rejects a suffix another class of the same level already uses (FR-012, case-insensitive). */
private fun OutcomeScope.requireUniqueSuffix(classes: SchoolClassRepository, schoolClass: SchoolClass) {
    val taken = classes.findByLevel(schoolClass.classLevelId)
        .any { it.id != schoolClass.id && it.suffix.equals(schoolClass.suffix, ignoreCase = true) }
    if (taken) failDuplicate(SchoolClass.FIELD_SUFFIX)
}

/**
 * 002 FR-002a: the home room must be a room of the school and may be the home room of at most one class; the
 * rejection names the other class, which the planner is allowed to see.
 */
private fun OutcomeScope.requireAvailableHomeRoom(
    rooms: RoomRepository,
    classes: SchoolClassRepository,
    levels: ClassLevelRepository,
    schoolId: SchoolId,
    schoolClass: SchoolClass,
) {
    val homeRoomId = schoolClass.homeRoomId ?: return
    if (rooms.findById(homeRoomId)?.schoolId != schoolId) {
        fail(ApplicationError.Invalid(listOf(ValidationError.InvalidReference(SchoolClass.FIELD_HOME_ROOM))))
    }
    val otherClass = classes.findByHomeRoom(homeRoomId)?.takeIf { it.id != schoolClass.id } ?: return
    val otherLevel = levels.findById(otherClass.classLevelId) ?: return
    fail(ApplicationError.Invalid(listOf(ValidationError.HomeRoomTaken(otherClass.displayName(otherLevel)))))
}

class CreateSchoolClass(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val levels: ClassLevelRepository,
    private val classes: SchoolClassRepository,
    private val rooms: RoomRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        levelId: ClassLevelId,
        suffix: String,
        priorityWeight: Int,
        homeRoomId: RoomId? = null,
    ): UseCaseResult<SchoolClass> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                loadLevelOfSchool(levels, schoolId, levelId)
                val schoolClass = SchoolClass.create(levelId, suffix, priorityWeight).bind().withHomeRoom(homeRoomId)
                requireUniqueSuffix(classes, schoolClass)
                requireAvailableHomeRoom(rooms, classes, levels, schoolId, schoolClass)
                classes.insert(schoolClass)
                schoolClass
            }
        }
}

class UpdateSchoolClass(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val levels: ClassLevelRepository,
    private val classes: SchoolClassRepository,
    private val rooms: RoomRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        classId: SchoolClassId,
        suffix: String,
        priorityWeight: Int,
        expectedVersion: Int,
        homeRoomId: RoomId?,
    ): UseCaseResult<SchoolClass> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val (schoolClass, _) = loadClassOfSchool(classes, levels, schoolId, classId)
                val updated = schoolClass.copy(version = expectedVersion).update(suffix, priorityWeight).bind().withHomeRoom(homeRoomId)
                requireUniqueSuffix(classes, updated)
                requireAvailableHomeRoom(rooms, classes, levels, schoolId, updated)
                requireUpdated(classes.update(updated))
                updated.copy(version = expectedVersion + 1)
            }
        }
}

class DeleteSchoolClass(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val levels: ClassLevelRepository,
    private val classes: SchoolClassRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, classId: SchoolClassId, expectedVersion: Int): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val (schoolClass, _) = loadClassOfSchool(classes, levels, schoolId, classId)
                requireVersion(schoolClass.version, expectedVersion)
                classes.delete(classId)
            }
        }
}

class GetSchoolClass(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val levels: ClassLevelRepository,
    private val classes: SchoolClassRepository,
    private val subjects: SubjectRepository,
    private val rooms: RoomRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, classId: SchoolClassId): UseCaseResult<SchoolClassDetails> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val (schoolClass, level) = loadClassOfSchool(classes, levels, schoolId, classId)
                SchoolClassDetails(
                    schoolClass = schoolClass,
                    level = level,
                    curriculum = ClassCurriculumResolver.curriculumOf(schoolClass, level),
                    subjects = subjects.findBySchool(schoolId),
                    rooms = roomsByIdentifier(rooms, schoolId),
                )
            }
        }
}
