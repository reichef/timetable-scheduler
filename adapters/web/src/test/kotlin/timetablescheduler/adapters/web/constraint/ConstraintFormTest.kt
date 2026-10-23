package timetablescheduler.adapters.web.constraint

import io.ktor.http.Parameters
import timetablescheduler.application.scheduling.ConstraintInput
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.ConstraintType
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import timetablescheduler.domain.scheduling.constraint.type
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import kotlin.test.Test
import kotlin.test.assertEquals

/** The constraint form reads every catalogue type, and the edit form shows a stored rule as it was entered (FR-015). */
class ConstraintFormTest {
    private val schoolClass = SchoolClassId.new()
    private val subject = SubjectId.new()
    private val teacher = TeacherId.new()
    private val slots = setOf(DaySlot(MONDAY, TimeSlotId.new()), DaySlot(FRIDAY, TimeSlotId.new()))

    private fun form(
        fields: Map<String, List<String>>,
        kind: String = "SOFT",
    ) = Parameters.build {
        fields.forEach { (name, values) -> appendAll(name, values) }
        append("kind", kind)
        append("weight", "HIGH")
    }

    private fun parse(
        fields: Map<String, List<String>>,
        type: ConstraintType,
    ) = ConstraintForm.parse(form(fields), type, TimeGrid.empty())

    @Test
    fun `the edit values of every catalogue type are read back as the same rule`() {
        val rules =
            listOf(
                ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(schoolClass), 6),
                ConstraintRule.ClassMinPeriodsPerDay(ClassScope.AllClasses, 4),
                ConstraintRule.ClassNoGaps(ClassScope.AllClasses),
                ConstraintRule.TeacherMaxPeriodsPerDay(teacher, 5),
                ConstraintRule.TeacherMaxGapsPerDay(teacher, 1),
                ConstraintRule.SubjectMaxPerDayInClass(ClassScope.SpecificClass(schoolClass), subject, 2),
                ConstraintRule.SubjectMaxPerDayInClass(ClassScope.AllClasses, null, 3),
                ConstraintRule.SubjectDoublePeriodsInClass(schoolClass, subject, acrossBreaks = true),
                ConstraintRule.ClassExcludedSlots(schoolClass, slots),
                ConstraintRule.TeacherExcludedSlots(teacher, slots),
                ConstraintRule.SubjectExcludedSlots(subject, schoolClass, slots),
                ConstraintRule.SubjectExcludedSlots(subject, null, slots),
            )

        rules.forEach { rule ->
            val parsed = parse(ConstraintFormValues.of(rule), rule.type)

            assertEquals(ParsedConstraint.Valid(ConstraintInput(ConstraintKind.SOFT, SoftWeight.HIGH, rule)), parsed, "$rule")
        }
    }

    @Test
    fun `a limit that is no number and a missing teacher are reported as format errors`() {
        val noNumber = parse(mapOf("classId" to listOf("ALL"), "limit" to listOf("drei")), ConstraintType.CLASS_MAX_PERIODS_PER_DAY)
        val noTeacher = parse(mapOf("limit" to listOf("2")), ConstraintType.TEACHER_MAX_GAPS_PER_DAY)

        assertEquals(ParsedConstraint.Invalid(listOf(ValidationError.InvalidFormat("limit"))), noNumber)
        assertEquals(ParsedConstraint.Invalid(listOf(ValidationError.InvalidReference("teacherId"))), noTeacher)
    }
}
