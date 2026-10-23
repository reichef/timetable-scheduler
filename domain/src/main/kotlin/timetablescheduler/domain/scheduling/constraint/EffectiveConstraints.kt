package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.scheduling.problem.SchedulingProblem

/**
 * A constraint as it applies to one target: class-related rules apply per class ([classId]); the subject daily limit
 * applies per class and subject ([subjectId]); teacher and exclusion rules carry their targets in the rule.
 */
data class AppliedConstraint(
    val constraint: SchedulingConstraint,
    val classId: SchoolClassId?,
    val subjectId: SubjectId?,
) {
    val rule: ConstraintRule get() = constraint.rule
}

/**
 * Resolves which active constraints apply to which class (FR-013b): a school-wide class-related constraint applies to
 * every class, unless the class has its own constraint of the same type, which then replaces it for that class. For the
 * subject daily limit the replacement is per subject: a class's limit for one subject replaces the school-wide limits
 * only for that subject; a class's limit for every subject replaces them for all its subjects not limited individually.
 */
object EffectiveConstraints {
    fun resolve(problem: SchedulingProblem): List<AppliedConstraint> {
        val active = problem.constraints.filter { it.active }
        return active.flatMap { constraint ->
            when (val rule = constraint.rule) {
                is ConstraintRule.SubjectMaxPerDayInClass -> {
                    subjectLimitTargets(constraint, rule, active, problem)
                }

                else -> {
                    when (val scope = rule.classScope) {
                        null -> {
                            listOf(AppliedConstraint(constraint, null, null))
                        }

                        is ClassScope.SpecificClass -> {
                            listOf(AppliedConstraint(constraint, scope.classId, null))
                        }

                        ClassScope.AllClasses -> {
                            problem.classes
                                .filterNot { schoolClass -> active.hasOwnConstraint(rule.type, schoolClass.id) }
                                .map { AppliedConstraint(constraint, it.id, null) }
                        }
                    }
                }
            }
        }
    }

    private fun List<SchedulingConstraint>.hasOwnConstraint(
        type: ConstraintType,
        classId: SchoolClassId,
    ): Boolean = any { it.rule.type == type && it.rule.classScope == ClassScope.SpecificClass(classId) }

    private fun subjectLimitTargets(
        constraint: SchedulingConstraint,
        rule: ConstraintRule.SubjectMaxPerDayInClass,
        active: List<SchedulingConstraint>,
        problem: SchedulingProblem,
    ): List<AppliedConstraint> {
        val classLimits = active.map { it.rule }.filterIsInstance<ConstraintRule.SubjectMaxPerDayInClass>()

        fun ownLimitFor(
            classId: SchoolClassId,
            subjectId: SubjectId?,
        ) = classLimits.any { it.scope == ClassScope.SpecificClass(classId) && it.subjectId == subjectId }
        val classes =
            when (val scope = rule.scope) {
                ClassScope.AllClasses -> problem.classes
                is ClassScope.SpecificClass -> listOfNotNull(problem.schoolClass(scope.classId))
            }
        return classes.flatMap { schoolClass ->
            schoolClass.curriculum
                .map { it.subjectId }
                .filter { subjectId -> rule.subjectId == null || rule.subjectId == subjectId }
                .filterNot { subjectId ->
                    when {
                        rule.scope == ClassScope.AllClasses -> ownLimitFor(schoolClass.id, subjectId) || ownLimitFor(schoolClass.id, null)
                        rule.subjectId == null -> ownLimitFor(schoolClass.id, subjectId)
                        else -> false
                    }
                }.map { subjectId -> AppliedConstraint(constraint, schoolClass.id, subjectId) }
        }
    }
}

/** The two school-wide soft constraints every school starts with (FR-013c). */
object DefaultConstraints {
    const val DEFAULT_SUBJECT_LIMIT = 2

    fun forSchool(schoolId: SchoolId): List<SchedulingConstraint> =
        listOf(
            ConstraintRule.SubjectMaxPerDayInClass(ClassScope.AllClasses, null, DEFAULT_SUBJECT_LIMIT),
            ConstraintRule.ClassNoGaps(ClassScope.AllClasses),
        ).map { rule ->
            SchedulingConstraint(
                SchedulingConstraintId.new(),
                schoolId,
                ConstraintKind.SOFT,
                SoftWeight.MEDIUM,
                active = true,
                rule = rule,
                version = 0,
            )
        }
}
