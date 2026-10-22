package timetablescheduler.adapters.web.constraint

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FORM
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.ul
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.hiddenField
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.layout.validationErrors
import timetablescheduler.application.scheduling.ConstraintOverview
import timetablescheduler.application.scheduling.TimetableNames
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintType
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.validation.ValidationError

/** Which fields a catalogue type needs on its form (G9). */
private val ConstraintType.classChoice: ClassChoice
    get() =
        when (this) {
            ConstraintType.CLASS_MAX_PERIODS_PER_DAY, ConstraintType.CLASS_MIN_PERIODS_PER_DAY, ConstraintType.CLASS_NO_GAPS,
            ConstraintType.SUBJECT_MAX_PER_DAY_IN_CLASS, ConstraintType.SUBJECT_EXCLUDED_SLOTS,
            -> ClassChoice.ONE_OR_ALL

            ConstraintType.SUBJECT_DOUBLE_PERIODS_IN_CLASS, ConstraintType.CLASS_EXCLUDED_SLOTS -> ClassChoice.ONE

            else -> ClassChoice.NONE
        }

private enum class ClassChoice { NONE, ONE, ONE_OR_ALL }

private val ConstraintType.needsTeacher get() =
    this in
        setOf(ConstraintType.TEACHER_MAX_PERIODS_PER_DAY, ConstraintType.TEACHER_MAX_GAPS_PER_DAY, ConstraintType.TEACHER_EXCLUDED_SLOTS)
private val ConstraintType.needsSubject get() =
    this in
        setOf(
            ConstraintType.SUBJECT_MAX_PER_DAY_IN_CLASS,
            ConstraintType.SUBJECT_DOUBLE_PERIODS_IN_CLASS,
            ConstraintType.SUBJECT_EXCLUDED_SLOTS,
        )
private val ConstraintType.allowsEverySubject get() = this == ConstraintType.SUBJECT_MAX_PER_DAY_IN_CLASS
private val ConstraintType.needsLimit get() =
    this in
        setOf(
            ConstraintType.CLASS_MAX_PERIODS_PER_DAY,
            ConstraintType.CLASS_MIN_PERIODS_PER_DAY,
            ConstraintType.TEACHER_MAX_PERIODS_PER_DAY,
            ConstraintType.TEACHER_MAX_GAPS_PER_DAY,
            ConstraintType.SUBJECT_MAX_PER_DAY_IN_CLASS,
        )
private val ConstraintType.needsSlots get() =
    this in
        setOf(ConstraintType.CLASS_EXCLUDED_SLOTS, ConstraintType.TEACHER_EXCLUDED_SLOTS, ConstraintType.SUBJECT_EXCLUDED_SLOTS)

/** The constraint list with the catalogue (FR-012, FR-013) and the teacher fixations (FR-013a). */
suspend fun ApplicationCall.respondConstraintList(overview: ConstraintOverview) {
    val texts = texts
    respondPage(texts.constraints.title) { session ->
        if (overview.constraints.isEmpty()) {
            p { +texts.constraints.noConstraints }
        }
        table {
            overview.constraints.forEach { constraint ->
                tr {
                    td {
                        a(href = constraintPath(constraint)) { +describe(constraint, overview.names, texts) }
                        if (!constraint.active) {
                            span(classes = "muted") { +" (${texts.constraints.switchedOff})" }
                        }
                    }
                    td {
                        form(action = "${constraintPath(constraint)}/active", method = FormMethod.post, classes = "inline") {
                            csrfField(session)
                            hiddenField("version", "${constraint.version}")
                            hiddenField("active", "${!constraint.active}")
                            button { +if (constraint.active) texts.constraints.switchOff else texts.constraints.switchOn }
                        }
                    }
                }
            }
        }

        h2 { +texts.constraints.catalogueHeading }
        ul {
            ConstraintType.entries.forEach { type ->
                li { a(href = "${constraintsPath(overview.schoolId)}/new?type=${type.name}") { +texts.constraints.typeName(type) } }
            }
        }

        h2 { +texts.constraints.fixationsHeading }
        if (overview.fixations.isEmpty()) {
            p { +texts.constraints.noFixations }
        }
        ul {
            overview.fixations.forEach { fixation ->
                li {
                    a(href = "${fixationsPath(overview.schoolId)}/${fixation.id.value}") { +describe(fixation, overview.names, texts) }
                    overview.invalidFixations[fixation.id]?.let { reason ->
                        span(classes = "errors") { +" – ${texts.constraints.invalidMark}: ${texts.constraints.invalidityReason(reason)}" }
                    }
                }
            }
        }
        p { a(href = "${fixationsPath(overview.schoolId)}/new") { +texts.constraints.newFixation } }

        p { a(href = "/schools/${overview.schoolId.value}") { +texts.common.toSchool } }
    }
}

