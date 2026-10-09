package timetablescheduler.domain.scheduling.evaluation

import timetablescheduler.domain.scheduling.TestSchool
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObjectiveEvaluatorTest {

    private val school = TestSchool(teachingSlotsPerDay = 6)
    private val room = school.room()
    private val math = school.subject()

    private fun evaluate(lessons: List<LessonAssignment>, unplaced: Map<CourseKey, Int> = emptyMap()) =
        ObjectiveEvaluator.evaluate(school.problem(), TimetableCandidate(lessons, unplaced)).values

    @Test
    fun `class fill weights each class by its priority weight`() {
        val important = school.schoolClass(math to 10, weight = 20)
        val normal = school.schoolClass(math to 10, weight = 10)
        val teacher = school.teacher(40, setOf(math))
        val otherTeacher = school.teacher(40, setOf(math))
        val nineForImportant = (0 until 9).map { school.lesson(important, math, teacher, room, if (it < 5) MONDAY else TUESDAY, it % 5) }
        val tenForNormal = (0 until 10).map { school.lesson(normal, math, otherTeacher, school.room(), if (it < 5) MONDAY else TUESDAY, it % 5) }

        val values = evaluate(nineForImportant + tenForNormal)

        // (20·9 + 10·10) / (20·10 + 10·10) = 280 / 300
        assertEquals(0.9333, values.classFill)
    }

    @Test
    fun `class fill is 1 when nothing is required`() {
        assertEquals(1.0, evaluate(emptyList()).classFill)
    }

    @Test
    fun `teacher load usage counts at most the load and ignores teachers without load`() {
        val class5a = school.schoolClass(math to 6)
        val full = school.teacher(2, setOf(math))
        val half = school.teacher(4, setOf(math))
        school.teacher(0, setOf(math))
        val lessons = listOf(
            school.lesson(class5a, math, full, room, MONDAY, 0),
            school.lesson(class5a, math, full, room, MONDAY, 1),
            school.lesson(class5a, math, half, room, MONDAY, 2),
            school.lesson(class5a, math, half, room, MONDAY, 3),
        )

        // (min(2,2) + min(2,4)) / (2 + 4)
        assertEquals(0.6667, evaluate(lessons).teacherLoadUsage)
    }

    @Test
    fun `teacher load usage is 1 when no teacher has a load`() {
        school.teacher(0, setOf(math))

        assertEquals(1.0, evaluate(emptyList()).teacherLoadUsage)
    }

    @Test
    fun `without soft constraints the other-soft objective is 1 and there are no separate objectives or stability`() {
        val values = evaluate(emptyList())

        assertEquals(1.0, values.otherSoft)
        assertEquals(emptyList(), values.separate)
        assertNull(values.stability)
        assertEquals(listOf(1.0, 1.0, 1.0), values.asVector())
    }
}
