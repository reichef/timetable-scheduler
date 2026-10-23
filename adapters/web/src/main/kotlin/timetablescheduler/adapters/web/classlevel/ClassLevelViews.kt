package timetablescheduler.adapters.web.classlevel

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FORM
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.hiddenField
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.layout.textField
import timetablescheduler.adapters.web.layout.validationErrors
import timetablescheduler.application.curriculum.ClassLevelDetails
import timetablescheduler.application.curriculum.ClassLevelOverview
import timetablescheduler.application.curriculum.ClassProposal
import timetablescheduler.application.curriculum.SchoolClassDetails
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.validation.ValidationError

const val SUBJECT_FIELD = "subjectId"
const val PERIODS_FIELD = "periodsPerWeek"

fun levelsPath(schoolId: SchoolId) = "/schools/${schoolId.value}/class-levels"

fun levelPath(level: ClassLevel) = "${levelsPath(level.schoolId)}/${level.id.value}"

fun classPath(
    schoolId: SchoolId,
    schoolClass: SchoolClass,
) = "/schools/${schoolId.value}/classes/${schoolClass.id.value}"

/** Entered values of the curriculum editor; periods stay raw text so wrong input is shown again unchanged. */
data class CurriculumFormInput(
    val identifier: String,
    val periodsBySubject: Map<SubjectId, String>,
)

/** Entered values of the class form. */
data class ClassFormInput(
    val suffix: String,
    val priorityWeight: String,
    val homeRoomId: String = "",
)

/** Optional home room of the class (002 FR-002a); the empty option means none. */
private fun FORM.homeRoomSelect(
    rooms: List<Room>,
    selectedRoomId: String,
    texts: UiTexts,
) {
    label {
        +texts.classLevels.homeRoomLabel
        select {
            name = "homeRoomId"
            option {
                value = ""
                selected = selectedRoomId.isEmpty()
                +texts.classLevels.noHomeRoom
            }
            rooms.forEach { room ->
                option {
                    value = "${room.id.value}"
                    selected = selectedRoomId == "${room.id.value}"
                    +room.identifier
                }
            }
        }
    }
}

private fun FlowContent.gridWarning(
    warning: ReadinessIssue.LevelExceedsGrid?,
    weeklyPeriods: Int,
    teachingSlotsPerWeek: Int,
    texts: UiTexts,
) {
    if (warning == null) {
        return
    }

    div(classes = "warning") { +texts.classLevels.gridWarning(weeklyPeriods, teachingSlotsPerWeek, warning.excessPeriods) }
}

suspend fun ApplicationCall.respondClassLevelList(
    schoolId: SchoolId,
    overview: ClassLevelOverview,
    errors: List<ValidationError> = emptyList(),
    enteredIdentifier: String? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    respondPage(texts.classLevels.title, status) { session ->
        p { +texts.classLevels.gridOffers(overview.teachingSlotsPerWeek) }
        table {
            tr {
                th { +texts.classLevels.levelColumn }
                th { +texts.classLevels.weeklyPeriodsColumn }
                th { +texts.classLevels.classesColumn }
            }
            overview.levels.forEach { summary ->
                tr {
                    td { a(href = levelPath(summary.level)) { +summary.level.identifier } }
                    td { +"${summary.level.weeklyPeriods()}" }
                    td {
                        summary.classes.forEachIndexed { index, schoolClass ->
                            if (index > 0) {
                                +", "
                            }
                            a(href = classPath(schoolId, schoolClass)) { +schoolClass.displayName(summary.level) }
                        }
                    }
                }
            }
        }
        h2 { +texts.classLevels.createLevel }
        validationErrors(errors, texts)
        form(action = levelsPath(schoolId), method = FormMethod.post) {
            csrfField(session)
            textField("identifier", texts.classLevels.levelIdentifierLabel, enteredIdentifier)
            button { +texts.common.create }
        }

        p { a(href = "/schools/${schoolId.value}") { +texts.common.toSchool } }
    }
}

