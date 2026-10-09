package timetablescheduler.adapters.web.teacher

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import timetablescheduler.adapters.web.WebDependencies
import timetablescheduler.adapters.web.auth.SESSION_AUTH
import timetablescheduler.adapters.web.auth.actor
import timetablescheduler.adapters.web.layout.FormInput
import timetablescheduler.adapters.web.layout.idParameter
import timetablescheduler.adapters.web.layout.receiveCheckedForm
import timetablescheduler.adapters.web.layout.respondFormResult
import timetablescheduler.adapters.web.layout.respondResult
import timetablescheduler.adapters.web.layout.seeOther
import timetablescheduler.adapters.web.layout.text
import timetablescheduler.adapters.web.layout.uuids
import timetablescheduler.adapters.web.layout.version
import timetablescheduler.adapters.web.school.schoolIdParameter
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.teacher.TeacherUseCases
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.teacher.Teacher
import timetablescheduler.domain.teacher.Unavailability
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek
import java.util.UUID

private suspend fun ApplicationCall.teacherIdParameter(): TeacherId? = idParameter("teacherId")?.let(::TeacherId)

private fun Parameters.teacherInput() = TeacherFormInput(text("name"), text("shortCode"), text("weeklyLoad"))

/** Parses the general-data form; a load that is no whole number is reported like a business-rule violation. */
private fun Parameters.parseGeneralData(save: (name: String, shortCode: String, weeklyLoad: Int) -> UseCaseResult<Teacher>): UseCaseResult<Teacher> {
    val input = FormInput(this)
    val weeklyLoad = input.int("weeklyLoad") ?: return UseCaseResult.Failure(ApplicationError.Invalid(input.errors))
    return save(text("name"), text("shortCode"), weeklyLoad)
}

/** Reads `{dayOfWeek}:{slotId}` checkbox values; null if any value is malformed. */
private fun Parameters.unavailability(): Set<Unavailability>? =
    getAll(SLOT_FIELD).orEmpty().map { value ->
        val day = DayOfWeek.entries.firstOrNull { it.name == value.substringBefore(':') } ?: return null
        val slotId = runCatching { UUID.fromString(value.substringAfter(':')) }.getOrNull() ?: return null
        Unavailability(day, TimeSlotId(slotId))
    }.toSet()

private suspend fun ApplicationCall.showTeacherAgain(
    teachers: TeacherUseCases,
    actor: AccountId,
    schoolId: SchoolId,
    teacherId: TeacherId,
    errors: List<ValidationError>,
    entered: TeacherFormInput?,
) {
    respondResult(teachers.get.execute(actor, schoolId, teacherId)) {
        respondTeacherDetails(it, errors, entered, HttpStatusCode.UnprocessableEntity)
    }
}

/** Teachers with short code, weekly load, qualifications and unavailability (FR-016 – FR-018, FR-021). */
fun Route.teacherRoutes(dependencies: WebDependencies) {
    val teachers = dependencies.useCases.teachers
    val timeGrid = dependencies.useCases.timeGrid

    /** The list page shows the load limit, which comes from the school's time grid. */
    suspend fun ApplicationCall.respondTeacherListPage(
        schoolId: SchoolId,
        errors: List<ValidationError> = emptyList(),
        entered: TeacherFormInput? = null,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        respondResult(teachers.list.execute(actor, schoolId)) { list ->
            respondResult(timeGrid.getTimeGrid.execute(actor, schoolId)) { view ->
                respondTeacherList(schoolId, list, view.timeGrid.teachingSlotsPerWeek(), errors, entered, status)
            }
        }
    }

    authenticate(SESSION_AUTH) {
        get("/schools/{schoolId}/teachers") {
            val schoolId = call.schoolIdParameter() ?: return@get
            call.respondTeacherListPage(schoolId)
        }

        post("/schools/{schoolId}/teachers") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondFormResult(
                form.parseGeneralData { name, shortCode, load -> teachers.create.execute(call.actor, schoolId, name, shortCode, load) },
                backLink = teachersPath(schoolId),
                onInvalid = { errors -> call.respondTeacherListPage(schoolId, errors, form.teacherInput(), HttpStatusCode.UnprocessableEntity) },
            ) { call.seeOther(teacherPath(it)) }
        }

        get("/schools/{schoolId}/teachers/{teacherId}") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val teacherId = call.teacherIdParameter() ?: return@get
            call.respondResult(teachers.get.execute(call.actor, schoolId, teacherId)) { call.respondTeacherDetails(it) }
        }

        post("/schools/{schoolId}/teachers/{teacherId}") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val teacherId = call.teacherIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondFormResult(
                form.parseGeneralData { name, shortCode, load ->
                    teachers.update.execute(call.actor, schoolId, teacherId, name, shortCode, load, form.version())
                },
                backLink = "${teachersPath(schoolId)}/${teacherId.value}",
                onInvalid = { errors -> call.showTeacherAgain(teachers, call.actor, schoolId, teacherId, errors, form.teacherInput()) },
            ) { call.seeOther(teacherPath(it)) }
        }

        post("/schools/{schoolId}/teachers/{teacherId}/qualifications") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val teacherId = call.teacherIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val subjectIds = form.uuids(QUALIFICATION_FIELD).map(::SubjectId).toSet()
            call.respondFormResult(
                teachers.updateQualifications.execute(call.actor, schoolId, teacherId, subjectIds, form.version()),
                backLink = "${teachersPath(schoolId)}/${teacherId.value}",
                onInvalid = { errors -> call.showTeacherAgain(teachers, call.actor, schoolId, teacherId, errors, null) },
            ) { call.seeOther(teacherPath(it)) }
        }

        post("/schools/{schoolId}/teachers/{teacherId}/unavailability") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val teacherId = call.teacherIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val entries = form.unavailability()
            val result = if (entries == null) UseCaseResult.Failure(ApplicationError.Invalid(listOf(ValidationError.InvalidReference(SLOT_FIELD))))
            else teachers.updateUnavailability.execute(call.actor, schoolId, teacherId, entries, form.version())
            call.respondFormResult(
                result,
                backLink = "${teachersPath(schoolId)}/${teacherId.value}",
                onInvalid = { errors -> call.showTeacherAgain(teachers, call.actor, schoolId, teacherId, errors, null) },
            ) { call.seeOther(teacherPath(it)) }
        }

        post("/schools/{schoolId}/teachers/{teacherId}/delete") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val teacherId = call.teacherIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondResult(
                teachers.delete.execute(call.actor, schoolId, teacherId, form.version()),
                "${teachersPath(schoolId)}/${teacherId.value}",
            ) { call.seeOther(teachersPath(schoolId)) }
        }
    }
}
