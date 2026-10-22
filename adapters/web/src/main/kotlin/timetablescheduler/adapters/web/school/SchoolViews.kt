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
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.hiddenField
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.layout.textField
import timetablescheduler.adapters.web.layout.validationErrors
import timetablescheduler.adapters.web.readiness.readinessSummary
import timetablescheduler.application.school.SchoolDashboard
import timetablescheduler.domain.school.School
import timetablescheduler.domain.validation.ValidationError

fun schoolPath(school: School) = "/schools/${school.id.value}"

suspend fun ApplicationCall.respondSchoolDashboard(
    dashboard: SchoolDashboard,
    readinessIssueCount: Int,
    errors: List<ValidationError> = emptyList(),
    enteredName: String? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    val school = dashboard.school
    respondPage(school.name, status) { session ->
        sectionLinks(school, dashboard, texts)
        readinessSummary(school.id, readinessIssueCount, texts)
        h2 { +texts.school.rename }
        validationErrors(errors, texts)
        form(action = schoolPath(school), method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${school.version}")
            textField("name", texts.common.name, enteredName ?: school.name)
            button { +texts.common.save }
        }
    }
}

private fun FlowContent.sectionLinks(
    school: School,
    dashboard: SchoolDashboard,
    texts: UiTexts,
) {
    ul {
        li { a(href = "${schoolPath(school)}/room-features") { +texts.school.featuresLink(dashboard.roomFeatureCount) } }
        li { a(href = "${schoolPath(school)}/rooms") { +texts.school.roomsLink(dashboard.roomCount) } }
        li { a(href = "${schoolPath(school)}/time-grid") { +texts.school.timeGridLink(dashboard.teachingSlotsPerWeek) } }
        li { a(href = "${schoolPath(school)}/subjects") { +texts.school.subjectsLink } }
        li { a(href = "${schoolPath(school)}/class-levels") { +texts.school.classLevelsLink } }
        li { a(href = "${schoolPath(school)}/teachers") { +texts.school.teachersLink } }
        li { a(href = "${schoolPath(school)}/constraints") { +texts.school.constraintsLink } }
        li { a(href = "${schoolPath(school)}/generations") { +texts.school.generationsLink } }
        li { a(href = "${schoolPath(school)}/timetable") { +texts.school.chosenTimetableLink } }
    }
}
