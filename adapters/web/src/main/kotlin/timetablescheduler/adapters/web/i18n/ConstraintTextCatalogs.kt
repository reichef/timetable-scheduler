package timetablescheduler.adapters.web.i18n

import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.ConstraintType
import timetablescheduler.domain.scheduling.constraint.FixationInvalidity
import timetablescheduler.domain.scheduling.constraint.SoftWeight

/** German constraint texts (002 US2); the reference wording with the school terminology. */
internal object GermanConstraintTexts : ConstraintTexts {
    override val title = "Bedingungen"
    override val catalogueHeading = "Neue Bedingung"
    override val fixationsHeading = "Festgelegte Lehrkräfte"
    override val newFixation = "Lehrkraft für ein Fach einer Klasse festlegen"
    override val noConstraints = "Noch keine Bedingungen."
    override val noFixations = "Keine Lehrkraft festgelegt; der Scheduler wählt alle Lehrkräfte selbst."
    override val switchedOff = "ausgeschaltet"
    override val switchOff = "Ausschalten"
    override val switchOn = "Einschalten"
    override val invalidMark = "ungültig"
    override val kindLabel = "Art"
    override val weightLabel = "Gewicht"
    override val classLabel = "Klasse"
    override val allClasses = "alle Klassen"
    override val teacherLabel = "Lehrkraft"
    override val subjectLabel = "Fach"
    override val everySubject = "jedes Fach"
    override val noClass = "alle Klassen"
    override val limitLabel = "Grenzwert"
    override val acrossBreaksLabel = "Doppelstunde auch über eine Pause"
    override val slotsLabel = "Stunden"
    override val wholeDay = "ganzer Tag"
    override val toConstraints = "Zu den Bedingungen"
    override val contradictionTitle = "Bedingungen widersprechen sich"
    override val contradictionIntro = "Diese Muss-Bedingungen lassen sich mit den Stammdaten nie erfüllen:"
    override val inEffectHeading = "Bedingungen bei Start"
    override val separateObjectivesLegend = "Soll-Bedingungen gesondert vergleichen (höchstens 3)"
    override val fixationTitle = "Festgelegte Lehrkraft"
    override val deleteConstraint = "Bedingung löschen"
    override val deleteFixation = "Festlegung löschen"

    override fun typeName(type: ConstraintType) =
        when (type) {
            ConstraintType.CLASS_MAX_PERIODS_PER_DAY -> "höchstens Stunden pro Tag (Klasse)"
            ConstraintType.CLASS_MIN_PERIODS_PER_DAY -> "mindestens Stunden pro Tag (Klasse)"
            ConstraintType.CLASS_NO_GAPS -> "keine Freistunden (Klasse)"
            ConstraintType.TEACHER_MAX_PERIODS_PER_DAY -> "höchstens Stunden pro Tag (Lehrkraft)"
            ConstraintType.TEACHER_MAX_GAPS_PER_DAY -> "höchstens Freistunden pro Tag (Lehrkraft)"
            ConstraintType.SUBJECT_MAX_PER_DAY_IN_CLASS -> "Fach höchstens Stunden pro Tag"
            ConstraintType.SUBJECT_DOUBLE_PERIODS_IN_CLASS -> "Doppelstunde"
            ConstraintType.CLASS_EXCLUDED_SLOTS -> "Klasse nicht in bestimmten Stunden"
            ConstraintType.TEACHER_EXCLUDED_SLOTS -> "Lehrkraft frei in bestimmten Stunden"
            ConstraintType.SUBJECT_EXCLUDED_SLOTS -> "Fach nicht in bestimmten Stunden"
        }

    override fun kindName(kind: ConstraintKind) =
        when (kind) {
            ConstraintKind.HARD -> "Muss"
            ConstraintKind.SOFT -> "Soll"
        }

    override fun weightName(weight: SoftWeight) =
        when (weight) {
            SoftWeight.LOW -> "niedrig"
            SoftWeight.MEDIUM -> "mittel"
            SoftWeight.HIGH -> "hoch"
        }

    override fun newConstraintTitle(type: ConstraintType) = "Neue Bedingung: ${typeName(type)}"

    private fun periods(count: Int) = if (count == 1) "1 Stunde" else "$count Stunden"

    private fun scope(
        scope: ClassScope,
        names: RuleNames,
    ) = when (scope) {
        ClassScope.AllClasses -> "Alle Klassen"
        is ClassScope.SpecificClass -> "Klasse ${names.className(scope.classId)}"
    }

