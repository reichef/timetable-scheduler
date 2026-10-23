package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.validation.ValidationError

/**
 * FR-015: rejects constraints with references to unknown entries, limits outside the time grid, slots that are no
 * teaching slots, subjects outside the class's curriculum, and a hard daily minimum above an active hard daily maximum
 * of the same class. [problem] describes the school's current data and its other constraints.
 */
object ConstraintValidation {
    const val FIELD_LIMIT = "limit"
    const val FIELD_CLASS = "classId"
    const val FIELD_TEACHER = "teacherId"
    const val FIELD_SUBJECT = "subjectId"
    const val FIELD_SLOT = "slot"

    fun validate(
        rule: ConstraintRule,
        kind: ConstraintKind,
        problem: SchedulingProblem,
    ): List<ValidationError> {
        val context = Context(problem)
        return when (rule) {
            is ConstraintRule.ClassMaxPeriodsPerDay -> {
                context.classScope(rule.scope) + context.dailyLimit(rule.limit)
            }

            is ConstraintRule.ClassMinPeriodsPerDay -> {
                context.classScope(rule.scope) + context.dailyLimit(rule.limit) + context.minimumWithinHardMaximum(rule, kind)
            }

            is ConstraintRule.ClassNoGaps -> {
                context.classScope(rule.scope)
            }

            is ConstraintRule.TeacherMaxPeriodsPerDay -> {
                context.teacher(rule.teacherId) + context.dailyLimit(rule.limit)
            }

            is ConstraintRule.TeacherMaxGapsPerDay -> {
                context.teacher(rule.teacherId) + context.range(rule.limit, 0, (context.maxTeachingSlotsPerDay - 2).coerceAtLeast(0))
            }

            is ConstraintRule.SubjectMaxPerDayInClass -> {
                context.classScope(rule.scope) + context.subjectOfScope(rule.scope, rule.subjectId) + context.dailyLimit(rule.limit)
            }

            is ConstraintRule.SubjectDoublePeriodsInClass -> {
                context.schoolClass(rule.classId) +
                    context.doublePeriodSubject(rule.classId, rule.subjectId)
            }

            is ConstraintRule.ClassExcludedSlots -> {
                context.schoolClass(rule.classId) + context.slots(rule)
            }

            is ConstraintRule.TeacherExcludedSlots -> {
                context.teacher(rule.teacherId) + context.slots(rule)
            }

            is ConstraintRule.SubjectExcludedSlots -> {
                context.subjectOfScope(rule.classId?.let(ClassScope::SpecificClass) ?: ClassScope.AllClasses, rule.subjectId) +
                    (rule.classId?.let(context::schoolClass) ?: emptyList()) + context.slots(rule)
            }
        }.distinct()
    }

    private class Context(
        val problem: SchedulingProblem,
    ) {
        val maxTeachingSlotsPerDay = problem.days.maxOfOrNull { it.teachingSlots.size } ?: 0

        fun schoolClass(classId: SchoolClassId) =
            if (problem.schoolClass(classId) == null) listOf(ValidationError.InvalidReference(FIELD_CLASS)) else emptyList()

        fun classScope(scope: ClassScope) = if (scope is ClassScope.SpecificClass) schoolClass(scope.classId) else emptyList()

        fun teacher(teacherId: TeacherId) =
            if (problem.teacher(teacherId) == null) {
                listOf(ValidationError.InvalidReference(FIELD_TEACHER))
            } else {
                emptyList()
            }

        fun range(
            limit: Int,
            min: Int,
            max: Int,
        ) = if (limit < min || limit > max) {
            listOf(ValidationError.OutOfRange(FIELD_LIMIT, min, max))
        } else {
            emptyList()
        }

        fun dailyLimit(limit: Int) = range(limit, 1, maxTeachingSlotsPerDay)

        /** The subject must exist and, for a specific class, be in the class's curriculum. */
        fun subjectOfScope(
            scope: ClassScope,
            subjectId: SubjectId?,
        ): List<ValidationError> {
            if (subjectId == null) {
                return emptyList()
            }

            val known = problem.subject(subjectId) != null
            val inCurriculum =
                scope !is ClassScope.SpecificClass ||
                    problem.schoolClass(scope.classId)?.curriculum?.any { it.subjectId == subjectId } == true
            return if (known && inCurriculum) emptyList() else listOf(ValidationError.InvalidReference(FIELD_SUBJECT))
        }

        fun doublePeriodSubject(
            classId: SchoolClassId,
            subjectId: SubjectId,
        ): List<ValidationError> {
            val periods =
                problem
                    .schoolClass(classId)
                    ?.curriculum
                    ?.firstOrNull { it.subjectId == subjectId }
                    ?.periodsPerWeek ?: 0
            return if (periods >= 2) emptyList() else listOf(ValidationError.InvalidReference(FIELD_SUBJECT))
        }

        fun slots(rule: ConstraintRule): List<ValidationError> {
            val slots = rule.excludedSlots
            return when {
                slots.isEmpty() -> listOf(ValidationError.BlankValue(FIELD_SLOT))
                !slots.all(problem::isTeachingSlot) -> listOf(ValidationError.InvalidReference(FIELD_SLOT))
                else -> emptyList()
            }
        }

        /** A hard minimum may not exceed the smallest active hard maximum applying to the same class(es). */
        fun minimumWithinHardMaximum(
            rule: ConstraintRule.ClassMinPeriodsPerDay,
            kind: ConstraintKind,
        ): List<ValidationError> {
            if (kind != ConstraintKind.HARD) {
                return emptyList()
            }

            val hardMaximums =
                problem.constraints
                    .filter { it.active && it.isHard }
                    .map { it.rule }
                    .filterIsInstance<ConstraintRule.ClassMaxPeriodsPerDay>()
                    .filter { maximum ->
                        rule.scope == ClassScope.AllClasses || maximum.scope == ClassScope.AllClasses || maximum.scope == rule.scope
                    }
            val lowestMaximum = hardMaximums.minOfOrNull { it.limit } ?: return emptyList()
            return range(rule.limit, 1, lowestMaximum)
        }
    }
}
