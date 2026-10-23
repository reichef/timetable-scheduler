package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.solution.CourseKey

/** Which classes a class-related constraint applies to (FR-013b). */
sealed interface ClassScope {
    /** Every class of the school, also classes created later. */
    data object AllClasses : ClassScope

    data class SpecificClass(
        val classId: SchoolClassId,
    ) : ClassScope
}

/** What a constraint demands: the catalogue of FR-013 (G9); every subtype counts its violations in [RuleViolations]. */
sealed interface ConstraintRule {
    /** At most [limit] periods per day for a class. */
    data class ClassMaxPeriodsPerDay(
        val scope: ClassScope,
        val limit: Int,
    ) : ConstraintRule

    /** At least [limit] periods on every teaching day for a class. */
    data class ClassMinPeriodsPerDay(
        val scope: ClassScope,
        val limit: Int,
    ) : ConstraintRule

    /** No free teaching slots between the first and last lesson of a day. */
    data class ClassNoGaps(
        val scope: ClassScope,
    ) : ConstraintRule

    data class TeacherMaxPeriodsPerDay(
        val teacherId: TeacherId,
        val limit: Int,
    ) : ConstraintRule

    /** At most [limit] free teaching slots between the first and last lesson of a teacher's day. */
    data class TeacherMaxGapsPerDay(
        val teacherId: TeacherId,
        val limit: Int,
    ) : ConstraintRule

    /** A subject — or every subject when [subjectId] is null — at most [limit] periods per day in a class. */
    data class SubjectMaxPerDayInClass(
        val scope: ClassScope,
        val subjectId: SubjectId?,
        val limit: Int,
    ) : ConstraintRule

    /** The subject is taught in pairs of consecutive teaching slots; [acrossBreaks] allows a break inside a pair. */
    data class SubjectDoublePeriodsInClass(
        val classId: SchoolClassId,
        val subjectId: SubjectId,
        val acrossBreaks: Boolean,
    ) : ConstraintRule

    data class ClassExcludedSlots(
        val classId: SchoolClassId,
        val slots: Set<DaySlot>,
    ) : ConstraintRule

    /** As soft constraint: the teacher's preferred free slots or days. */
    data class TeacherExcludedSlots(
        val teacherId: TeacherId,
        val slots: Set<DaySlot>,
    ) : ConstraintRule

    /** For every class, or only for [classId] when given. */
    data class SubjectExcludedSlots(
        val subjectId: SubjectId,
        val classId: SchoolClassId?,
        val slots: Set<DaySlot>,
    ) : ConstraintRule {
        fun appliesTo(course: CourseKey): Boolean = course.subjectId == subjectId && (classId == null || course.classId == classId)
    }
}

/** The catalogue type of a rule, as stored and named in the interface. */
enum class ConstraintType {
    CLASS_MAX_PERIODS_PER_DAY,
    CLASS_MIN_PERIODS_PER_DAY,
    CLASS_NO_GAPS,
    TEACHER_MAX_PERIODS_PER_DAY,
    TEACHER_MAX_GAPS_PER_DAY,
    SUBJECT_MAX_PER_DAY_IN_CLASS,
    SUBJECT_DOUBLE_PERIODS_IN_CLASS,
    CLASS_EXCLUDED_SLOTS,
    TEACHER_EXCLUDED_SLOTS,
    SUBJECT_EXCLUDED_SLOTS,
}

val ConstraintRule.type: ConstraintType
    get() =
        when (this) {
            is ConstraintRule.ClassMaxPeriodsPerDay -> ConstraintType.CLASS_MAX_PERIODS_PER_DAY
            is ConstraintRule.ClassMinPeriodsPerDay -> ConstraintType.CLASS_MIN_PERIODS_PER_DAY
            is ConstraintRule.ClassNoGaps -> ConstraintType.CLASS_NO_GAPS
            is ConstraintRule.TeacherMaxPeriodsPerDay -> ConstraintType.TEACHER_MAX_PERIODS_PER_DAY
            is ConstraintRule.TeacherMaxGapsPerDay -> ConstraintType.TEACHER_MAX_GAPS_PER_DAY
            is ConstraintRule.SubjectMaxPerDayInClass -> ConstraintType.SUBJECT_MAX_PER_DAY_IN_CLASS
            is ConstraintRule.SubjectDoublePeriodsInClass -> ConstraintType.SUBJECT_DOUBLE_PERIODS_IN_CLASS
            is ConstraintRule.ClassExcludedSlots -> ConstraintType.CLASS_EXCLUDED_SLOTS
            is ConstraintRule.TeacherExcludedSlots -> ConstraintType.TEACHER_EXCLUDED_SLOTS
            is ConstraintRule.SubjectExcludedSlots -> ConstraintType.SUBJECT_EXCLUDED_SLOTS
        }

/** The class scope of class-related rules; null for teacher- and slot-related rules. */
val ConstraintRule.classScope: ClassScope?
    get() =
        when (this) {
            is ConstraintRule.ClassMaxPeriodsPerDay -> scope
            is ConstraintRule.ClassMinPeriodsPerDay -> scope
            is ConstraintRule.ClassNoGaps -> scope
            is ConstraintRule.SubjectMaxPerDayInClass -> scope
            else -> null
        }

/** The excluded slots of exclusion rules; empty for all others. */
val ConstraintRule.excludedSlots: Set<DaySlot>
    get() =
        when (this) {
            is ConstraintRule.ClassExcludedSlots -> slots
            is ConstraintRule.TeacherExcludedSlots -> slots
            is ConstraintRule.SubjectExcludedSlots -> slots
            else -> emptySet()
        }

/**
 * A text that identifies the rule independent of set order — used by the input fingerprint (G8), so loading the same
 * slots in another order never makes a generation look outdated.
 */
fun ConstraintRule.canonicalText(): String {
    val slots = excludedSlots.map { "${it.day}:${it.slotId.value}" }.sorted()
    val scope =
        when (val classScope = classScope) {
            ClassScope.AllClasses -> "all"
            is ClassScope.SpecificClass -> "${classScope.classId.value}"
            null -> "-"
        }
    val details =
        when (this) {
            is ConstraintRule.ClassMaxPeriodsPerDay -> "$limit"
            is ConstraintRule.ClassMinPeriodsPerDay -> "$limit"
            is ConstraintRule.ClassNoGaps -> ""
            is ConstraintRule.TeacherMaxPeriodsPerDay -> "${teacherId.value} $limit"
            is ConstraintRule.TeacherMaxGapsPerDay -> "${teacherId.value} $limit"
            is ConstraintRule.SubjectMaxPerDayInClass -> "${subjectId?.value} $limit"
            is ConstraintRule.SubjectDoublePeriodsInClass -> "${classId.value} ${subjectId.value} $acrossBreaks"
            is ConstraintRule.ClassExcludedSlots -> "${classId.value}"
            is ConstraintRule.TeacherExcludedSlots -> "${teacherId.value}"
            is ConstraintRule.SubjectExcludedSlots -> "${subjectId.value} ${classId?.value}"
        }
    return "$type $scope $details $slots"
}
