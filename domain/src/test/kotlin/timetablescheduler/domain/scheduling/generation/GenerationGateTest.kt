package timetablescheduler.domain.scheduling.generation

import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.readiness.ReadinessIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerationGateTest {

    private val teacherAboveGrid = ReadinessIssue.TeacherLoadExceedsGrid(TeacherId.new(), 2)
    private val levelAboveGrid = ReadinessIssue.LevelExceedsGrid(ClassLevelId.new(), 3)
    private val noTeacher = ReadinessIssue.SubjectNoQualifiedTeacher(SubjectId.new())
    private val zeroLoad = ReadinessIssue.TeacherZeroLoad(TeacherId.new())

    @Test
    fun `teacher load and level above the grid block the start (FR-010)`() {
        val gate = GenerationGate.classify(listOf(teacherAboveGrid, noTeacher, levelAboveGrid, zeroLoad))

        assertEquals(listOf(teacherAboveGrid, levelAboveGrid), gate.blocking)
        assertEquals(listOf(noTeacher, zeroLoad), gate.warnings)
    }

    @Test
    fun `without issues nothing blocks and nothing needs confirming`() {
        val gate = GenerationGate.classify(emptyList())

        assertTrue(gate.blocking.isEmpty())
        assertTrue(gate.warnings.isEmpty())
    }
}
