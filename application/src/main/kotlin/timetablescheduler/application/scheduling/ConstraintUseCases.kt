package timetablescheduler.application.scheduling

import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.SchedulingConstraintRepository
import timetablescheduler.application.port.TeacherFixationRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.ConstraintValidation
import timetablescheduler.domain.scheduling.constraint.FixationInvalidity
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.constraint.TeacherFixationId
import timetablescheduler.domain.scheduling.constraint.TeacherFixationRules
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.validation.ValidationError

/** What the planner entered for a constraint; a soft constraint without weight gets medium (FR-014). */
data class ConstraintInput(val kind: ConstraintKind, val weight: SoftWeight?, val rule: ConstraintRule) {
    val effectiveWeight: SoftWeight? get() = if (kind == ConstraintKind.SOFT) weight ?: SoftWeight.MEDIUM else null
}

/**
 * The constraint page: all constraints and fixations of the school, names to describe them, the fixations made
 * invalid by later changes (FR-013a) and the school's scheduling problem for the forms (classes, slots, curricula).
 */
data class ConstraintOverview(
    val schoolId: SchoolId,
    val constraints: List<SchedulingConstraint>,
    val fixations: List<TeacherFixation>,
    val invalidFixations: Map<TeacherFixationId, FixationInvalidity>,
    val names: TimetableNames,
    val problem: SchedulingProblem,
)

private fun OutcomeScope.loadConstraintOfSchool(constraints: SchedulingConstraintRepository, schoolId: SchoolId, id: SchedulingConstraintId): SchedulingConstraint {
    val constraint = constraints.findById(id)
    if (constraint == null || constraint.schoolId != schoolId) fail(ApplicationError.NotFound)
    return constraint
}

private fun OutcomeScope.loadFixationOfSchool(fixations: TeacherFixationRepository, schoolId: SchoolId, id: TeacherFixationId): TeacherFixation {
    val fixation = fixations.findById(id)
    if (fixation == null || fixation.schoolId != schoolId) fail(ApplicationError.NotFound)
    return fixation
}

/** The school's current scheduling problem with its stored constraints and fixations, for validation. */
class ConstraintContextLoader(
    private val problems: SchedulingProblemBuilder,
    private val constraints: SchedulingConstraintRepository,
    private val fixations: TeacherFixationRepository,
) {
    fun load(schoolId: SchoolId): SchedulingProblem =
        problems.build(schoolId).copy(constraints = constraints.findBySchool(schoolId), fixations = fixations.findBySchool(schoolId))
}

class ListConstraints(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val context: ConstraintContextLoader,
    private val names: TimetableNamesLoader,
) {
    fun execute(actor: AccountId, schoolId: SchoolId): UseCaseResult<ConstraintOverview> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val problem = context.load(schoolId)
                ConstraintOverview(
                    schoolId = schoolId,
                    constraints = problem.constraints,
                    fixations = problem.fixations,
                    invalidFixations = problem.fixations.mapNotNull { fixation -> TeacherFixationRules.invalidityOf(fixation, problem)?.let { fixation.id to it } }.toMap(),
                    names = names.load(schoolId),
                    problem = problem,
                )
            }
        }
}

class GetConstraint(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val constraints: SchedulingConstraintRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, constraintId: SchedulingConstraintId): UseCaseResult<SchedulingConstraint> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                loadConstraintOfSchool(constraints, schoolId, constraintId)
            }
        }
}

/** FR-012, FR-015: validates against the school's current data and the other constraints, then saves. */
class CreateConstraint(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val constraints: SchedulingConstraintRepository,
    private val context: ConstraintContextLoader,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, input: ConstraintInput): UseCaseResult<SchedulingConstraint> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                requireValid(input, context.load(schoolId))
                SchedulingConstraint(SchedulingConstraintId.new(), schoolId, input.kind, input.effectiveWeight, active = true, rule = input.rule, version = 0)
                    .also(constraints::insert)
            }
        }
}

class UpdateConstraint(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val constraints: SchedulingConstraintRepository,
    private val context: ConstraintContextLoader,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, constraintId: SchedulingConstraintId, input: ConstraintInput, expectedVersion: Int): UseCaseResult<SchedulingConstraint> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val stored = loadConstraintOfSchool(constraints, schoolId, constraintId)
                val problem = context.load(schoolId)
                requireValid(input, problem.copy(constraints = problem.constraints.filterNot { it.id == constraintId }))
                val updated = stored.copy(kind = input.kind, weight = input.effectiveWeight, rule = input.rule, version = expectedVersion)
                requireUpdated(constraints.update(updated))
                updated.copy(version = expectedVersion + 1)
            }
        }
}

