package timetablescheduler.domain.account

import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId

/** Guards FR-033: every school that has an active planner must keep at least one. */
object PlannerCoverage {

    /**
     * Returns the schools that lose their last active planner when [before] is changed to [after].
     * Only schools where [before] was an active planner can become uncovered; such a school stays covered
     * if [after] is still its active planner or any other active planner remains.
     */
    fun schoolsLeftWithoutActivePlanner(
        before: UserAccount,
        after: UserAccount,
        activePlannersOfSchool: (SchoolId) -> Set<AccountId>,
    ): Set<SchoolId> =
        before.schoolIds
            .filter { schoolId -> before.isActivePlannerOf(schoolId) && !after.isActivePlannerOf(schoolId) }
            .filter { schoolId -> (activePlannersOfSchool(schoolId) - before.id).isEmpty() }
            .toSet()
}
