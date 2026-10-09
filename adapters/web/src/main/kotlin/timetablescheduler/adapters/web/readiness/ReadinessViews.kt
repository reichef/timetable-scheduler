package timetablescheduler.adapters.web.readiness

import io.ktor.server.application.ApplicationCall
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.application.readiness.ReadinessReport
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.readiness.ReadinessIssue

fun readinessPath(schoolId: SchoolId) = "/schools/${schoolId.value}/readiness"

/** One list entry: a link to the affected entry and what is wrong with it. */
internal fun FlowContent.issueEntry(report: ReadinessReport, issue: ReadinessIssue, texts: UiTexts) {
    val school = "/schools/${report.schoolId.value}"
    when (issue) {
        is ReadinessIssue.SubjectNoSuitableRoom -> {
            a(href = "$school/subjects/${issue.subjectId.value}") { +report.subjectName(issue.subjectId) }
            val missing = issue.missingFeatureIds.map(report::featureName).sorted()
            +if (missing.isEmpty()) texts.readiness.noRoomOffersAll else texts.readiness.missingFeatures(missing)
        }
        is ReadinessIssue.SubjectNoQualifiedTeacher ->
            a(href = "$school/subjects/${issue.subjectId.value}") { +report.subjectName(issue.subjectId) }
        is ReadinessIssue.SubjectTeacherCapacityShortfall -> {
            a(href = "$school/subjects/${issue.subjectId.value}") { +report.subjectName(issue.subjectId) }
            +texts.readiness.missingPeriods(issue.shortfallPeriods)
        }
        is ReadinessIssue.LevelExceedsGrid -> {
            a(href = "$school/class-levels/${issue.classLevelId.value}") {
                +texts.readiness.levelName(report.levelIdentifier(issue.classLevelId))
            }
            +texts.readiness.excessPeriods(issue.excessPeriods)
        }
        is ReadinessIssue.TeacherWithoutQualification ->
            a(href = "$school/teachers/${issue.teacherId.value}") { +report.teacherName(issue.teacherId) }
        is ReadinessIssue.TeacherZeroLoad ->
            a(href = "$school/teachers/${issue.teacherId.value}") { +report.teacherName(issue.teacherId) }
        is ReadinessIssue.TeacherLoadExceedsGrid -> {
            a(href = "$school/teachers/${issue.teacherId.value}") { +report.teacherName(issue.teacherId) }
            +texts.readiness.excessPeriods(issue.excessPeriods)
        }
    }
}

/** Issues grouped under one heading per kind, in the order the check reports them (rule by rule). */
suspend fun ApplicationCall.respondReadiness(report: ReadinessReport) {
    val texts = texts
    respondPage(texts.readiness.title) {
        if (report.issues.isEmpty()) {
            p { +texts.readiness.noIssues }
        }
        report.issues.groupBy { texts.readiness.heading(it) }.forEach { (heading, group) ->
            h2 { +heading }
            ul { group.forEach { issue -> li { issueEntry(report, issue, texts) } } }
        }
        p { a(href = "/schools/${report.schoolId.value}") { +texts.common.toSchool } }
    }
}

/** Dashboard line linking to the overview (US4). */
fun FlowContent.readinessSummary(schoolId: SchoolId, issueCount: Int, texts: UiTexts) {
    p {
        a(href = readinessPath(schoolId)) { +texts.school.readinessLink }
        +texts.school.readinessSummary(issueCount)
    }
}
