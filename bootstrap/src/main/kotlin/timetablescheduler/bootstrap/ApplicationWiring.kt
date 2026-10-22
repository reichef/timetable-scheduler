package timetablescheduler.bootstrap

import timetablescheduler.adapters.persistence.DatabaseFactory
import timetablescheduler.adapters.persistence.account.BcryptPasswordHasher
import timetablescheduler.adapters.persistence.account.ExposedPasswordCredentialRepository
import timetablescheduler.adapters.persistence.account.ExposedUserAccountRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedClassLevelRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedSchoolClassRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedSubjectRepository
import timetablescheduler.adapters.persistence.room.ExposedRoomFeatureRepository
import timetablescheduler.adapters.persistence.room.ExposedRoomRepository
import timetablescheduler.adapters.persistence.scheduling.ExposedChosenTimetableRepository
import timetablescheduler.adapters.persistence.scheduling.ExposedGenerationRepository
import timetablescheduler.adapters.persistence.scheduling.ExposedSchedulingConstraintRepository
import timetablescheduler.adapters.persistence.scheduling.ExposedTeacherFixationRepository
import timetablescheduler.adapters.persistence.school.ExposedSchoolRepository
import timetablescheduler.adapters.persistence.teacher.ExposedTeacherRepository
import timetablescheduler.adapters.persistence.teacher.ExposedTimeSlotUsage
import timetablescheduler.adapters.solver.TimefoldTimetableSolver
import timetablescheduler.application.MasterDataPorts
import timetablescheduler.application.MasterDataUseCases
import timetablescheduler.application.SchedulingPorts
import timetablescheduler.application.account.InstallationUseCase
import java.time.Clock

/**
 * Wires the use cases with PostgreSQL, the Timefold solver and the background executor. Used by the application
 * and by tooling that needs the real wiring, such as the test-school scenario.
 */
class ApplicationWiring(databaseFactory: DatabaseFactory) {
    private val transactions = databaseFactory.transactionRunner
    private val schools = ExposedSchoolRepository()
    private val accounts = ExposedUserAccountRepository()
    private val credentials = ExposedPasswordCredentialRepository()
    private val hasher = BcryptPasswordHasher()
    private val constraints = ExposedSchedulingConstraintRepository()

    val installation = InstallationUseCase(transactions, schools, accounts, credentials, hasher, constraints)

    val useCases: MasterDataUseCases = MasterDataUseCases(
        MasterDataPorts(
            transactions = transactions,
            schools = schools,
            accounts = accounts,
            credentials = credentials,
            hasher = hasher,
            rooms = ExposedRoomRepository(),
            roomFeatures = ExposedRoomFeatureRepository(),
            timeSlotUsage = ExposedTimeSlotUsage(),
            subjects = ExposedSubjectRepository(),
            classLevels = ExposedClassLevelRepository(),
            schoolClasses = ExposedSchoolClassRepository(),
            teachers = ExposedTeacherRepository(),
        ),
        SchedulingPorts(
            constraints = constraints,
            fixations = ExposedTeacherFixationRepository(),
            generations = ExposedGenerationRepository(),
            chosenTimetables = ExposedChosenTimetableRepository(),
            solver = TimefoldTimetableSolver(),
            executor = SingleThreadGenerationExecutor(),
            eventLog = Slf4jGenerationEventLog(),
            clock = Clock.systemUTC(),
        ),
    )
}
