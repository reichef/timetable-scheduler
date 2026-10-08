package timetablescheduler.application.curriculum

import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.ClassLevelRepository
import timetablescheduler.application.port.SchoolClassRepository
import timetablescheduler.application.port.SubjectRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.curriculum.ClassCurriculumResolver
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject

/** A class with the curriculum it receives (read only, FR-014) and the subjects to name its entries. */
data class SchoolClassDetails(
    val schoolClass: SchoolClass,
    val level: ClassLevel,
    val curriculum: List<CurriculumEntry>,
    val subjects: List<Subject>,
) {
    val displayName: String get() = schoolClass.displayName(level)
}

/** Rejects a suffix another class of the same level already uses (FR-012, case-insensitive). */
private fun OutcomeScope.requireUniqueSuffix(classes: SchoolClassRepository, schoolClass: SchoolClass) {
    val taken = classes.findByLevel(schoolClass.classLevelId)
        .any { it.id != schoolClass.id && it.suffix.equals(schoolClass.suffix, ignoreCase = true) }
    if (taken) failDuplicate(SchoolClass.FIELD_SUFFIX)
}

class CreateSchoolClass(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val levels: ClassLevelRepository,
    private val classes: SchoolClassRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, levelId: ClassLevelId, suffix: String, priorityWeight: Int): UseCaseResult<SchoolClass> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                loadLevelOfSchool(levels, schoolId, levelId)
                val schoolClass = SchoolClass.create(levelId, suffix, priorityWeight).bind()
                requireUniqueSuffix(classes, schoolClass)
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
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        classId: SchoolClassId,
        suffix: String,
        priorityWeight: Int,
        expectedVersion: Int,
    ): UseCaseResult<SchoolClass> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val (schoolClass, _) = loadClassOfSchool(classes, levels, schoolId, classId)
                val updated = schoolClass.copy(version = expectedVersion).update(suffix, priorityWeight).bind()
                requireUniqueSuffix(classes, updated)
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
                )
            }
        }
}
