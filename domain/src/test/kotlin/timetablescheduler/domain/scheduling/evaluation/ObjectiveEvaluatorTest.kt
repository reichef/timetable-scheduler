package timetablescheduler.domain.scheduling.evaluation

import timetablescheduler.domain.scheduling.TestSchool
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import timetablescheduler.domain.scheduling.constraint.soft
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.ObjectiveValues
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

    private fun evaluate(
        lessons: List<LessonAssignment>,
        unplaced: Map<CourseKey, Int> = emptyMap(),
    ) = ObjectiveEvaluator.evaluate(school.problem(), TimetableCandidate(lessons, unplaced)).values

    @Test
    fun `class fill weights each class by its priority weight`() {
        val important = school.schoolClass(math to 10, weight = 20)
        val normal = school.schoolClass(math to 10, weight = 10)
        val teacher = school.teacher(40, setOf(math))
        val otherTeacher = school.teacher(40, setOf(math))
        val nineForImportant = (0 until 9).map { school.lesson(important, math, teacher, room, if (it < 5) MONDAY else TUESDAY, it % 5) }
        val tenForNormal =
            (0 until 10).map {
                school.lesson(
                    normal,
                    math,
                    otherTeacher,
                    school.room(),
                    if (it <
                        5
                    ) {
                        MONDAY
                    } else {
                        TUESDAY
                    },
                    it % 5,
                )
            }

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
        val lessons =
            listOf(
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

    @Test
    fun `a soft constraint selected as separate objective gets its own value 1 - violations over opportunities (FR-006a)`() {
        val class5a = school.schoolClass(math to 4)
        val teacher = school.teacher(10, setOf(math))
        val mathAtMostOne = soft(ConstraintRule.SubjectMaxPerDayInClass(ClassScope.SpecificClass(class5a), math, 1))
        val problem = school.problem().copy(constraints = listOf(mathAtMostOne), separateObjectiveIds = listOf(mathAtMostOne.id))
        val lessons =
            (0 until 3).map { school.lesson(class5a, math, teacher, room, MONDAY, it) } +
                school.lesson(class5a, math, teacher, room, TUESDAY, 0)

        val evaluation = ObjectiveEvaluator.evaluate(problem, TimetableCandidate(lessons, emptyMap()))

        // 2 excess periods of 4 opportunities (the subject's periods)
        assertEquals(listOf(0.5), evaluation.values.separate)
        assertEquals(1.0, evaluation.values.otherSoft)
        assertEquals(mapOf(mathAtMostOne.id to 2), evaluation.softViolations)
    }

    @Test
    fun `all other soft constraints are combined by their weights 1, 3 and 9 (G3)`() {
        val class5a = school.schoolClass(math to 4)
        val teacher = school.teacher(10, setOf(math))
        val low = soft(ConstraintRule.ClassExcludedSlots(class5a, setOf(school.slot(MONDAY, 0))), SoftWeight.LOW)
        val high = soft(ConstraintRule.ClassExcludedSlots(class5a, setOf(school.slot(MONDAY, 1))), SoftWeight.HIGH)
        val problem = school.problem().copy(constraints = listOf(low, high))
        val lessons =
            listOf(school.lesson(class5a, math, teacher, room, MONDAY, 0), school.lesson(class5a, math, teacher, room, TUESDAY, 0))

        val evaluation = ObjectiveEvaluator.evaluate(problem, TimetableCandidate(lessons, emptyMap()))

        // low violated once of 4 opportunities, high not at all: 1 − (1·1) / (1·4 + 9·4)
        assertEquals(ObjectiveValues.round4(1 - 1.0 / 40), evaluation.values.otherSoft)
        assertEquals(mapOf(low.id to 1, high.id to 0), evaluation.softViolations)
    }
}
