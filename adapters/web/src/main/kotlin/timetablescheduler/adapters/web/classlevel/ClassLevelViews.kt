package timetablescheduler.adapters.web.classlevel

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
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
import timetablescheduler.domain.validation.ValidationError

const val SUBJECT_FIELD = "subjectId"
const val PERIODS_FIELD = "periodsPerWeek"

fun levelsPath(schoolId: SchoolId) = "/schools/${schoolId.value}/class-levels"
fun levelPath(level: ClassLevel) = "${levelsPath(level.schoolId)}/${level.id.value}"
fun classPath(schoolId: SchoolId, schoolClass: SchoolClass) = "/schools/${schoolId.value}/classes/${schoolClass.id.value}"

/** Entered values of the curriculum editor; periods stay raw text so wrong input is shown again unchanged. */
data class CurriculumFormInput(val identifier: String, val periodsBySubject: Map<SubjectId, String>)

/** Entered values of the class form. */
data class ClassFormInput(val suffix: String, val priorityWeight: String)

private fun FlowContent.gridWarning(warning: ReadinessIssue.LevelExceedsGrid?, weeklyPeriods: Int, teachingSlotsPerWeek: Int) {
    if (warning == null) return
    div(classes = "warning") {
        +"Der Lehrplan passt nicht in das Zeitraster: $weeklyPeriods Wochenstunden, aber nur $teachingSlotsPerWeek "
        +"Unterrichtsstunden pro Woche (${warning.excessPeriods} zu viel). Gespeichert ist er trotzdem."
    }
}

suspend fun ApplicationCall.respondClassLevelList(
    schoolId: SchoolId,
    overview: ClassLevelOverview,
    errors: List<ValidationError> = emptyList(),
    enteredIdentifier: String? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondPage("Klassenstufen", status) { session ->
        p { +"Das Zeitraster bietet ${overview.teachingSlotsPerWeek} Unterrichtsstunden pro Woche." }
        table {
            tr { th { +"Stufe" }; th { +"Wochenstunden" }; th { +"Klassen" } }
            overview.levels.forEach { summary ->
                tr {
                    td { a(href = levelPath(summary.level)) { +summary.level.identifier } }
                    td { +"${summary.level.weeklyPeriods()}" }
                    td {
                        summary.classes.forEachIndexed { index, schoolClass ->
                            if (index > 0) +", "
                            a(href = classPath(schoolId, schoolClass)) { +schoolClass.displayName(summary.level) }
                        }
                    }
                }
            }
        }
        h2 { +"Klassenstufe anlegen" }
        validationErrors(errors)
        form(action = levelsPath(schoolId), method = FormMethod.post) {
            csrfField(session)
            textField("identifier", "Bezeichnung (z. B. 6)", enteredIdentifier)
            button { +"Anlegen" }
        }
        p { a(href = "/schools/${schoolId.value}") { +"Zur Schule" } }
    }
}

suspend fun ApplicationCall.respondClassLevelDetails(
    details: ClassLevelDetails,
    errors: List<ValidationError> = emptyList(),
    entered: CurriculumFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val level = details.level
    val storedPeriods = level.curriculum.associate { it.subjectId to "${it.periodsPerWeek}" }
    respondPage("Klassenstufe ${level.identifier}", status) { session ->
        gridWarning(details.warning, level.weeklyPeriods(), details.teachingSlotsPerWeek)
        p { +"Summe: ${level.weeklyPeriods()} Wochenstunden (Zeitraster: ${details.teachingSlotsPerWeek} Unterrichtsstunden pro Woche)" }
        validationErrors(errors)
        h2 { +"Lehrplan" }
        form(action = levelPath(level), method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${level.version}")
            textField("identifier", "Bezeichnung", entered?.identifier ?: level.identifier)
            table {
                tr { th { +"Fach" }; th { +"Wochenstunden (leer = nicht unterrichtet)" } }
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
            button { +"Speichern" }
        }
        h2 { +"Klassen" }
        p {
            details.classes.forEachIndexed { index, schoolClass ->
                if (index > 0) +", "
                a(href = classPath(level.schoolId, schoolClass)) { +schoolClass.displayName(level) }
            }
        }
        p { a(href = "${levelPath(level)}/classes/new") { +"Klasse anlegen" } }
        form(action = "${levelPath(level)}/delete", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${level.version}")
            button(classes = "danger") { +"Klassenstufe löschen" }
        }
        p { a(href = levelsPath(level.schoolId)) { +"Zu den Klassenstufen" } }
    }
}

suspend fun ApplicationCall.respondNewClassForm(
    proposal: ClassProposal,
    errors: List<ValidationError> = emptyList(),
    entered: ClassFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val level = proposal.level
    respondPage("Neue Klasse in Stufe ${level.identifier}", status) { session ->
        validationErrors(errors)
        form(action = "${levelPath(level)}/classes", method = FormMethod.post) {
            csrfField(session)
            textField("suffix", "Klassenzusatz", entered?.suffix ?: proposal.suffix)
            textField("priorityWeight", "Gewichtung (1–100)", entered?.priorityWeight ?: "${SchoolClass.DEFAULT_PRIORITY_WEIGHT}", InputType.number)
            button { +"Anlegen" }
        }
        p { a(href = levelPath(level)) { +"Zur Klassenstufe" } }
    }
}

suspend fun ApplicationCall.respondSchoolClassDetails(
    details: SchoolClassDetails,
    errors: List<ValidationError> = emptyList(),
    entered: ClassFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val schoolClass = details.schoolClass
    val path = classPath(details.level.schoolId, schoolClass)
    val subjectNames = details.subjects.associate { it.id to it.name }
    respondPage("Klasse ${details.displayName}", status) { session ->
        h2 { +"Lehrplan (aus Stufe ${details.level.identifier})" }
        table {
            tr { th { +"Fach" }; th { +"Wochenstunden" } }
            details.curriculum.forEach { entry ->
                tr {
                    td { +subjectNames[entry.subjectId].orEmpty() }
                    td { +"${entry.periodsPerWeek}" }
                }
            }
        }
        p { +"Summe: ${details.curriculum.sumOf { it.periodsPerWeek }} Wochenstunden" }
        h2 { +"Klasse bearbeiten" }
        validationErrors(errors)
        form(action = path, method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${schoolClass.version}")
            textField("suffix", "Klassenzusatz", entered?.suffix ?: schoolClass.suffix)
            textField("priorityWeight", "Gewichtung (1–100)", entered?.priorityWeight ?: "${schoolClass.priorityWeight}", InputType.number)
            button { +"Speichern" }
        }
        form(action = "$path/delete", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${schoolClass.version}")
            button(classes = "danger") { +"Klasse löschen" }
        }
        p { a(href = levelPath(details.level)) { +"Zur Klassenstufe" } }
    }
}
