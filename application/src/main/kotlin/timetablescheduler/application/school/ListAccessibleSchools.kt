package timetablescheduler.application.school

import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.school.School

/** Lists only the schools assigned to the planner (FR-028a), ordered by name. */
class ListAccessibleSchools(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
) {
    fun execute(actor: AccountId): UseCaseResult<List<School>> =
        transactions.inTransaction {
            outcome {
                val planner = access.requirePlanner(actor).bind()
                schools.findByIds(planner.schoolIds).sortedBy { it.name.lowercase() }
            }
        }
}