    override fun ruleText(
        rule: ConstraintRule,
        kind: ConstraintKind,
        names: RuleNames,
    ) = when (rule) {
        is ConstraintRule.ClassMaxPeriodsPerDay -> {
            "${scope(rule.scope, names)}: höchstens ${periods(rule.limit)} pro Tag"
        }

        is ConstraintRule.ClassMinPeriodsPerDay -> {
            "${scope(rule.scope, names)}: mindestens ${periods(rule.limit)} pro Tag"
        }

        is ConstraintRule.ClassNoGaps -> {
            "${scope(rule.scope, names)}: keine Freistunden"
        }

        is ConstraintRule.TeacherMaxPeriodsPerDay -> {
            "Lehrkraft ${names.teacherShortCode(rule.teacherId)}: höchstens ${periods(rule.limit)} pro Tag"
        }

        is ConstraintRule.TeacherMaxGapsPerDay -> {
            val teacher = names.teacherShortCode(rule.teacherId)

            "Lehrkraft $teacher: höchstens ${rule.limit} ${if (rule.limit == 1) "Freistunde" else "Freistunden"} pro Tag"
        }

        is ConstraintRule.SubjectMaxPerDayInClass -> {
            val subject = rule.subjectId?.let(names.subjectName) ?: everySubject

            "${scope(rule.scope, names)}: $subject höchstens ${periods(rule.limit)} pro Tag"
        }

        is ConstraintRule.SubjectDoublePeriodsInClass -> {
            "Klasse ${names.className(rule.classId)}: ${names.subjectName(rule.subjectId)} als Doppelstunde" +
                if (rule.acrossBreaks) " (auch über Pausen)" else ""
        }

        is ConstraintRule.ClassExcludedSlots -> {
            "Klasse ${names.className(rule.classId)}: nicht in ${slotList(rule.slots, names)}"
        }

        is ConstraintRule.TeacherExcludedSlots -> {
            val teacher = names.teacherShortCode(rule.teacherId)

            "Lehrkraft $teacher: ${if (kind == ConstraintKind.SOFT) "frei" else "nicht in"} ${slotList(rule.slots, names)}"
        }

        is ConstraintRule.SubjectExcludedSlots -> {
            val classes = rule.classId?.let { "Klasse ${names.className(it)}" } ?: allClasses

            "${names.subjectName(rule.subjectId)} ($classes): nicht in ${slotList(rule.slots, names)}"
        }
    }

    override fun fixationText(
        className: String,
        subjectName: String,
        teacherShortCode: String,
    ) = "$className $subjectName: $teacherShortCode"

    override fun invalidityReason(reason: FixationInvalidity) =
        when (reason) {
            FixationInvalidity.NOT_IN_CURRICULUM -> "das Fach steht nicht mehr im Lehrplan der Klasse"
            FixationInvalidity.NOT_QUALIFIED -> "die Lehrkraft ist für das Fach nicht mehr qualifiziert"
            FixationInvalidity.EXCEEDS_LOAD -> "die festgelegten Stunden übersteigen das Deputat"
        }
}

/** English constraint texts (002 US2). */
internal object EnglishConstraintTexts : ConstraintTexts {
    override val title = "Constraints"
    override val catalogueHeading = "New constraint"
    override val fixationsHeading = "Fixed teachers"
    override val newFixation = "Fix the teacher of a subject in a class"
    override val noConstraints = "No constraints yet."
    override val noFixations = "No teacher fixed; the scheduler chooses all teachers itself."
    override val switchedOff = "switched off"
    override val switchOff = "Switch off"
    override val switchOn = "Switch on"
    override val invalidMark = "invalid"
    override val kindLabel = "Type"
    override val weightLabel = "Weight"
    override val classLabel = "Class"
    override val allClasses = "all classes"
    override val teacherLabel = "Teacher"
    override val subjectLabel = "Subject"
    override val everySubject = "every subject"
    override val noClass = "all classes"
    override val limitLabel = "Limit"
    override val acrossBreaksLabel = "Double period also across a break"
    override val slotsLabel = "Periods"
    override val wholeDay = "whole day"
    override val toConstraints = "To the constraints"
    override val contradictionTitle = "Constraints contradict each other"
    override val contradictionIntro = "These hard constraints can never be met with the master data:"
    override val inEffectHeading = "Constraints at start"
    override val separateObjectivesLegend = "Compare soft constraints separately (at most 3)"
    override val fixationTitle = "Fixed teacher"
    override val deleteConstraint = "Delete constraint"
    override val deleteFixation = "Delete fixation"

