package timetablescheduler.adapters.web.classlevel

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
import timetablescheduler.application.curriculum.CurriculumUseCases
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.validation.ValidationError
import java.util.UUID

private suspend fun ApplicationCall.levelIdParameter(): ClassLevelId? = idParameter("levelId")?.let(::ClassLevelId)

private suspend fun ApplicationCall.classIdParameter(): SchoolClassId? = idParameter("classId")?.let(::SchoolClassId)

private fun invalid(error: ValidationError) = UseCaseResult.Failure(ApplicationError.Invalid(listOf(error)))

/**
 * Pairs the repeated `subjectId` / `periodsPerWeek` fields of the curriculum editor by position. An empty or 0
 * period means "not taught" and is left out; text that is no whole number is a format error.
 */
private fun Parameters.curriculumInput(): CurriculumFormInput {
    val subjectIds = getAll(SUBJECT_FIELD).orEmpty()
    val periods = getAll(PERIODS_FIELD).orEmpty()
    val periodsBySubject = subjectIds.zip(periods)
        .mapNotNull { (subjectId, period) -> runCatching { SubjectId(UUID.fromString(subjectId)) }.getOrNull()?.let { it to period.trim() } }
        .toMap()
    return CurriculumFormInput(text("identifier"), periodsBySubject)
}

private fun CurriculumFormInput.toEntries(): List<CurriculumEntry>? =
    periodsBySubject
        .filterValues { it.isNotEmpty() && it != "0" }
        .map { (subjectId, period) -> CurriculumEntry(subjectId, period.toIntOrNull() ?: return null) }

private fun Parameters.classInput() = ClassFormInput(text("suffix"), text("priorityWeight"), text("homeRoomId"))

/** The chosen home room; empty or malformed means none (a foreign id is rejected by the use case). */
private fun Parameters.homeRoomId(): RoomId? = uuids("homeRoomId").firstOrNull()?.let(::RoomId)

/** Parses the class form; a weight that is no whole number is reported like a business-rule violation. */
private fun Parameters.parseClass(save: (suffix: String, priorityWeight: Int) -> UseCaseResult<SchoolClass>): UseCaseResult<SchoolClass> {
    val input = FormInput(this)
    val priorityWeight = input.int("priorityWeight") ?: return UseCaseResult.Failure(ApplicationError.Invalid(input.errors))
    return save(text("suffix"), priorityWeight)
}

private suspend fun ApplicationCall.showLevelAgain(
    curriculum: CurriculumUseCases,
    actor: AccountId,
    schoolId: SchoolId,
    levelId: ClassLevelId,
    errors: List<ValidationError>,
    entered: CurriculumFormInput?,
) {
    respondResult(curriculum.getClassLevel.execute(actor, schoolId, levelId)) {
        respondClassLevelDetails(it, errors, entered, HttpStatusCode.UnprocessableEntity)
    }
}

/** Class levels with their curriculum (FR-010, FR-011) and the classes of each level. */
fun Route.classLevelRoutes(dependencies: WebDependencies) {
    val curriculum = dependencies.useCases.curriculum

    authenticate(SESSION_AUTH) {
        get("/schools/{schoolId}/class-levels") {
            val schoolId = call.schoolIdParameter() ?: return@get
            call.respondResult(curriculum.listClassLevels.execute(call.actor, schoolId)) { call.respondClassLevelList(schoolId, it) }
        }

        post("/schools/{schoolId}/class-levels") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val identifier = call.receiveCheckedForm().text("identifier")
            call.respondFormResult(
                curriculum.createClassLevel.execute(call.actor, schoolId, identifier),
                backLink = levelsPath(schoolId),
                onInvalid = { errors ->
                    call.respondResult(curriculum.listClassLevels.execute(call.actor, schoolId)) {
                        call.respondClassLevelList(schoolId, it, errors, identifier, HttpStatusCode.UnprocessableEntity)
                    }
                },
            ) { call.seeOther(levelPath(it)) }
        }

        get("/schools/{schoolId}/class-levels/{levelId}") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val levelId = call.levelIdParameter() ?: return@get
            call.respondResult(curriculum.getClassLevel.execute(call.actor, schoolId, levelId)) { call.respondClassLevelDetails(it) }
        }

        post("/schools/{schoolId}/class-levels/{levelId}") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val levelId = call.levelIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val entered = form.curriculumInput()
            val entries = entered.toEntries()
            val result = if (entries == null) invalid(ValidationError.InvalidFormat(PERIODS_FIELD))
            else curriculum.updateClassLevel.execute(call.actor, schoolId, levelId, entered.identifier, entries, form.version())
            call.respondFormResult(
                result,
                backLink = "${levelsPath(schoolId)}/${levelId.value}",
                onInvalid = { errors -> call.showLevelAgain(curriculum, call.actor, schoolId, levelId, errors, entered) },
            ) { call.seeOther(levelPath(it.level)) }
        }

        post("/schools/{schoolId}/class-levels/{levelId}/delete") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val levelId = call.levelIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondResult(
                curriculum.deleteClassLevel.execute(call.actor, schoolId, levelId, form.version()),
                "${levelsPath(schoolId)}/${levelId.value}",
            ) { call.seeOther(levelsPath(schoolId)) }
        }

        schoolClassRoutes(curriculum)
    }
}

