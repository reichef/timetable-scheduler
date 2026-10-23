package timetablescheduler.bootstrap.testschool

import timetablescheduler.adapters.persistence.DatabaseFactory
import timetablescheduler.application.MasterDataUseCases
import timetablescheduler.application.account.InitialSetup
import timetablescheduler.bootstrap.AppConfig
import timetablescheduler.bootstrap.ApplicationWiring
import timetablescheduler.bootstrap.MissingConfiguration
import timetablescheduler.bootstrap.ensureInstalled
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId

/**
 * `./gradlew :bootstrap:seedTestSchool [-PtestSchool=understaffed]`: seeds the fictitious test school, or its
 * teacher-shortage variant, into the database of the `TTS_*` variables, so it can be inspected with `:bootstrap:run`.
 * Use a separate, empty database — the seeder refuses a school that already has master data. Prints ids and counts
 * only.
 */
fun main(arguments: Array<String>) {
    val testSet = testSetNamed(arguments.firstOrNull() ?: "full")
    val config = AppConfig.fromEnvironment(System.getenv())
    val setup =
        config.initialSetup
            ?: throw MissingConfiguration(
                "Set TTS_INITIAL_PLANNER_USER and TTS_INITIAL_PLANNER_PASSWORD; the planner seeds the test school",
            )

    val wiring = ApplicationWiring(DatabaseFactory.connectAndMigrate(config.database))
    ensureInstalled(wiring.installation, config)

    val (actor, schoolId) = plannerAndSchool(wiring.useCases, setup)
    val seeded = TestSchoolSeeder(wiring.useCases, testSet).seed(actor, schoolId)

    println("Test school seeded: school ${seeded.schoolId.value}, ${seeded.classIds.size} classes, ${seeded.teacherIds.size} teachers")
}

private fun testSetNamed(name: String): TestSchoolData =
    when (name) {
        "full" -> BwGymnasiumTestSet
        "understaffed" -> UnderstaffedTestSet
        else -> throw IllegalArgumentException("Unknown test school '$name'; use full or understaffed")
    }

/** The installation's planner and its only school; the test school replaces nothing but this empty school. */
fun plannerAndSchool(
    useCases: MasterDataUseCases,
    setup: InitialSetup,
): Pair<AccountId, SchoolId> {
    val planner = useCases.login.execute(setup.plannerUsername, setup.plannerPassword).required("log in as planner")
    val school =
        useCases.schools.list
            .execute(planner.accountId)
            .required("list schools")
            .single()

    return planner.accountId to school.id
}
