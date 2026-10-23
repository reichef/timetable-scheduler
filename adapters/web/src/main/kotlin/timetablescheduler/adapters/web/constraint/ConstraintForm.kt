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
import timetablescheduler.domain.scheduling.constraint.classScope
import timetablescheduler.domain.scheduling.constraint.excludedSlots
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek
import java.util.UUID

/** The value of "all classes" or "every subject" in the selects. */
private const val ALL = "ALL"

/** A parsed constraint form: the input for the use case, or the format errors of the form. */
sealed interface ParsedConstraint {
    data class Valid(
        val input: ConstraintInput,
    ) : ParsedConstraint

    data class Invalid(
        val errors: List<ValidationError>,
    ) : ParsedConstraint
}

/**
 * Reads the form of one catalogue type (contracts/web-routes.md). Format problems — a missing selection, a limit that is
 * no number — are reported like business rules; the business rules themselves are checked by the use case (FR-015).
 * "Whole day" checkboxes stand for every teaching slot of that day in [grid].
 */
object ConstraintForm {
    fun parse(
        form: Parameters,
        type: ConstraintType,
        grid: TimeGrid,
    ): ParsedConstraint {
        val errors = mutableListOf<ValidationError>()

        fun uuid(name: String): UUID? =
            form[name]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: null.also { errors += ValidationError.InvalidReference(name) }

        fun scope(): ClassScope? =
            if (form["classId"] == ALL) {
                ClassScope.AllClasses
            } else {
                uuid("classId")?.let { ClassScope.SpecificClass(SchoolClassId(it)) }
            }

        fun optionalClass(): SchoolClassId? =
            if (form["classId"] == ALL || form["classId"].isNullOrBlank()) {
                null
            } else {
                uuid("classId")?.let(::SchoolClassId)
            }

        fun limit(): Int? = form["limit"]?.trim()?.toIntOrNull() ?: null.also { errors += ValidationError.InvalidFormat("limit") }

        fun subjectOrEvery(): SubjectId? = if (form["subjectId"] == ALL) null else uuid("subjectId")?.let(::SubjectId)
        val slots = slotsOf(form, grid)

        val rule: ConstraintRule? =
            when (type) {
                ConstraintType.CLASS_MAX_PERIODS_PER_DAY -> {
                    build(scope(), limit()) { s, l -> ConstraintRule.ClassMaxPeriodsPerDay(s, l) }
                }

                ConstraintType.CLASS_MIN_PERIODS_PER_DAY -> {
                    build(scope(), limit()) { s, l -> ConstraintRule.ClassMinPeriodsPerDay(s, l) }
                }

                ConstraintType.CLASS_NO_GAPS -> {
                    scope()?.let(ConstraintRule::ClassNoGaps)
                }

                ConstraintType.TEACHER_MAX_PERIODS_PER_DAY -> {
                    build(uuid("teacherId")?.let(::TeacherId), limit()) { t, l -> ConstraintRule.TeacherMaxPeriodsPerDay(t, l) }
                }

                ConstraintType.TEACHER_MAX_GAPS_PER_DAY -> {
                    build(uuid("teacherId")?.let(::TeacherId), limit()) { t, l -> ConstraintRule.TeacherMaxGapsPerDay(t, l) }
                }

                ConstraintType.SUBJECT_MAX_PER_DAY_IN_CLASS -> {
                    val scope = scope()
                    val subject = subjectOrEvery()
                    val limit = limit()
                    if (scope != null && limit != null && (subject != null || form["subjectId"] == ALL)) {
                        ConstraintRule.SubjectMaxPerDayInClass(scope, subject, limit)
                    } else {
                        null
                    }
                }

                ConstraintType.SUBJECT_DOUBLE_PERIODS_IN_CLASS -> {
                    build(uuid("classId")?.let(::SchoolClassId), uuid("subjectId")?.let(::SubjectId)) { c, s ->
                        ConstraintRule.SubjectDoublePeriodsInClass(c, s, form["acrossBreaks"] == "true")
                    }
                }

                ConstraintType.CLASS_EXCLUDED_SLOTS -> {
                    uuid("classId")?.let { ConstraintRule.ClassExcludedSlots(SchoolClassId(it), slots) }
                }

                ConstraintType.TEACHER_EXCLUDED_SLOTS -> {
                    uuid("teacherId")?.let { ConstraintRule.TeacherExcludedSlots(TeacherId(it), slots) }
                }

                ConstraintType.SUBJECT_EXCLUDED_SLOTS -> {
                    uuid("subjectId")?.let { ConstraintRule.SubjectExcludedSlots(SubjectId(it), optionalClass(), slots) }
                }
            }
        val kind =
            ConstraintKind.entries.firstOrNull { it.name == form["kind"] } ?: null.also { errors += ValidationError.InvalidFormat("kind") }
        val weight = SoftWeight.entries.firstOrNull { it.name == form["weight"] }
        return if (rule == null || kind == null || errors.isNotEmpty()) {
            ParsedConstraint.Invalid(errors.distinct().ifEmpty { listOf(ValidationError.InvalidFormat("kind")) })
        } else {
            ParsedConstraint.Valid(ConstraintInput(kind, weight, rule))
        }
    }