    override fun typeName(type: ConstraintType) =
        when (type) {
            ConstraintType.CLASS_MAX_PERIODS_PER_DAY -> "at most periods per day (class)"
            ConstraintType.CLASS_MIN_PERIODS_PER_DAY -> "at least periods per day (class)"
            ConstraintType.CLASS_NO_GAPS -> "no free periods (class)"
            ConstraintType.TEACHER_MAX_PERIODS_PER_DAY -> "at most periods per day (teacher)"
            ConstraintType.TEACHER_MAX_GAPS_PER_DAY -> "at most free periods per day (teacher)"
            ConstraintType.SUBJECT_MAX_PER_DAY_IN_CLASS -> "subject at most periods per day"
            ConstraintType.SUBJECT_DOUBLE_PERIODS_IN_CLASS -> "double periods"
            ConstraintType.CLASS_EXCLUDED_SLOTS -> "class not in certain periods"
            ConstraintType.TEACHER_EXCLUDED_SLOTS -> "teacher free in certain periods"
            ConstraintType.SUBJECT_EXCLUDED_SLOTS -> "subject not in certain periods"
        }

    override fun kindName(kind: ConstraintKind) =
        when (kind) {
            ConstraintKind.HARD -> "hard"
            ConstraintKind.SOFT -> "soft"
        }

    override fun weightName(weight: SoftWeight) =
        when (weight) {
            SoftWeight.LOW -> "low"
            SoftWeight.MEDIUM -> "medium"
            SoftWeight.HIGH -> "high"
        }

    override fun newConstraintTitle(type: ConstraintType) = "New constraint: ${typeName(type)}"

    private fun periods(count: Int) = if (count == 1) "1 period" else "$count periods"

    private fun scope(
        scope: ClassScope,
        names: RuleNames,
    ) = when (scope) {
        ClassScope.AllClasses -> "All classes"
        is ClassScope.SpecificClass -> "Class ${names.className(scope.classId)}"
    }

    override fun ruleText(
        rule: ConstraintRule,
        kind: ConstraintKind,
        names: RuleNames,
    ) = when (rule) {
        is ConstraintRule.ClassMaxPeriodsPerDay -> {
            "${scope(rule.scope, names)}: at most ${periods(rule.limit)} per day"
        }

        is ConstraintRule.ClassMinPeriodsPerDay -> {
            "${scope(rule.scope, names)}: at least ${periods(rule.limit)} per day"
        }

        is ConstraintRule.ClassNoGaps -> {
            "${scope(rule.scope, names)}: no free periods"
        }

        is ConstraintRule.TeacherMaxPeriodsPerDay -> {
            "Teacher ${names.teacherShortCode(rule.teacherId)}: at most ${periods(rule.limit)} per day"
        }

        is ConstraintRule.TeacherMaxGapsPerDay -> {
            val teacher = names.teacherShortCode(rule.teacherId)

            "Teacher $teacher: at most ${rule.limit} ${if (rule.limit == 1) "free period" else "free periods"} per day"
        }

        is ConstraintRule.SubjectMaxPerDayInClass -> {
            val subject = rule.subjectId?.let(names.subjectName) ?: everySubject

            "${scope(rule.scope, names)}: $subject at most ${periods(rule.limit)} per day"
        }

        is ConstraintRule.SubjectDoublePeriodsInClass -> {
            "Class ${names.className(rule.classId)}: ${names.subjectName(rule.subjectId)} as double periods" +
                if (rule.acrossBreaks) " (also across breaks)" else ""
        }

        is ConstraintRule.ClassExcludedSlots -> {
            "Class ${names.className(rule.classId)}: not in ${slotList(rule.slots, names)}"
        }

        is ConstraintRule.TeacherExcludedSlots -> {
            val teacher = names.teacherShortCode(rule.teacherId)

            "Teacher $teacher: ${if (kind == ConstraintKind.SOFT) "free" else "not in"} ${slotList(rule.slots, names)}"
        }

        is ConstraintRule.SubjectExcludedSlots -> {
            val classes = rule.classId?.let { "class ${names.className(it)}" } ?: allClasses

            "${names.subjectName(rule.subjectId)} ($classes): not in ${slotList(rule.slots, names)}"
        }
    }

    override fun fixationText(
        className: String,
        subjectName: String,
        teacherShortCode: String,
    ) = "$className $subjectName: $teacherShortCode"

    override fun invalidityReason(reason: FixationInvalidity) =
        when (reason) {
            FixationInvalidity.NOT_IN_CURRICULUM -> "the subject is no longer in the class's curriculum"
            FixationInvalidity.NOT_QUALIFIED -> "the teacher is no longer qualified for the subject"
            FixationInvalidity.EXCEEDS_LOAD -> "the fixed periods exceed the teaching load"
        }
}