/**
 * The form of one catalogue type for a new or an existing constraint; after a rejected save it shows the entered
 * values ([entered]) with the messages (FR-015).
 */
suspend fun ApplicationCall.respondConstraintForm(
    overview: ConstraintOverview,
    type: ConstraintType,
    existing: SchedulingConstraint? = null,
    entered: Parameters? = null,
    errors: List<ValidationError> = emptyList(),
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    val values = entered ?: existing?.let(::formValuesOf) ?: Parameters.Empty
    val title = existing?.let { describe(it, overview.names, texts) } ?: texts.constraints.newConstraintTitle(type)
    respondPage(title, status) { session ->
        validationErrors(errors, texts)
        form(action = existing?.let(::constraintPath) ?: constraintsPath(overview.schoolId), method = FormMethod.post) {
            csrfField(session)
            hiddenField("type", type.name)
            if (existing != null) {
                hiddenField("version", "${existing.version}")
            }
            constraintFields(overview, type, values, texts)
            button { +texts.common.save }
        }
        if (existing != null) {
            form(action = "${constraintPath(existing)}/delete", method = FormMethod.post) {
                csrfField(session)
                hiddenField("version", "${existing.version}")
                button(classes = "danger") { +texts.constraints.deleteConstraint }
            }
        }
        p { a(href = constraintsPath(overview.schoolId)) { +texts.constraints.toConstraints } }
    }
}

private fun FORM.constraintFields(
    overview: ConstraintOverview,
    type: ConstraintType,
    values: Parameters,
    texts: UiTexts,
) {
    val names = overview.names

    when (type.classChoice) {
        ClassChoice.NONE -> {
            Unit
        }

        ClassChoice.ONE -> {
            choice("classId", texts.constraints.classLabel, classOptions(names), values["classId"], allLabel = null)
        }

        ClassChoice.ONE_OR_ALL -> {
            choice(
                "classId",
                texts.constraints.classLabel,
                classOptions(names),
                values["classId"] ?: ALL,
                allLabel = texts.constraints.allClasses,
            )
        }
    }
    if (type.needsTeacher) {
        choice("teacherId", texts.constraints.teacherLabel, teacherOptions(names), values["teacherId"], allLabel = null)
    }
    if (type.needsSubject) {
        val everySubject = if (type.allowsEverySubject) texts.constraints.everySubject else null
        choice(
            "subjectId",
            texts.constraints.subjectLabel,
            subjectOptions(names),
            values["subjectId"] ?: everySubject?.let { ALL },
            everySubject,
        )
    }

    if (type.needsLimit) {
        label {
            +texts.constraints.limitLabel
            input(type = InputType.number, name = "limit") { value = values["limit"].orEmpty() }
        }
    }
    if (type == ConstraintType.SUBJECT_DOUBLE_PERIODS_IN_CLASS) {
        label {
            input(type = InputType.checkBox, name = "acrossBreaks") {
                value = "true"
                checked = values["acrossBreaks"] == "true"
            }
            +texts.constraints.acrossBreaksLabel
        }
    }
    if (type.needsSlots) {
        slotGrid(overview, values.getAll("slot").orEmpty().toSet(), texts)
    }

    kindAndWeight(values, texts)
}

private const val ALL = "ALL"

private fun classOptions(names: TimetableNames) =
    names.classNames.map { (id, name) -> "${id.value}" to name }.sortedBy {
        it.second.lowercase()
    }

private fun teacherOptions(names: TimetableNames) =
    names.teacherNames.map { (id, name) -> "${id.value}" to name }.sortedBy {
        it.second.lowercase()
    }

private fun subjectOptions(names: TimetableNames) =
    names.subjectNames.map { (id, name) -> "${id.value}" to name }.sortedBy {
        it.second.lowercase()
    }

private fun FORM.choice(
    name: String,
    labelText: String,
    options: List<Pair<String, String>>,
    selectedValue: String?,
    allLabel: String?,
) {
    label {
        +labelText
        select {
            this.name = name
            if (allLabel != null) {
                option {
                    value = ALL
                    selected = selectedValue == ALL
                    +allLabel
                }
            }
            options.forEach { (id, optionName) ->
                option {
                    value = id
                    selected = selectedValue == id
                    +optionName
                }
            }
        }
    }
}

