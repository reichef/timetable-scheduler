package timetablescheduler.application.teacher

import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.SubjectRepository
import timetablescheduler.application.port.TeacherRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.teacher.Teacher
import timetablescheduler.domain.teacher.Unavailability
import timetablescheduler.domain.validation.ValidationError

/** A teacher with what the edit page offers: the school's subjects and its time grid. */
data class TeacherDetails(val teacher: Teacher, val subjects: List<Subject>, val timeGrid: TimeGrid)

/** Teachers are only reachable through their own school; others are "not found". */
private fun OutcomeScope.loadTeacherOfSchool(teachers: TeacherRepository, schoolId: SchoolId, teacherId: TeacherId): Teacher {
    val teacher = teachers.findById(teacherId)
    if (teacher == null || teacher.schoolId != schoolId) fail(ApplicationError.NotFound)
    return teacher
}

/** FR-016: the short code identifies a teacher across the installation (acceptance 3.2). */
private fun OutcomeScope.requireUniqueShortCode(teachers: TeacherRepository, teacher: Teacher) {
    val existing = teachers.findByShortCode(teacher.shortCode)
    if (existing != null && existing.id != teacher.id) {
        fail(ApplicationError.Invalid(listOf(ValidationError.DuplicateValue(Teacher.FIELD_SHORT_CODE))))
    }
}

/** Saves a changed teacher with optimistic locking and returns the stored state. */
private fun OutcomeScope.saveTeacher(teachers: TeacherRepository, changed: Teacher): Teacher {
    requireUpdated(teachers.update(changed))
    return changed.copy(version = changed.version + 1)
}

class CreateTeacher(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
    private val teachers: TeacherRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, name: String, shortCode: String, weeklyLoad: Int): UseCaseResult<Teacher> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val teachingSlotsPerWeek = schools.findTimeGrid(schoolId).teachingSlotsPerWeek()
                val teacher = Teacher.create(schoolId, name, shortCode, weeklyLoad, teachingSlotsPerWeek).bind()
                requireUniqueShortCode(teachers, teacher)
                teachers.insert(teacher)
                teacher
            }
        }
}

class UpdateTeacher(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
    private val teachers: TeacherRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        teacherId: TeacherId,
        name: String,
        shortCode: String,
        weeklyLoad: Int,
        expectedVersion: Int,
    ): UseCaseResult<Teacher> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val teacher = loadTeacherOfSchool(teachers, schoolId, teacherId).copy(version = expectedVersion)
                val teachingSlotsPerWeek = schools.findTimeGrid(schoolId).teachingSlotsPerWeek()
                val updated = teacher.withGeneralData(name, shortCode, weeklyLoad, teachingSlotsPerWeek).bind()
                requireUniqueShortCode(teachers, updated)
                saveTeacher(teachers, updated)
            }
        }
}

class UpdateQualifications(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val subjects: SubjectRepository,
    private val teachers: TeacherRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, teacherId: TeacherId, subjectIds: Set<SubjectId>, expectedVersion: Int): UseCaseResult<Teacher> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val teacher = loadTeacherOfSchool(teachers, schoolId, teacherId).copy(version = expectedVersion)
                val schoolSubjects = subjects.findBySchool(schoolId).map { it.id }.toSet()
                saveTeacher(teachers, teacher.withQualifications(subjectIds, schoolSubjects).bind())
            }
        }
}

class UpdateUnavailability(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
    private val teachers: TeacherRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        teacherId: TeacherId,
        unavailability: Set<Unavailability>,
        expectedVersion: Int,
    ): UseCaseResult<Teacher> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val teacher = loadTeacherOfSchool(teachers, schoolId, teacherId).copy(version = expectedVersion)
                saveTeacher(teachers, teacher.withUnavailability(unavailability, schools.findTimeGrid(schoolId)).bind())
            }
        }
}

/** Never blocked: qualifications and unavailability are deleted with the teacher (data-model deletion rules). */
class DeleteTeacher(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val teachers: TeacherRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, teacherId: TeacherId, expectedVersion: Int): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val teacher = loadTeacherOfSchool(teachers, schoolId, teacherId)
                if (teacher.version != expectedVersion) fail(ApplicationError.VersionConflict)
                teachers.delete(teacherId)
            }
        }
}

class ListTeachers(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val teachers: TeacherRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId): UseCaseResult<List<Teacher>> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                teachers.findBySchool(schoolId).sortedWith(compareBy({ it.name.lowercase() }, { it.shortCode }))
            }
        }
}

class GetTeacher(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
    private val subjects: SubjectRepository,
    private val teachers: TeacherRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, teacherId: TeacherId): UseCaseResult<TeacherDetails> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                TeacherDetails(
                    teacher = loadTeacherOfSchool(teachers, schoolId, teacherId),
                    subjects = subjects.findBySchool(schoolId).sortedBy { it.name.lowercase() },
                    timeGrid = schools.findTimeGrid(schoolId),
                )
            }
        }
}

class TeacherUseCases(
    val create: CreateTeacher,
    val update: UpdateTeacher,
    val updateQualifications: UpdateQualifications,
    val updateUnavailability: UpdateUnavailability,
    val delete: DeleteTeacher,
    val list: ListTeachers,
    val get: GetTeacher,
)
