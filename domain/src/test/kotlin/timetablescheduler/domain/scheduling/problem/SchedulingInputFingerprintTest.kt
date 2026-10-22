package timetablescheduler.domain.scheduling.problem

import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.scheduling.TestSchool
import java.time.DayOfWeek.MONDAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SchedulingInputFingerprintTest {

    private val school = TestSchool()
    private val lab = RoomFeatureId.new()
    private val room = school.room(setOf(lab))
    private val math = school.subject()
    private val chemistry = school.subject(setOf(lab))
    private val teacher = school.teacher(10, setOf(math, chemistry))
    private val schoolClass = school.schoolClass(math to 4, chemistry to 2)
    private val problem = school.problem()
    private val original = SchedulingInputFingerprint.of(problem)

    private fun assertChanges(changed: SchedulingProblem) = assertNotEquals(original, SchedulingInputFingerprint.of(changed))

    private fun SchedulingProblem.mapTeacher(change: (ProblemTeacher) -> ProblemTeacher) = copy(teachers = teachers.map(change))

    private fun SchedulingProblem.mapClass(change: (ProblemClass) -> ProblemClass) = copy(classes = classes.map(change))

    @Test
    fun `is a 64 character hex SHA-256 hash`() {
        assertEquals(64, original.length)
        assertEquals(original, original.lowercase())
    }

    @Test
    fun `the order of entries does not matter`() {
        val reordered = problem.copy(
            rooms = problem.rooms.reversed(),
            subjects = problem.subjects.reversed(),
            classes = problem.classes.map { it.copy(curriculum = it.curriculum.reversed()) },
        )

        assertEquals(original, SchedulingInputFingerprint.of(reordered))
    }

    @Test
    fun `every scheduling-relevant change changes the hash`() {
        assertChanges(problem.mapTeacher { it.copy(unavailable = setOf(school.slot(MONDAY, 0))) })
        assertChanges(problem.mapTeacher { it.copy(weeklyLoad = 9) })
        assertChanges(problem.mapTeacher { it.copy(qualifiedSubjectIds = setOf(math)) })
        assertChanges(problem.mapClass { it.copy(curriculum = listOf(CurriculumEntry(math, 3), CurriculumEntry(chemistry, 2))) })
        assertChanges(problem.mapClass { it.copy(priorityWeight = 11) })
        assertChanges(problem.mapClass { it.copy(homeRoomId = room) })
        assertChanges(problem.copy(rooms = problem.rooms.map { it.copy(featureIds = emptySet()) }))
        assertChanges(problem.copy(days = problem.days.map { it.copy(slots = it.slots.drop(1)) }))
        assertEquals(teacher, problem.teachers.single().id)
        assertEquals(schoolClass, problem.classes.single().id)
    }
}