/** The classes of a level: new-class form with suffix proposal, class page with resolved curriculum (FR-012 – FR-015). */
private fun Route.schoolClassRoutes(curriculum: CurriculumUseCases) {
    get("/schools/{schoolId}/class-levels/{levelId}/classes/new") {
        val schoolId = call.schoolIdParameter() ?: return@get
        val levelId = call.levelIdParameter() ?: return@get
        call.respondResult(curriculum.proposeClassSuffix.execute(call.actor, schoolId, levelId)) { call.respondNewClassForm(it) }
    }

    post("/schools/{schoolId}/class-levels/{levelId}/classes") {
        val schoolId = call.schoolIdParameter() ?: return@post
        val levelId = call.levelIdParameter() ?: return@post
        val form = call.receiveCheckedForm()
        call.respondFormResult(
            form.parseClass { suffix, weight -> curriculum.createSchoolClass.execute(call.actor, schoolId, levelId, suffix, weight, form.homeRoomId()) },
            backLink = "${levelsPath(schoolId)}/${levelId.value}",
            onInvalid = { errors ->
                call.respondResult(curriculum.proposeClassSuffix.execute(call.actor, schoolId, levelId)) {
                    call.respondNewClassForm(it, errors, form.classInput(), HttpStatusCode.UnprocessableEntity)
                }
            },
        ) { call.seeOther(classPath(schoolId, it)) }
    }

    get("/schools/{schoolId}/classes/{classId}") {
        val schoolId = call.schoolIdParameter() ?: return@get
        val classId = call.classIdParameter() ?: return@get
        call.respondResult(curriculum.getSchoolClass.execute(call.actor, schoolId, classId)) { call.respondSchoolClassDetails(it) }
    }

    post("/schools/{schoolId}/classes/{classId}") {
        val schoolId = call.schoolIdParameter() ?: return@post
        val classId = call.classIdParameter() ?: return@post
        val form = call.receiveCheckedForm()
        call.respondFormResult(
            form.parseClass { suffix, weight -> curriculum.updateSchoolClass.execute(call.actor, schoolId, classId, suffix, weight, form.version(), form.homeRoomId()) },
            backLink = "/schools/${schoolId.value}/classes/${classId.value}",
            onInvalid = { errors ->
                call.respondResult(curriculum.getSchoolClass.execute(call.actor, schoolId, classId)) {
                    call.respondSchoolClassDetails(it, errors, form.classInput(), HttpStatusCode.UnprocessableEntity)
                }
            },
        ) { call.seeOther(classPath(schoolId, it)) }
    }

    post("/schools/{schoolId}/classes/{classId}/delete") {
        val schoolId = call.schoolIdParameter() ?: return@post
        val classId = call.classIdParameter() ?: return@post
        val form = call.receiveCheckedForm()
        call.respondResult(
            curriculum.getSchoolClass.execute(call.actor, schoolId, classId),
            "/schools/${schoolId.value}/classes/${classId.value}",
        ) { details ->
            call.respondResult(curriculum.deleteSchoolClass.execute(call.actor, schoolId, classId, form.version()), classPath(schoolId, details.schoolClass)) {
                call.seeOther(levelPath(details.level))
            }
        }
    }
}