suspend fun ApplicationCall.respondClassLevelDetails(
    details: ClassLevelDetails,
    errors: List<ValidationError> = emptyList(),
    entered: CurriculumFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    val level = details.level
    val storedPeriods = level.curriculum.associate { it.subjectId to "${it.periodsPerWeek}" }
    respondPage(texts.classLevels.levelTitle(level.identifier), status) { session ->
        gridWarning(details.warning, level.weeklyPeriods(), details.teachingSlotsPerWeek, texts)
        p { +texts.classLevels.levelSum(level.weeklyPeriods(), details.teachingSlotsPerWeek) }
        validationErrors(errors, texts)

        h2 { +texts.classLevels.curriculum }
        form(action = levelPath(level), method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${level.version}")
            textField("identifier", texts.common.identifier, entered?.identifier ?: level.identifier)
            table {
                tr {
                    th { +texts.classLevels.subjectColumn }
                    th { +texts.classLevels.periodsInputColumn }
                }
                details.subjects.forEach { subject ->
                    tr {
                        td {
                            input(type = InputType.hidden, name = SUBJECT_FIELD) { value = "${subject.id.value}" }
                            +subject.name
                        }
                        td {
                            input(type = InputType.number, name = PERIODS_FIELD) {
                                min = "0"
                                value = (entered?.periodsBySubject ?: storedPeriods)[subject.id].orEmpty()
                            }
                        }
                    }
                }
            }
            button { +texts.common.save }
        }

        h2 { +texts.classLevels.classesColumn }
        p {
            details.classes.forEachIndexed { index, schoolClass ->
                if (index > 0) {
                    +", "
                }
                a(href = classPath(level.schoolId, schoolClass)) { +schoolClass.displayName(level) }
            }
        }
        p { a(href = "${levelPath(level)}/classes/new") { +texts.classLevels.createClass } }
        form(action = "${levelPath(level)}/delete", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${level.version}")
            button(classes = "danger") { +texts.classLevels.deleteLevel }
        }

        p { a(href = levelsPath(level.schoolId)) { +texts.classLevels.toLevels } }
    }
}

suspend fun ApplicationCall.respondNewClassForm(
    proposal: ClassProposal,
    errors: List<ValidationError> = emptyList(),
    entered: ClassFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    val level = proposal.level
    respondPage(texts.classLevels.newClassTitle(level.identifier), status) { session ->
        validationErrors(errors, texts)
        form(action = "${levelPath(level)}/classes", method = FormMethod.post) {
            csrfField(session)
            textField("suffix", texts.classLevels.suffixLabel, entered?.suffix ?: proposal.suffix)
            textField(
                "priorityWeight",
                texts.classLevels.weightLabel,
                entered?.priorityWeight ?: "${SchoolClass.DEFAULT_PRIORITY_WEIGHT}",
                InputType.number,
            )
            homeRoomSelect(proposal.rooms, entered?.homeRoomId.orEmpty(), texts)
            button { +texts.common.create }
        }
        p { a(href = levelPath(level)) { +texts.classLevels.toLevel } }
    }
}

suspend fun ApplicationCall.respondSchoolClassDetails(
    details: SchoolClassDetails,
    errors: List<ValidationError> = emptyList(),
    entered: ClassFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    val schoolClass = details.schoolClass
    val path = classPath(details.level.schoolId, schoolClass)
    val subjectNames = details.subjects.associate { it.id to it.name }
    respondPage(texts.classLevels.classTitle(details.displayName), status) { session ->
        h2 { +texts.classLevels.curriculumFromLevel(details.level.identifier) }
        table {
            tr {
                th { +texts.classLevels.subjectColumn }
                th { +texts.classLevels.weeklyPeriodsColumn }
            }
            details.curriculum.forEach { entry ->
                tr {
                    td { +subjectNames[entry.subjectId].orEmpty() }
                    td { +"${entry.periodsPerWeek}" }
                }
            }
        }
        p { +texts.classLevels.classSum(details.curriculum.sumOf { it.periodsPerWeek }) }

        h2 { +texts.classLevels.editClass }
        validationErrors(errors, texts)
        form(action = path, method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${schoolClass.version}")
            textField("suffix", texts.classLevels.suffixLabel, entered?.suffix ?: schoolClass.suffix)
            textField(
                "priorityWeight",
                texts.classLevels.weightLabel,
                entered?.priorityWeight ?: "${schoolClass.priorityWeight}",
                InputType.number,
            )
            homeRoomSelect(
                details.rooms,
                entered?.homeRoomId ?: schoolClass.homeRoomId
                    ?.value
                    ?.toString()
                    .orEmpty(),
                texts,
            )

            button { +texts.common.save }
        }
        form(action = "$path/delete", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${schoolClass.version}")
            button(classes = "danger") { +texts.classLevels.deleteClass }
        }

        p { a(href = levelPath(details.level)) { +texts.classLevels.toLevel } }
    }
}
