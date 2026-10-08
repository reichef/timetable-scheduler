package timetablescheduler.application.school

import timetablescheduler.application.access.DependantSource
import timetablescheduler.application.access.DependantVisibilityFilter
import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.access.dependantsOf
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.RoomFeatureRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.school.School
import timetablescheduler.domain.validation.ValidationError

/** Rejects [name] if another school already uses it (FR-001: unique in installation, case-insensitive). */
internal fun OutcomeScope.requireUniqueSchoolName(schools: SchoolRepository, name: String, ownId: SchoolId?) {
    val existing = schools.findByNameIgnoreCase(name)
    if (existing != null && existing.id != ownId) {
        fail(ApplicationError.Invalid(listOf(ValidationError.DuplicateValue(School.FIELD_NAME))))
    }
}

/** Loads the school for a change and checks optimistic locking against [expectedVersion]. */
internal fun OutcomeScope.loadSchoolForChange(schools: SchoolRepository, schoolId: SchoolId, expectedVersion: Int): School {
    val school = schools.findById(schoolId).orNotFound()
    if (school.version != expectedVersion) fail(ApplicationError.VersionConflict)
    return school
}

/** A planner creates a further school and is assigned to it automatically (FR-028a). */
class CreateSchool(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
    private val accounts: UserAccountRepository,
) {
    fun execute(actor: AccountId, name: String): UseCaseResult<School> =
        transactions.inTransaction {
            outcome {
                val planner = access.requirePlanner(actor).bind()
                val school = School.create(name).bind()
                requireUniqueSchoolName(schools, school.name, ownId = null)
                schools.insert(school)
                val assignedPlanner = planner.withRoleAndSchools(planner.role, planner.schoolIds + school.id).bind()
                requireUpdated(accounts.update(assignedPlanner))
                school
            }
        }
}

class RenameSchool(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, newName: String, expectedVersion: Int): UseCaseResult<School> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val school = loadSchoolForChange(schools, schoolId, expectedVersion)
                val renamed = school.rename(newName).bind()
                requireUniqueSchoolName(schools, renamed.name, ownId = schoolId)
                requireUpdated(schools.update(renamed))
                renamed.copy(version = renamed.version + 1)
            }
        }
}

/** Deletion is blocked while anything still refers to the school (FR-023, FR-031a). */
class DeleteSchool(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
    private val accounts: UserAccountRepository,
    private val dependantSources: List<DependantSource<SchoolId>>,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, expectedVersion: Int): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                val planner = access.requireSchoolAccess(actor, schoolId).bind()
                loadSchoolForChange(schools, schoolId, expectedVersion)
                val dependants = dependantSources.dependantsOf(schoolId)
                if (dependants.isNotEmpty()) fail(DependantVisibilityFilter.deletionBlocked(dependants, planner))
                accounts.removeSchoolAssignments(schoolId)
                schools.delete(schoolId)
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
    fun execute(actor: AccountId, schoolId: SchoolId): UseCaseResult<SchoolDashboard> =
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
    val create: CreateSchool,
    val rename: RenameSchool,
    val delete: DeleteSchool,
    val dashboard: GetSchoolDashboard,
)
