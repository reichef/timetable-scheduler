package timetablescheduler.application.curriculum

import timetablescheduler.application.access.DependantSource
import timetablescheduler.application.access.DependantVisibilityFilter
import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.access.dependantsOf
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.ClassLevelRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.application.port.SchoolClassRepository
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.SubjectRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.curriculum.SuffixProposal
import timetablescheduler.domain.readiness.LevelExceedsGridRule
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.room.Room

/** A saved level and, if its curriculum does not fit into the weekly grid, the non-blocking warning (FR-025). */
data class ClassLevelSaved(
    val level: ClassLevel,
    val warning: ReadinessIssue.LevelExceedsGrid?,
)

data class ClassLevelSummary(
    val level: ClassLevel,
    val classes: List<SchoolClass>,
)

data class ClassLevelOverview(
    val levels: List<ClassLevelSummary>,
    val teachingSlotsPerWeek: Int,
)

data class ClassLevelDetails(
    val level: ClassLevel,
    val classes: List<SchoolClass>,
    val subjects: List<Subject>,
    val teachingSlotsPerWeek: Int,
    val warning: ReadinessIssue.LevelExceedsGrid?,
)

/** The new-class form: proposed suffix and the rooms that can be chosen as home room (002 FR-002a). */
data class ClassProposal(
    val level: ClassLevel,
    val suffix: String?,
    val rooms: List<Room> = emptyList(),
)

/** Rejects an identifier another level of the same school already uses (FR-010, case-insensitive). */
private fun OutcomeScope.requireUniqueIdentifier(
    levels: ClassLevelRepository,
    level: ClassLevel,
) {
    val existing = levels.findByIdentifierIgnoreCase(level.schoolId, level.identifier)
    if (existing != null && existing.id != level.id) {
        failDuplicate(ClassLevel.FIELD_IDENTIFIER)
    }
}

private fun SchoolClassRepository.sortedClassesOf(levelId: ClassLevelId): List<SchoolClass> =
    findByLevel(levelId).sortedBy { it.suffix.lowercase() }

class CreateClassLevel(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val levels: ClassLevelRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        identifier: String,
    ): UseCaseResult<ClassLevel> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val level = ClassLevel.create(schoolId, identifier).bind()
                requireUniqueIdentifier(levels, level)
                levels.insert(level)
                level
            }
        }
}

/** Saves identifier and curriculum; an over-full level is saved and only warned about (acceptance 2.5, FR-025). */
class UpdateClassLevel(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
    private val levels: ClassLevelRepository,
    private val subjects: SubjectRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        levelId: ClassLevelId,
        identifier: String,
        curriculum: List<CurriculumEntry>,
        expectedVersion: Int,
    ): UseCaseResult<ClassLevelSaved> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()

                val level = loadLevelOfSchool(levels, schoolId, levelId).copy(version = expectedVersion)
                val schoolSubjects = subjects.findBySchool(schoolId).map { it.id }.toSet()

                val updated = level.update(identifier, curriculum, schoolSubjects).bind()
                requireUniqueIdentifier(levels, updated)

                requireUpdated(levels.update(updated))
                val saved = updated.copy(version = expectedVersion + 1)

                ClassLevelSaved(saved, LevelExceedsGridRule.check(saved, schools.findTimeGrid(schoolId).teachingSlotsPerWeek()))
            }
        }
}

/** Blocked while the level still has classes (FR-023). */
class DeleteClassLevel(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val levels: ClassLevelRepository,
    private val dependantSources: List<DependantSource<ClassLevelId>>,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        levelId: ClassLevelId,
        expectedVersion: Int,
    ): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                val planner = access.requireSchoolAccess(actor, schoolId).bind()
                requireVersion(loadLevelOfSchool(levels, schoolId, levelId).version, expectedVersion)
                val dependants = dependantSources.dependantsOf(levelId)
                if (dependants.isNotEmpty()) {
                    fail(DependantVisibilityFilter.deletionBlocked(dependants, planner))
                }
                levels.delete(levelId)
            }
        }
}

class ListClassLevels(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
    private val levels: ClassLevelRepository,
    private val classes: SchoolClassRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
    ): UseCaseResult<ClassLevelOverview> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val summaries =
                    levels
                        .findBySchool(schoolId)
                        .sortedBy { it.identifier.lowercase() }
                        .map { ClassLevelSummary(it, classes.sortedClassesOf(it.id)) }
                ClassLevelOverview(summaries, schools.findTimeGrid(schoolId).teachingSlotsPerWeek())
            }
        }
}

class GetClassLevel(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
    private val levels: ClassLevelRepository,
    private val classes: SchoolClassRepository,
    private val subjects: SubjectRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        levelId: ClassLevelId,
    ): UseCaseResult<ClassLevelDetails> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val level = loadLevelOfSchool(levels, schoolId, levelId)
                val teachingSlotsPerWeek = schools.findTimeGrid(schoolId).teachingSlotsPerWeek()
                ClassLevelDetails(
                    level = level,
                    classes = classes.sortedClassesOf(levelId),
                    subjects = subjects.findBySchool(schoolId).sortedBy { it.name.lowercase() },
                    teachingSlotsPerWeek = teachingSlotsPerWeek,
                    warning = LevelExceedsGridRule.check(level, teachingSlotsPerWeek),
                )
            }
        }
}

/** FR-013: the suffix the new-class form is pre-filled with. */
class ProposeClassSuffix(
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
    ): UseCaseResult<ClassProposal> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val level = loadLevelOfSchool(levels, schoolId, levelId)
                ClassProposal(
                    level,
                    SuffixProposal.next(classes.findByLevel(levelId).map { it.suffix }),
                    roomsByIdentifier(rooms, schoolId),
                )
            }
        }
}
