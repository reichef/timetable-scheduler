package timetablescheduler.adapters.web

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import io.ktor.server.testing.ApplicationTestBuilder
import timetablescheduler.application.account.InitialSetup
import timetablescheduler.application.account.InstallationUseCase
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.school.School

/** In-memory application wired exactly like production, but with fake repositories. */
class TestSystem {
    val world = MasterDataWorld()
    val dependencies = WebDependencies(world.useCases)
    val settings = WebSettings(devMode = true, sessionSignKey = ByteArray(32) { it.toByte() })

    /** Installs the first school "Gymnasium Nord" with planner `admin` / `sehr-geheim-1`. */
    fun installed(): TestSystem =
        apply {
            with(world) {
                installation().execute(InitialSetup("Gymnasium Nord", ADMIN, PASSWORD))
            }
        }

    fun firstSchool(): School = world.schools.findByNameIgnoreCase("Gymnasium Nord")!!

    fun firstSchoolId(): SchoolId = firstSchool().id

    fun admin(): UserAccount = world.accounts.findByUsernameIgnoreCase(ADMIN)!!

    fun addAccount(
        username: String,
        role: Role,
        schoolIds: Set<SchoolId>,
    ): UserAccount = world.account(username, role, schoolIds, PASSWORD)

    companion object {
        const val ADMIN = "admin"
        const val PASSWORD = MasterDataWorld.DEFAULT_PASSWORD
    }
}

fun ApplicationTestBuilder.startWith(system: TestSystem): HttpClient {
    application { webModule(system.dependencies, system.settings) }
    return newClient()
}

/** A further browser with its own cookies, e.g. to log in a second user. */
fun ApplicationTestBuilder.newClient(): HttpClient =
    createClient {
        followRedirects = false
        install(HttpCookies)
    }

suspend fun HttpClient.login(
    username: String = TestSystem.ADMIN,
    password: String = TestSystem.PASSWORD,
): HttpResponse = submitForm("/login", parametersOf("username" to listOf(username), "password" to listOf(password)))

private val csrfFieldPattern = Regex("""name="_csrf" value="([^"]+)"""")

/** Reads the CSRF token from a page the logged-in user can open. */
suspend fun HttpClient.csrfToken(pagePath: String = "/home"): String {
    val page = get(pagePath).bodyAsText()
    return csrfFieldPattern.find(page)?.groupValues?.get(1) ?: error("No CSRF token on $pagePath")
}

/** Submits a form like the browser does, including the CSRF token; repeated names become repeated fields. */
suspend fun HttpClient.submitChecked(
    path: String,
    vararg fields: Pair<String, String>,
): HttpResponse {
    val token = csrfToken()
    val parameters =
        Parameters.build {
            append("_csrf", token)
            fields.forEach { (name, value) -> append(name, value) }
        }
    return submitForm(path, parameters)
}
