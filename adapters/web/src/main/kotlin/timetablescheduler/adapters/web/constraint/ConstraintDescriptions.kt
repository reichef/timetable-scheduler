package timetablescheduler.adapters.web.constraint

import timetablescheduler.adapters.web.i18n.RuleNames
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.application.scheduling.TimetableNames
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.TeacherFixation

fun constraintsPath(schoolId: SchoolId) = "/schools/${schoolId.value}/constraints"

fun constraintPath(constraint: SchedulingConstraint) = "${constraintsPath(constraint.schoolId)}/${constraint.id.value}"

fun fixationsPath(schoolId: SchoolId) = "/schools/${schoolId.value}/teacher-fixations"

/** Resolves ids to the names shown in descriptions; entries deleted since are named as deleted (001 FR-044). */
fun ruleNamesOf(names: TimetableNames, texts: UiTexts): RuleNames {
    val deleted = texts.timetables.deletedEntry
    val slots = names.timeGrid.slots.sortedBy { it.startTime }
    return RuleNames(
        className = { names.classNames[it] ?: deleted },
        teacherShortCode = { names.teacherShortCodes[it] ?: deleted },
        subjectName = { names.subjectNames[it] ?: deleted },
        slotName = { daySlot -> "${texts.common.dayName(daySlot.day)} ${slots.firstOrNull { it.id == daySlot.slotId }?.label ?: deleted}" },
        // Day first, then the slot's position in the grid; slots no longer in the grid come last on their day.
        slotOrder = { daySlot: DaySlot -> daySlot.day.ordinal * 1000 + slots.indexOfFirst { it.id == daySlot.slotId }.let { if (it < 0) 999 else it } },
    )
}

/** "Klasse 6b: höchstens 6 Stunden pro Tag – Muss" (US2 scenario 1). */
fun describe(constraint: SchedulingConstraint, names: TimetableNames, texts: UiTexts): String =
    texts.constraints.ruleText(constraint.rule, constraint.kind, ruleNamesOf(names, texts)) + texts.constraints.kindSuffix(constraint.kind, constraint.weight)

/** The description without kind and weight, e.g. as heading of a separately compared objective. */
fun describeRule(constraint: SchedulingConstraint, names: TimetableNames, texts: UiTexts): String =
    texts.constraints.ruleText(constraint.rule, constraint.kind, ruleNamesOf(names, texts))

/** "6b Mathematik: SCHA". */
fun describe(fixation: TeacherFixation, names: TimetableNames, texts: UiTexts): String {
    val deleted = texts.timetables.deletedEntry
    return texts.constraints.fixationText(
        names.classNames[fixation.classId] ?: deleted,
        names.subjectNames[fixation.subjectId] ?: deleted,
        names.teacherShortCodes[fixation.teacherId] ?: deleted,
    )
}