/** Switches a constraint off or on (US2 scenario 7); switched-off constraints are kept but ignored. */
class SetConstraintActive(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val constraints: SchedulingConstraintRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, constraintId: SchedulingConstraintId, active: Boolean, expectedVersion: Int): UseCaseResult<SchedulingConstraint> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val updated = loadConstraintOfSchool(constraints, schoolId, constraintId).copy(active = active, version = expectedVersion)
                requireUpdated(constraints.update(updated))
                updated.copy(version = expectedVersion + 1)
            }
        }
}

/** Deleting a constraint is never blocked; generations keep their snapshot of it (FR-022). */
class DeleteConstraint(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val constraints: SchedulingConstraintRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, constraintId: SchedulingConstraintId, expectedVersion: Int): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val stored = loadConstraintOfSchool(constraints, schoolId, constraintId)
                if (stored.version != expectedVersion) fail(ApplicationError.VersionConflict)
                constraints.delete(constraintId)
            }
        }
}

private fun OutcomeScope.requireValid(input: ConstraintInput, problem: SchedulingProblem) {
    val errors = ConstraintValidation.validate(input.rule, input.kind, problem)
    if (errors.isNotEmpty()) fail(ApplicationError.Invalid(errors))
}

/** FR-013a: create, change and delete teacher fixations; one per class and subject. */
class TeacherFixationUseCases(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val fixations: TeacherFixationRepository,
    private val context: ConstraintContextLoader,
) {
    val create = Create()
    val update = Update()
    val delete = Delete()

    inner class Create {
        fun execute(actor: AccountId, schoolId: SchoolId, classId: SchoolClassId, subjectId: SubjectId, teacherId: TeacherId): UseCaseResult<TeacherFixation> =
            transactions.inTransaction {
                outcome {
                    access.requireSchoolAccess(actor, schoolId).bind()
                    if (fixations.findByCourse(classId, subjectId) != null) {
                        fail(ApplicationError.Invalid(listOf(ValidationError.DuplicateValue(FIELD_COURSE))))
                    }
                    val fixation = TeacherFixation(TeacherFixationId.new(), schoolId, classId, subjectId, teacherId, version = 0)
                    requireValidFixation(fixation, schoolId)
                    fixation.also(fixations::insert)
                }
            }
    }

    inner class Update {
        fun execute(actor: AccountId, schoolId: SchoolId, fixationId: TeacherFixationId, teacherId: TeacherId, expectedVersion: Int): UseCaseResult<TeacherFixation> =
            transactions.inTransaction {
                outcome {
                    access.requireSchoolAccess(actor, schoolId).bind()
                    val updated = loadFixationOfSchool(fixations, schoolId, fixationId).copy(teacherId = teacherId, version = expectedVersion)
                    requireValidFixation(updated, schoolId)
                    requireUpdated(fixations.update(updated))
                    updated.copy(version = expectedVersion + 1)
                }
            }
    }

    inner class Delete {
        fun execute(actor: AccountId, schoolId: SchoolId, fixationId: TeacherFixationId, expectedVersion: Int): UseCaseResult<Unit> =
            transactions.inTransaction {
                outcome {
                    access.requireSchoolAccess(actor, schoolId).bind()
                    if (loadFixationOfSchool(fixations, schoolId, fixationId).version != expectedVersion) fail(ApplicationError.VersionConflict)
                    fixations.delete(fixationId)
                }
            }
    }

    private fun OutcomeScope.requireValidFixation(fixation: TeacherFixation, schoolId: SchoolId) {
        val problem = context.load(schoolId)
        if (problem.schoolClass(fixation.classId) == null) fail(ApplicationError.Invalid(listOf(ValidationError.InvalidReference(FIELD_CLASS))))
        val errors = TeacherFixationRules.validate(fixation, problem)
        if (errors.isNotEmpty()) fail(ApplicationError.Invalid(errors))
    }

    companion object {
        const val FIELD_COURSE = "course"
        const val FIELD_CLASS = "classId"
    }
}

class SchedulingConstraintUseCases(
    val list: ListConstraints,
    val get: GetConstraint,
    val create: CreateConstraint,
    val update: UpdateConstraint,
    val setActive: SetConstraintActive,
    val delete: DeleteConstraint,
)
