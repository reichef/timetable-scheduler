package timetablescheduler.adapters.web.readiness

import io.ktor.server.application.ApplicationCall
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.application.readiness.ReadinessReport
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.readiness.ReadinessIssue
import kotlin.reflect.KClass

fun readinessPath(schoolId: SchoolId) = "/schools/${schoolId.value}/readiness"

/** Headings in the order the check reports the issues (data-model "Readiness issues"). */
private val groupHeadings: List<Pair<KClass<out ReadinessIssue>, String>> = listOf(
    ReadinessIssue.SubjectNoSuitableRoom::class to "Fächer ohne passenden Raum",
    ReadinessIssue.SubjectNoQualifiedTeacher::class to "Fächer ohne qualifizierte Lehrkraft",
    ReadinessIssue.SubjectTeacherCapacityShortfall::class to "Fächer mit zu wenig Lehrkraft-Stunden",
    ReadinessIssue.LevelExceedsGrid::class to "Klassenstufen über dem Zeitraster",
    ReadinessIssue.TeacherWithoutQualification::class to "Lehrkräfte ohne Qualifikation",
    ReadinessIssue.TeacherZeroLoad::class to "Lehrkräfte ohne Deputat",
    ReadinessIssue.TeacherLoadExceedsGrid::class to "Lehrkräfte mit Deputat über dem Zeitraster",
)

/** One list entry: a link to the affected entry and what is wrong with it. */
private fun FlowContent.issueEntry(report: ReadinessReport, issue: ReadinessIssue) {
    val school = "/schools/${report.schoolId.value}"
    when (issue) {
        is ReadinessIssue.SubjectNoSuitableRoom -> {
            a(href = "$school/subjects/${issue.subjectId.value}") { +report.subjectName(issue.subjectId) }
            val missing = issue.missingFeatureIds.map(report::featureName).sorted()
            +if (missing.isEmpty()) ": kein Raum bietet alle benötigte Ausstattung zusammen" else " – fehlende Ausstattung: ${missing.joinToString(", ")}"
        }
        is ReadinessIssue.SubjectNoQualifiedTeacher ->
            a(href = "$school/subjects/${issue.subjectId.value}") { +report.subjectName(issue.subjectId) }
        is ReadinessIssue.SubjectTeacherCapacityShortfall -> {
            a(href = "$school/subjects/${issue.subjectId.value}") { +report.subjectName(issue.subjectId) }
            +": ${issue.shortfallPeriods} Wochenstunden fehlen"
        }
        is ReadinessIssue.LevelExceedsGrid -> {
            a(href = "$school/class-levels/${issue.classLevelId.value}") { +"Stufe ${report.levelIdentifier(issue.classLevelId)}" }
            +": ${issue.excessPeriods} Wochenstunden zu viel"
        }
        is ReadinessIssue.TeacherWithoutQualification ->
            a(href = "$school/teachers/${issue.teacherId.value}") { +report.teacherName(issue.teacherId) }
        is ReadinessIssue.TeacherZeroLoad ->
            a(href = "$school/teachers/${issue.teacherId.value}") { +report.teacherName(issue.teacherId) }
        is ReadinessIssue.TeacherLoadExceedsGrid -> {
            a(href = "$school/teachers/${issue.teacherId.value}") { +report.teacherName(issue.teacherId) }
            +": ${issue.excessPeriods} Wochenstunden zu viel"
        }
    }
}

suspend fun ApplicationCall.respondReadiness(report: ReadinessReport) {
    respondPage("Bereitschaft für die Planung") {
        if (report.issues.isEmpty()) {
            p { +"Keine blockierenden Probleme gefunden." }
        }
        groupHeadings.forEach { (kind, heading) ->
            val group = report.issues.filter { kind.isInstance(it) }
            if (group.isEmpty()) return@forEach
            h2 { +heading }
            ul { group.forEach { issue -> li { issueEntry(report, issue) } } }
        }
        p { a(href = "/schools/${report.schoolId.value}") { +"Zur Schule" } }
    }
}

/** Dashboard line linking to the overview (US4). */
fun FlowContent.readinessSummary(schoolId: SchoolId, issueCount: Int) {
    p {
        a(href = readinessPath(schoolId)) { +"Bereitschaft für die Planung" }
        +if (issueCount == 0) ": keine Probleme" else ": $issueCount Probleme"
    }
}