/** Teaching days × teaching slots as checkboxes, plus one "whole day" checkbox per day. */
private fun FORM.slotGrid(
    overview: ConstraintOverview,
    checked: Set<String>,
    texts: UiTexts,
) {
    val grid = overview.names.timeGrid
    val days = grid.teachingDays.sorted()
    fieldSet {
        legend { +texts.constraints.slotsLabel }
        table {
            tr {
                th { }
                days.forEach { day ->
                    th {
                        +texts.common.dayName(day)
                        label {
                            input(type = InputType.checkBox, name = "wholeDay") { value = day.name }
                            +texts.constraints.wholeDay
                        }
                    }
                }
            }
            grid.slots.sortedBy { it.startTime }.filter { it.kind == SlotKind.TEACHING }.forEach { slot ->
                tr {
                    th { +slot.label }
                    days.forEach { day ->
                        td {
                            if (grid.teachingSlotsOn(day).any { it.id == slot.id }) {
                                val slotValue = "${day.name}:${slot.id.value}"
                                input(type = InputType.checkBox, name = "slot") {
                                    value = slotValue
                                    this.checked = slotValue in checked
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FORM.kindAndWeight(
    values: Parameters,
    texts: UiTexts,
) {
    fieldSet {
        legend { +texts.constraints.kindLabel }
        ConstraintKind.entries.forEach { kind ->
            label {
                input(type = InputType.radio, name = "kind") {
                    value = kind.name
                    checked = (values["kind"] ?: ConstraintKind.SOFT.name) == kind.name
                }
                +texts.constraints.kindName(kind)
            }
        }
    }
    label {
        +texts.constraints.weightLabel
        select {
            name = "weight"
            SoftWeight.entries.forEach { weight ->
                option {
                    value = weight.name
                    selected = (values["weight"] ?: SoftWeight.MEDIUM.name) == weight.name
                    +texts.constraints.weightName(weight)
                }
            }
        }
    }
}

/** The form values of a stored constraint, so the edit form shows them like entered ones. */
private fun formValuesOf(constraint: SchedulingConstraint): Parameters =
    Parameters.build {
        val columns = ConstraintFormValues.of(constraint.rule)
        columns.forEach { (name, values) -> values.forEach { append(name, it) } }
        append("kind", constraint.kind.name)
        constraint.weight?.let { append("weight", it.name) }
    }

/** A teacher fixation's page: change the teacher or delete it (FR-013a). */
suspend fun ApplicationCall.respondFixationForm(
    overview: ConstraintOverview,
    existing: TeacherFixation?,
    entered: Parameters? = null,
    errors: List<ValidationError> = emptyList(),
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    val names = overview.names
    val values =
        entered ?: existing?.let {
            Parameters.build {
                append("classId", "${it.classId.value}")
                append("subjectId", "${it.subjectId.value}")
                append("teacherId", "${it.teacherId.value}")
            }
        } ?: Parameters.Empty
    respondPage(existing?.let { describe(it, names, texts) } ?: texts.constraints.newFixation, status) { session ->
        validationErrors(errors, texts)
        form(
            action =
                existing?.let {
                    "${fixationsPath(overview.schoolId)}/${it.id.value}"
                } ?: fixationsPath(overview.schoolId),
            method = FormMethod.post,
        ) {
            csrfField(session)
            if (existing == null) {
                choice("classId", texts.constraints.classLabel, classOptions(names), values["classId"], allLabel = null)
                choice("subjectId", texts.constraints.subjectLabel, subjectOptions(names), values["subjectId"], allLabel = null)
            } else {
                hiddenField("version", "${existing.version}")
            }
            choice("teacherId", texts.constraints.teacherLabel, teacherOptions(names), values["teacherId"], allLabel = null)
            button { +texts.common.save }
        }
        if (existing != null) {
            form(action = "${fixationsPath(overview.schoolId)}/${existing.id.value}/delete", method = FormMethod.post) {
                csrfField(session)
                hiddenField("version", "${existing.version}")
                button(classes = "danger") { +texts.constraints.deleteFixation }
            }
        }
        p { a(href = constraintsPath(overview.schoolId)) { +texts.constraints.toConstraints } }
    }
}

/** Constraints that can never be met (FR-017), named on the start refusal. */
fun FlowContent.constraintList(
    constraints: List<SchedulingConstraint>,
    names: TimetableNames,
    texts: UiTexts,
) {
    ul { constraints.forEach { li { +describe(it, names, texts) } } }
}
