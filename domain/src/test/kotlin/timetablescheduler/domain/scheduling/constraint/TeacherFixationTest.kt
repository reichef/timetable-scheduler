package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.TestSchool
import timetablescheduler.domain.validation.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals

class TeacherFixationTest {
    private val school = TestSchool()
    private val math = school.subject()
    private val german = school.subject()
    private val art = school.subject()
    private val schmidt = school.teacher(6, setOf(math, german))
    private val class5a = school.schoolClass(math to 4, german to 3)
    private val class5b = school.schoolClass(math to 4)

    private fun fixation(
        classId: timetablescheduler.domain.common.SchoolClassId,
        subject: timetablescheduler.domain.common.SubjectId,
    ) = TeacherFixation(TeacherFixationId.new(), SchoolId.new(), classId, subject, schmidt, 0)

    @Test
    fun `a qualified teacher with enough load can be fixed (FR-013a)`() {
        assertEquals(emptyList(), TeacherFixationRules.validate(fixation(class5a, math), school.problem()))
    }

    @Test
    fun `a teacher not qualified for the subject is rejected`() {
        school.schoolClass(art to 2)
        val problem = school.problem()
        val withArt = problem.classes.last().id

        assertEquals(listOf(ValidationError.TeacherNotQualified), TeacherFixationRules.validate(fixation(withArt, art), problem))
    }

    @Test
    fun `a subject outside the class's curriculum is rejected`() {
        assertEquals(
            listOf(ValidationError.SubjectNotInCurriculum),
            TeacherFixationRules.validate(fixation(class5b, german), school.problem()),
        )
    }

    @Test
    fun `fixed periods above the teacher's weekly load are rejected naming load and periods`() {
        val existing = fixation(class5a, math)
        val problem = school.problem().copy(fixations = listOf(existing))

        // 4 already fixed + 4 more = 8 > load 6
        assertEquals(listOf(ValidationError.FixedPeriodsExceedLoad(6, 8)), TeacherFixationRules.validate(fixation(class5b, math), problem))
    }

    @Test
    fun `after master-data changes an invalid fixation reports why`() {
        val stored = fixation(class5a, german)
        val problem = school.problem().copy(fixations = listOf(stored))
        val withoutQualification = problem.copy(teachers = problem.teachers.map { it.copy(qualifiedSubjectIds = setOf(math)) })
        val loadReduced = problem.copy(teachers = problem.teachers.map { it.copy(weeklyLoad = 2) })
        val subjectRemoved =
            problem.copy(
                classes =
                    problem.classes.map {
                        it.copy(
                            curriculum =
                                it.curriculum.filter { entry ->
                                    entry.subjectId ==
                                        math
                                },
                        )
                    },
            )

        assertEquals(null, TeacherFixationRules.invalidityOf(stored, problem))
        assertEquals(FixationInvalidity.NOT_QUALIFIED, TeacherFixationRules.invalidityOf(stored, withoutQualification))
        assertEquals(FixationInvalidity.EXCEEDS_LOAD, TeacherFixationRules.invalidityOf(stored, loadReduced))
        assertEquals(FixationInvalidity.NOT_IN_CURRICULUM, TeacherFixationRules.invalidityOf(stored, subjectRemoved))
    }
}