    fun typeOf(value: String?): ConstraintType? = ConstraintType.entries.firstOrNull { it.name == value }

    /** Checked slots plus all teaching slots of the days checked as "whole day". */
    private fun slotsOf(
        form: Parameters,
        grid: TimeGrid,
    ): Set<DaySlot> {
        val single =
            form.getAll("slot").orEmpty().mapNotNull { value ->
                val day = DayOfWeek.entries.firstOrNull { it.name == value.substringBefore(':') } ?: return@mapNotNull null
                val slotId = runCatching { UUID.fromString(value.substringAfter(':')) }.getOrNull() ?: return@mapNotNull null
                DaySlot(day, TimeSlotId(slotId))
            }
        val wholeDays =
            form
                .getAll("wholeDay")
                .orEmpty()
                .mapNotNull { day -> DayOfWeek.entries.firstOrNull { it.name == day } }
                .flatMap { day -> grid.teachingSlotsOn(day).map { DaySlot(day, it.id) } }
        return (single + wholeDays).toSet()
    }

    private inline fun <A : Any, B : Any> build(
        first: A?,
        second: B?,
        create: (A, B) -> ConstraintRule,
    ): ConstraintRule? = if (first != null && second != null) create(first, second) else null
}

/** The form fields of a stored rule, so the edit form shows it like entered values. */
object ConstraintFormValues {
    fun of(rule: ConstraintRule): Map<String, List<String>> =
        buildMap {
            when (val scope = rule.classScope) {
                ClassScope.AllClasses -> put("classId", listOf(ALL))
                is ClassScope.SpecificClass -> put("classId", listOf("${scope.classId.value}"))
                null -> Unit
            }
            when (rule) {
                is ConstraintRule.ClassMaxPeriodsPerDay -> {
                    put("limit", listOf("${rule.limit}"))
                }

                is ConstraintRule.ClassMinPeriodsPerDay -> {
                    put("limit", listOf("${rule.limit}"))
                }

                is ConstraintRule.TeacherMaxPeriodsPerDay -> {
                    put("teacherId", listOf("${rule.teacherId.value}"))
                    put("limit", listOf("${rule.limit}"))
                }

                is ConstraintRule.TeacherMaxGapsPerDay -> {
                    put("teacherId", listOf("${rule.teacherId.value}"))
                    put("limit", listOf("${rule.limit}"))
                }

                is ConstraintRule.SubjectMaxPerDayInClass -> {
                    put("subjectId", listOf(rule.subjectId?.value?.toString() ?: ALL))
                    put("limit", listOf("${rule.limit}"))
                }

                is ConstraintRule.SubjectDoublePeriodsInClass -> {
                    put("classId", listOf("${rule.classId.value}"))
                    put("subjectId", listOf("${rule.subjectId.value}"))
                    if (rule.acrossBreaks) {
                        put("acrossBreaks", listOf("true"))
                    }
                }

                is ConstraintRule.ClassExcludedSlots -> {
                    put("classId", listOf("${rule.classId.value}"))
                }

                is ConstraintRule.TeacherExcludedSlots -> {
                    put("teacherId", listOf("${rule.teacherId.value}"))
                }

                is ConstraintRule.SubjectExcludedSlots -> {
                    put("subjectId", listOf("${rule.subjectId.value}"))
                    put("classId", listOf(rule.classId?.value?.toString() ?: ALL))
                }

                is ConstraintRule.ClassNoGaps -> {
                    Unit
                }
            }
            if (rule.excludedSlots.isNotEmpty()) {
                put("slot", rule.excludedSlots.map { "${it.day.name}:${it.slotId.value}" })
            }
        }
}
