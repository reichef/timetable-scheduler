package timetablescheduler.adapters.web.i18n

import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.ConstraintType
import timetablescheduler.domain.scheduling.constraint.FixationInvalidity
import timetablescheduler.domain.scheduling.constraint.SoftWeight

/** The names a constraint description needs, already resolved (names as entered, deleted entries marked). */
class RuleNames(
    val className: (SchoolClassId) -> String,
    val teacherShortCode: (TeacherId) -> String,
    val subjectName: (SubjectId) -> String,
    val slotName: (DaySlot) -> String,
    val slotOrder: (DaySlot) -> Int,
)

/** Constraints and teacher fixations (002 US2). */
interface ConstraintTexts {
    val title: String
    val catalogueHeading: String
    val fixationsHeading: String
    val newFixation: String
    val noConstraints: String
    val noFixations: String
    val switchedOff: String
    val switchOff: String
    val switchOn: String
    val invalidMark: String
    val kindLabel: String
    val weightLabel: String
    val classLabel: String
    val allClasses: String
    val teacherLabel: String
    val subjectLabel: String
    val everySubject: String
    val noClass: String
    val limitLabel: String
    val acrossBreaksLabel: String
    val slotsLabel: String
    val wholeDay: String
    val toConstraints: String
    val contradictionTitle: String
    val contradictionIntro: String
    val inEffectHeading: String
    val separateObjectivesLegend: String
    val fixationTitle: String
    val deleteConstraint: String
    val deleteFixation: String
    fun typeName(type: ConstraintType): String
    fun kindName(kind: ConstraintKind): String
    fun weightName(weight: SoftWeight): String
    fun newConstraintTitle(type: ConstraintType): String
    fun ruleText(rule: ConstraintRule, kind: ConstraintKind, names: RuleNames): String
    fun fixationText(className: String, subjectName: String, teacherShortCode: String): String
    fun invalidityReason(reason: FixationInvalidity): String

    /** "– Muss" / "– Soll (mittel)" after a description. */
    fun kindSuffix(kind: ConstraintKind, weight: SoftWeight?): String =
        if (kind == ConstraintKind.SOFT && weight != null) " – ${kindName(kind)} (${weightName(weight)})" else " – ${kindName(kind)}"
}

/** Lists slots in week order, e.g. "Dienstag 1. Stunde, Dienstag 2. Stunde". */
internal fun slotList(slots: Set<DaySlot>, names: RuleNames): String =
    slots.sortedBy(names.slotOrder).joinToString(", ") { names.slotName(it) }
