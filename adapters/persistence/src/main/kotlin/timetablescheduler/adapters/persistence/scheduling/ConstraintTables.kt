package timetablescheduler.adapters.persistence.scheduling

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import timetablescheduler.adapters.persistence.curriculum.SchoolClassTable
import timetablescheduler.adapters.persistence.curriculum.SubjectTable
import timetablescheduler.adapters.persistence.school.SchoolTable
import timetablescheduler.adapters.persistence.school.TimeSlotTable
import timetablescheduler.adapters.persistence.teacher.TeacherTable
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.ConstraintType
import timetablescheduler.domain.scheduling.constraint.classScope
import timetablescheduler.domain.scheduling.constraint.excludedSlots
import timetablescheduler.domain.scheduling.constraint.type
import java.time.DayOfWeek
import java.util.UUID

object SchedulingConstraintTable : Table("scheduling_constraint") {
    val id = javaUUID("id")
    val schoolId = javaUUID("school_id").references(SchoolTable.id)
    val type = varchar("type", 40)
    val kind = varchar("kind", 4)
    val weight = varchar("weight", 6).nullable()
    val active = bool("active")
    val allClasses = bool("all_classes")
    val classId = javaUUID("class_id").references(SchoolClassTable.id).nullable()
    val teacherId = javaUUID("teacher_id").references(TeacherTable.id).nullable()
    val subjectId = javaUUID("subject_id").references(SubjectTable.id).nullable()
    val limitValue = integer("limit_value").nullable()
    val acrossBreaks = bool("across_breaks").nullable()
    val version = integer("version")
    override val primaryKey = PrimaryKey(id)
}

object SchedulingConstraintSlotTable : Table("scheduling_constraint_slot") {
    val constraintId = javaUUID("constraint_id").references(SchedulingConstraintTable.id)
    val dayOfWeek = varchar("day_of_week", 9)
    val slotId = javaUUID("slot_id").references(TimeSlotTable.id)
    override val primaryKey = PrimaryKey(constraintId, dayOfWeek, slotId)
}

object TeacherFixationTable : Table("teacher_fixation") {
    val id = javaUUID("id")
    val schoolId = javaUUID("school_id").references(SchoolTable.id)
    val classId = javaUUID("class_id").references(SchoolClassTable.id)
    val subjectId = javaUUID("subject_id").references(SubjectTable.id)
    val teacherId = javaUUID("teacher_id").references(TeacherTable.id)
    val version = integer("version")
    override val primaryKey = PrimaryKey(id)
}

/**
 * A rule spread over the typed columns of a constraint row (G9) — shared by the constraint table and the snapshot
 * of a generation, which store rules the same way.
 */
data class RuleColumns(
    val type: ConstraintType,
    val allClasses: Boolean,
    val classId: UUID?,
    val teacherId: UUID?,
    val subjectId: UUID?,
    val limit: Int?,
    val acrossBreaks: Boolean?,
    val slots: Set<DaySlot>,
) {
    fun toRule(): ConstraintRule {
        // Only class-related types have a scope; teacher and exclusion types have no class or an optional one.
        val scope by lazy { if (allClasses) ClassScope.AllClasses else ClassScope.SpecificClass(SchoolClassId(classId!!)) }
        return when (type) {
            ConstraintType.CLASS_MAX_PERIODS_PER_DAY -> ConstraintRule.ClassMaxPeriodsPerDay(scope, limit!!)
            ConstraintType.CLASS_MIN_PERIODS_PER_DAY -> ConstraintRule.ClassMinPeriodsPerDay(scope, limit!!)
            ConstraintType.CLASS_NO_GAPS -> ConstraintRule.ClassNoGaps(scope)
            ConstraintType.TEACHER_MAX_PERIODS_PER_DAY -> ConstraintRule.TeacherMaxPeriodsPerDay(TeacherId(teacherId!!), limit!!)
            ConstraintType.TEACHER_MAX_GAPS_PER_DAY -> ConstraintRule.TeacherMaxGapsPerDay(TeacherId(teacherId!!), limit!!)
            ConstraintType.SUBJECT_MAX_PER_DAY_IN_CLASS -> ConstraintRule.SubjectMaxPerDayInClass(scope, subjectId?.let(::SubjectId), limit!!)
            ConstraintType.SUBJECT_DOUBLE_PERIODS_IN_CLASS ->
                ConstraintRule.SubjectDoublePeriodsInClass(SchoolClassId(classId!!), SubjectId(subjectId!!), acrossBreaks!!)
            ConstraintType.CLASS_EXCLUDED_SLOTS -> ConstraintRule.ClassExcludedSlots(SchoolClassId(classId!!), slots)
            ConstraintType.TEACHER_EXCLUDED_SLOTS -> ConstraintRule.TeacherExcludedSlots(TeacherId(teacherId!!), slots)
            ConstraintType.SUBJECT_EXCLUDED_SLOTS -> ConstraintRule.SubjectExcludedSlots(SubjectId(subjectId!!), classId?.let(::SchoolClassId), slots)
        }
    }

    companion object {
        fun of(rule: ConstraintRule): RuleColumns {
            val scope = rule.classScope
            val ruleClassId = when (rule) {
                is ConstraintRule.SubjectDoublePeriodsInClass -> rule.classId
                is ConstraintRule.ClassExcludedSlots -> rule.classId
                is ConstraintRule.SubjectExcludedSlots -> rule.classId
                else -> (scope as? ClassScope.SpecificClass)?.classId
            }
            val teacher = when (rule) {
                is ConstraintRule.TeacherMaxPeriodsPerDay -> rule.teacherId
                is ConstraintRule.TeacherMaxGapsPerDay -> rule.teacherId
                is ConstraintRule.TeacherExcludedSlots -> rule.teacherId
                else -> null
            }
            val subject = when (rule) {
                is ConstraintRule.SubjectMaxPerDayInClass -> rule.subjectId
                is ConstraintRule.SubjectDoublePeriodsInClass -> rule.subjectId
                is ConstraintRule.SubjectExcludedSlots -> rule.subjectId
                else -> null
            }
            val limit = when (rule) {
                is ConstraintRule.ClassMaxPeriodsPerDay -> rule.limit
                is ConstraintRule.ClassMinPeriodsPerDay -> rule.limit
                is ConstraintRule.TeacherMaxPeriodsPerDay -> rule.limit
                is ConstraintRule.TeacherMaxGapsPerDay -> rule.limit
                is ConstraintRule.SubjectMaxPerDayInClass -> rule.limit
                else -> null
            }
            return RuleColumns(
                type = rule.type,
                allClasses = scope == ClassScope.AllClasses,
                classId = ruleClassId?.value,
                teacherId = teacher?.value,
                subjectId = subject?.value,
                limit = limit,
                acrossBreaks = (rule as? ConstraintRule.SubjectDoublePeriodsInClass)?.acrossBreaks,
                slots = rule.excludedSlots,
            )
        }

        fun slotOf(day: String, slotId: UUID) = DaySlot(DayOfWeek.valueOf(day), TimeSlotId(slotId))
    }
}
