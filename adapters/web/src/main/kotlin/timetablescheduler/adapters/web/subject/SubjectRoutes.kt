package timetablescheduler.adapters.web.subject

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
import timetablescheduler.adapters.web.layout.idParameter
import timetablescheduler.adapters.web.layout.receiveCheckedForm
import timetablescheduler.adapters.web.layout.respondFormResult
import timetablescheduler.adapters.web.layout.respondResult
import timetablescheduler.adapters.web.layout.seeOther
import timetablescheduler.adapters.web.layout.text
import timetablescheduler.adapters.web.layout.uuids
import timetablescheduler.adapters.web.layout.version
import timetablescheduler.adapters.web.school.schoolIdParameter
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SubjectId

private suspend fun ApplicationCall.subjectIdParameter(): SubjectId? = idParameter("subjectId")?.let(::SubjectId)

private fun Parameters.subjectInput() = SubjectFormInput(text("name"), uuids(REQUIRED_FEATURE_FIELD).map(::RoomFeatureId).toSet())

/** Subjects with their required room features (FR-008, FR-009). */
fun Route.subjectRoutes(dependencies: WebDependencies) {
    val curriculum = dependencies.useCases.curriculum

    authenticate(SESSION_AUTH) {
        get("/schools/{schoolId}/subjects") {
            val schoolId = call.schoolIdParameter() ?: return@get
            call.respondResult(curriculum.listSubjects.execute(call.actor, schoolId)) { call.respondSubjectList(schoolId, it) }
        }

        post("/schools/{schoolId}/subjects") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val entered = call.receiveCheckedForm().subjectInput()
            call.respondFormResult(
                curriculum.createSubject.execute(call.actor, schoolId, entered.name, entered.requiredFeatureIds),
                backLink = subjectsPath(schoolId),
                onInvalid = { errors ->
                    call.respondResult(curriculum.listSubjects.execute(call.actor, schoolId)) {
                        call.respondSubjectList(schoolId, it, errors, entered, HttpStatusCode.UnprocessableEntity)
                    }
                },
            ) { call.seeOther(subjectsPath(schoolId)) }
        }

        get("/schools/{schoolId}/subjects/{subjectId}") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val subjectId = call.subjectIdParameter() ?: return@get
            call.respondResult(curriculum.getSubject.execute(call.actor, schoolId, subjectId)) { call.respondSubjectDetails(it) }
        }

        post("/schools/{schoolId}/subjects/{subjectId}") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val subjectId = call.subjectIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val entered = form.subjectInput()
            call.respondFormResult(
                curriculum.updateSubject.execute(call.actor, schoolId, subjectId, entered.name, entered.requiredFeatureIds, form.version()),
                backLink = "${subjectsPath(schoolId)}/${subjectId.value}",
                onInvalid = { errors ->
                    call.respondResult(curriculum.getSubject.execute(call.actor, schoolId, subjectId)) {
                        call.respondSubjectDetails(it, errors, entered, HttpStatusCode.UnprocessableEntity)
                    }
                },
            ) { call.seeOther(subjectsPath(schoolId)) }
        }

        post("/schools/{schoolId}/subjects/{subjectId}/delete") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val subjectId = call.subjectIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondResult(
                curriculum.deleteSubject.execute(call.actor, schoolId, subjectId, form.version()),
                "${subjectsPath(schoolId)}/${subjectId.value}",
            ) { call.seeOther(subjectsPath(schoolId)) }
        }
    }
}
