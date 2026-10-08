package timetablescheduler.bootstrap

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory
import timetablescheduler.adapters.persistence.DatabaseFactory
import timetablescheduler.adapters.persistence.account.BcryptPasswordHasher
import timetablescheduler.adapters.persistence.account.ExposedPasswordCredentialRepository
import timetablescheduler.adapters.persistence.account.ExposedUserAccountRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedClassLevelRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedSchoolClassRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedSubjectRepository
import timetablescheduler.adapters.persistence.room.ExposedRoomFeatureRepository
import timetablescheduler.adapters.persistence.room.ExposedRoomRepository
import timetablescheduler.adapters.persistence.school.ExposedSchoolRepository
import timetablescheduler.adapters.web.WebDependencies
import timetablescheduler.adapters.web.WebSettings
import timetablescheduler.adapters.web.webModule
import timetablescheduler.application.MasterDataPorts
import timetablescheduler.application.MasterDataUseCases
import timetablescheduler.application.account.InstallationOutcome
import timetablescheduler.application.account.InstallationUseCase
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.port.TimeSlotUsage
import timetablescheduler.domain.common.TimeSlotId

private val log = LoggerFactory.getLogger("timetablescheduler.bootstrap.Main")

/** Composition root: the only place that knows every module (clean architecture, outermost circle). */
fun main() {
    val config = AppConfig.fromEnvironment(System.getenv())
    val databaseFactory = DatabaseFactory.connectAndMigrate(config.database)
    log.info("Database migrations applied")

    val transactions = databaseFactory.transactionRunner
    val schools = ExposedSchoolRepository()
    val accounts = ExposedUserAccountRepository()
    val credentials = ExposedPasswordCredentialRepository()
    val hasher = BcryptPasswordHasher()

    ensureInstalled(InstallationUseCase(transactions, schools, accounts, credentials, hasher), config)

    val ports = MasterDataPorts(
        transactions = transactions,
        schools = schools,
        accounts = accounts,
        credentials = credentials,
        hasher = hasher,
        rooms = ExposedRoomRepository(),
        roomFeatures = ExposedRoomFeatureRepository(),
        timeSlotUsage = NoTeacherUnavailabilitiesYet,
        subjects = ExposedSubjectRepository(),
        classLevels = ExposedClassLevelRepository(),
        schoolClasses = ExposedSchoolClassRepository(),
    )
    val dependencies = WebDependencies(MasterDataUseCases(ports))
    embeddedServer(Netty, port = 8080) {
        webModule(dependencies, WebSettings(config.devMode, config.sessionSignKey))
    }.start(wait = true)
}

/** FR-031: the first start creates the first school and planner; later starts ignore the initial variables. */
private fun ensureInstalled(installation: InstallationUseCase, config: AppConfig) {
    if (installation.isInstalled()) return
    val setup = config.initialSetup ?: throw MissingConfiguration(
        "Empty database: set TTS_INITIAL_SCHOOL_NAME, TTS_INITIAL_PLANNER_USER and TTS_INITIAL_PLANNER_PASSWORD",
    )
    when (val result = installation.execute(setup)) {
        is UseCaseResult.Success ->
            if (result.value == InstallationOutcome.INSTALLED) log.info("First school and initial planner created")
        is UseCaseResult.Failure ->
            throw MissingConfiguration("Initial setup rejected (${result.error.javaClass.simpleName}); check TTS_INITIAL_* values")
    }
}

/** Placeholder until teacher unavailabilities exist (US3). */
private object NoTeacherUnavailabilitiesYet : TimeSlotUsage {
    override fun countUnavailabilities(slotId: TimeSlotId): Int = 0
    override fun deleteUnavailabilities(slotId: TimeSlotId) = Unit
}
