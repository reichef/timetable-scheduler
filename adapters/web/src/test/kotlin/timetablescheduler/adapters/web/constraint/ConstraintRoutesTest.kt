package timetablescheduler.adapters.web.constraint

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.generation.GenerationTestData
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.newClient
import timetablescheduler.adapters.web.startWith
import timetablescheduler.adapters.web.submitChecked
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstraintRoutesTest {
    private fun GenerationTestData.constraintsPath() = "/schools/${schoolId.value}/constraints"

    @Test
    fun `the list shows the two school-wide defaults and the catalogue (FR-013c)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val client = startWith(system)
            client.login()

            val page = client.get(data.constraintsPath()).bodyAsText()

            assertTrue("Alle Klassen: jedes Fach höchstens 2 Stunden pro Tag – Soll (mittel)" in page)
            assertTrue("Alle Klassen: keine Freistunden – Soll (mittel)" in page)
            assertTrue("${data.constraintsPath()}/new?type=CLASS_MAX_PERIODS_PER_DAY" in page)
            assertTrue("${data.constraintsPath()}/new?type=TEACHER_EXCLUDED_SLOTS" in page)
        }

    @Test
    fun `a hard class limit is created and listed with a readable description`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val client = startWith(system)
            client.login()

            val form = client.get("${data.constraintsPath()}/new?type=CLASS_MAX_PERIODS_PER_DAY").bodyAsText()
            val created =
                client.submitChecked(
                    data.constraintsPath(),
                    "type" to "CLASS_MAX_PERIODS_PER_DAY",
                    "classId" to "${data.class5a.id.value}",
                    "limit" to "2",
                    "kind" to "HARD",
                )

            assertTrue("""name="limit"""" in form && "5a" in form)
            assertEquals(HttpStatusCode.SeeOther, created.status)
            assertTrue("Klasse 5a: höchstens 2 Stunden pro Tag – Muss" in client.get(data.constraintsPath()).bodyAsText())
        }

    @Test
    fun `excluded slots are chosen in a slot grid, a whole day at once`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val client = startWith(system)
            client.login()

            val response =
                client.submitChecked(
                    data.constraintsPath(),
                    "type" to "TEACHER_EXCLUDED_SLOTS",
                    "teacherId" to "${data.schmidt.id.value}",
                    "wholeDay" to "TUESDAY",
                    "kind" to "SOFT",
                    "weight" to "HIGH",
                )

            assertEquals(HttpStatusCode.SeeOther, response.status)
            val stored =
                system.world.constraints
                    .findBySchool(data.schoolId)
                    .single { it.rule is ConstraintRule.TeacherExcludedSlots }
            assertEquals(2, (stored.rule as ConstraintRule.TeacherExcludedSlots).slots.size)
            assertEquals(SoftWeight.HIGH, stored.weight)
            assertTrue(
                "Lehrkraft SCHA: frei Dienstag 1. Stunde, Dienstag 2. Stunde – Soll (hoch)" in
                    client.get(data.constraintsPath()).bodyAsText(),
            )
        }

    @Test
    fun `invalid parameters re-render the form with 422 (FR-015)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val client = startWith(system)
            client.login()

            val response =
                client.submitChecked(
                    data.constraintsPath(),
                    "type" to "CLASS_MAX_PERIODS_PER_DAY",
                    "classId" to "ALL",
                    "limit" to "0",
                    "kind" to "HARD",
                )

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertTrue("Grenzwert muss zwischen 1 und 2 liegen." in response.bodyAsText())
        }

    @Test
    fun `a constraint is edited, switched off and deleted, with version conflicts detected`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)

            val client = startWith(system)
            client.login()

            client.submitChecked(
                data.constraintsPath(),
                "type" to "CLASS_NO_GAPS",
                "classId" to "${data.class5a.id.value}",
                "kind" to "SOFT",
                "weight" to "LOW",
            )
            val stored =
                system.world.constraints.findBySchool(data.schoolId).single {
                    it.rule == ConstraintRule.ClassNoGaps(ClassScope.SpecificClass(data.class5a.id))
                }
            val path = "${data.constraintsPath()}/${stored.id.value}"

            assertTrue("""name="version" value="0"""" in client.get(path).bodyAsText())

            assertEquals(
                HttpStatusCode.SeeOther,
                client
                    .submitChecked(
                        path,
                        "type" to "CLASS_NO_GAPS",
                        "classId" to "ALL",
                        "kind" to "HARD",
                        "version" to "0",
                    ).status,
            )
            assertEquals(
                HttpStatusCode.Conflict,
                client
                    .submitChecked(
                        path,
                        "type" to "CLASS_NO_GAPS",
                        "classId" to "ALL",
                        "kind" to "HARD",
                        "version" to "0",
                    ).status,
            )

            assertEquals(HttpStatusCode.SeeOther, client.submitChecked("$path/active", "active" to "false", "version" to "1").status)
            assertTrue("ausgeschaltet" in client.get(data.constraintsPath()).bodyAsText())

            assertEquals(HttpStatusCode.SeeOther, client.submitChecked("$path/delete", "version" to "2").status)
            assertEquals(null, system.world.constraints.findById(stored.id))
        }

    @Test
    fun `a teacher fixation is created from the form and shown in the list (FR-013a)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val client = startWith(system)
            client.login()

            val form = client.get("/schools/${data.schoolId.value}/teacher-fixations/new").bodyAsText()
            val created =
                client.submitChecked(
                    "/schools/${data.schoolId.value}/teacher-fixations",
                    "classId" to "${data.class5a.id.value}",
                    "subjectId" to "${data.math.id.value}",
                    "teacherId" to "${data.schmidt.id.value}",
                )
            val duplicate =
                client.submitChecked(
                    "/schools/${data.schoolId.value}/teacher-fixations",
                    "classId" to "${data.class5a.id.value}",
                    "subjectId" to "${data.math.id.value}",
                    "teacherId" to "${data.schmidt.id.value}",
                )

            assertTrue("Anna Schmidt (SCHA)" in form)
            assertEquals(HttpStatusCode.SeeOther, created.status)
            assertEquals(data.constraintsPath(), created.headers[HttpHeaders.Location])
            assertTrue("5a Mathematik: SCHA" in client.get(data.constraintsPath()).bodyAsText())
            assertEquals(HttpStatusCode.UnprocessableEntity, duplicate.status)
        }

    @Test
    fun `deleting a class used by a constraint lists the constraint (FR-016)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            system.world.useCases.constraints.create.execute(
                system.admin().id,
                data.schoolId,
                timetablescheduler.application.scheduling.ConstraintInput(
                    ConstraintKind.HARD,
                    null,
                    ConstraintRule.ClassNoGaps(ClassScope.SpecificClass(data.class5a.id)),
                ),
            )
            val client = startWith(system)
            client.login()

            val response =
                client.submitChecked(
                    "/schools/${data.schoolId.value}/classes/${data.class5a.id.value}/delete",
                    "version" to "${data.class5a.version}",
                )

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertTrue("Bedingung „keine Freistunden (Klasse)“: 5a" in response.bodyAsText())
        }

    @Test
    fun `the start form offers active soft constraints as separate objectives and the result shows their column (FR-006a)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            system.world.solver.candidates = { listOf(data.complete()) }

            val client = startWith(system)
            client.login()

            val noGaps =
                system.world.constraints
                    .findBySchool(data.schoolId)
                    .single { it.rule is ConstraintRule.ClassNoGaps }

            val form = client.get(data.generationsPath).bodyAsText()
            client.submitChecked(data.generationsPath, "timeLimitMinutes" to "1", "separateObjective" to "${noGaps.id.value}")
            val generation =
                system.world.generations
                    .all()
                    .single()
            val result = client.get("${data.generationsPath}/${generation.id.value}").bodyAsText()

            assertTrue("""name="separateObjective" value="${noGaps.id.value}"""" in form)
            assertTrue("Alle Klassen: keine Freistunden" in result && "sort=separate1" in result)
            assertTrue("Bedingungen bei Start" in result)
        }

    @Test
    fun `teachers get 403 on the constraint pages (FR-025)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            system.addAccount("lehrer1", Role.TEACHER, setOf(data.schoolId))

            startWith(system)
            val teacher = newClient()
            teacher.login("lehrer1")

            assertEquals(HttpStatusCode.Forbidden, teacher.get(data.constraintsPath()).status)
            assertFalse("Freistunden" in teacher.get(data.constraintsPath()).bodyAsText())
        }
}
