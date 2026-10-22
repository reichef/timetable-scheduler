package timetablescheduler.application.school

import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.RoomFeatureRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.school.School

/** Loads the school for a change and checks optimistic locking against [expectedVersion]. */
internal fun OutcomeScope.loadSchoolForChange(
    schools: SchoolRepository,
    schoolId: SchoolId,
    expectedVersion: Int,
): School {
    val school = schools.findById(schoolId).orNotFound()
    if (school.version != expectedVersion) {
        fail(ApplicationError.VersionConflict)
    }
    return school
}

/** Renames the installation's school; with one school no name uniqueness check is needed (FR-001). */
class RenameSchool(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        newName: String,
        expectedVersion: Int,
    ): UseCaseResult<School> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val school = loadSchoolForChange(schools, schoolId, expectedVersion)
                val renamed = school.rename(newName).bind()
                requireUpdated(schools.update(renamed))
                renamed.copy(version = renamed.version + 1)
            }
        }
}

data class SchoolDashboard(
    val school: School,
    val roomCount: Int,
    val roomFeatureCount: Int,
    val teachingSlotsPerWeek: Int,
)

class GetSchoolDashboard(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
    private val rooms: RoomRepository,
    private val roomFeatures: RoomFeatureRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
    ): UseCaseResult<SchoolDashboard> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val school = schools.findById(schoolId).orNotFound()
                SchoolDashboard(
                    school = school,
                    roomCount = rooms.findBySchool(schoolId).size,
                    roomFeatureCount = roomFeatures.findBySchool(schoolId).size,
                    teachingSlotsPerWeek = schools.findTimeGrid(schoolId).teachingSlotsPerWeek(),
                )
            }
        }
}

class SchoolUseCases(
    val list: ListAccessibleSchools,
    val rename: RenameSchool,
    val dashboard: GetSchoolDashboard,
)
