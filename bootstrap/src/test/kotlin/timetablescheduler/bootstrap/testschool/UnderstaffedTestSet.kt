/*
 * TEST DATA — NOT A REAL SCHOOL.
 * Teacher-shortage variant of the fictitious test school: same classes, rooms, curricula, constraints and fixations,
 * but too few teacher periods, so some classes and subjects can certainly not be served completely.
 * Description: specs/002-timetable-generation/test-set-bw-gymnasium.md, section "Variant: teacher shortage".
 */
package timetablescheduler.bootstrap.testschool

object UnderstaffedTestSet : TestSchoolData by BwGymnasiumTestSet {

    /** Teachers missing in the variant; Sport, Musik and Bildende Kunst lose their second teacher. */
    private val removedTeachers = setOf("T16", "T27", "T29", "T31")

    /** Reduced weekly loads, e.g. part-time instead of full-time positions. */
    private val reducedLoads = mapOf(
        "T07" to 18,
        "T08" to 18,
        "T21" to 18,
        "T28" to 18,
        "T30" to 18,
        "T19" to 15,
        "T22" to 15,
    )

    override val schoolName = "${BwGymnasiumTestSet.SCHOOL_NAME} (Lehrkräftemangel)"

    override val teachers: List<TestTeacher> = BwGymnasiumTestSet.teachers
        .filter { it.code !in removedTeachers }
        .map { teacher ->
            val reducedLoad = reducedLoads[teacher.code]

            if (reducedLoad == null) {
                teacher
            } else {
                teacher.copy(weeklyLoad = reducedLoad)
            }
        }

    /** The constraints of the first test set, without those about removed teachers. */
    override val constraints: List<TestConstraint> = BwGymnasiumTestSet.constraints.filter { constraint ->
        val teacherCode = when (val rule = constraint.rule) {
            is TestRule.TeacherExcludedSlots -> rule.teacherCode
            is TestRule.TeacherMaxPeriodsPerDay -> rule.teacherCode
            is TestRule.TeacherMaxGapsPerDay -> rule.teacherCode
            else -> null
        }

        teacherCode !in removedTeachers
    }
}
