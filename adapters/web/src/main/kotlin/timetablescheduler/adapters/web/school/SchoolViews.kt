package timetablescheduler.adapters.web.school

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.ul
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.hiddenField
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.layout.textField
import timetablescheduler.adapters.web.layout.validationErrors
import timetablescheduler.application.school.SchoolDashboard
import timetablescheduler.domain.school.School
import timetablescheduler.domain.validation.ValidationError

fun schoolPath(school: School) = "/schools/${school.id.value}"

suspend fun ApplicationCall.respondSchoolDashboard(
    dashboard: SchoolDashboard,
    errors: List<ValidationError> = emptyList(),
    enteredName: String? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val school = dashboard.school
    respondPage(school.name, status) { session ->
        sectionLinks(school, dashboard)
        h2 { +"Umbenennen" }
        validationErrors(errors)
        form(action = schoolPath(school), method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${school.version}")
            textField("name", "Name", enteredName ?: school.name)
            button { +"Speichern" }
        }
    }
}

private fun FlowContent.sectionLinks(school: School, dashboard: SchoolDashboard) {
    ul {
        li { a(href = "${schoolPath(school)}/room-features") { +"Ausstattung (${dashboard.roomFeatureCount})" } }
        li { a(href = "${schoolPath(school)}/rooms") { +"Räume (${dashboard.roomCount})" } }
        li { a(href = "${schoolPath(school)}/time-grid") { +"Zeitraster (${dashboard.teachingSlotsPerWeek} Unterrichtsstunden pro Woche)" } }
    }
}
