package timetablescheduler.adapters.persistence.scheduling

import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import timetablescheduler.adapters.persistence.PostgresTestBase
import timetablescheduler.adapters.persistence.curriculum.ExposedClassLevelRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedSchoolClassRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedSubjectRepository
import timetablescheduler.adapters.persistence.school.ExposedSchoolRepository
import timetablescheduler.adapters.persistence.teacher.ExposedTeacherRepository
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.constraint.TeacherFixationId
import timetablescheduler.domain.school.School
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.teacher.Teacher
import timetablescheduler.domain.validation.ValidationResult
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SchedulingConstraintRepositoryIT : PostgresTestBase() {
    private val schools = ExposedSchoolRepository()
    private val subjects = ExposedSubjectRepository()
    private val levels = ExposedClassLevelRepository()
    private val classes = ExposedSchoolClassRepository()
    private val teachers = ExposedTeacherRepository()
    private val constraints = ExposedSchedulingConstraintRepository()
    private val fixations = ExposedTeacherFixationRepository()

    private fun <T> ValidationResult<T>.valid(): T = (this as ValidationResult.Valid).value

    /** A school with one class 5a (Mathematik 4), teacher SCHA and two teaching slots on Monday. */
    private inner class Data {
        val school = School.create("Gymnasium Nord").valid().also { inTransaction { schools.insert(it) } }
        val grid =
            TimeGrid
                .empty()
                .withTeachingDays(setOf(DayOfWeek.MONDAY))
                .withSlotAdded("1. Stunde", LocalTime.of(8, 0), LocalTime.of(8, 45), SlotKind.TEACHING)
                .valid()
                .withSlotAdded("2. Stunde", LocalTime.of(8, 50), LocalTime.of(9, 35), SlotKind.TEACHING)
                .valid()
                .also { inTransaction { schools.saveTimeGrid(school.id, it) } }
        val slots = inTransaction { schools.findTimeGrid(school.id) }.slots.map { DaySlot(DayOfWeek.MONDAY, it.id) }
        val math = Subject.create(school.id, "Mathematik", emptySet(), emptySet()).valid().also { inTransaction { subjects.insert(it) } }
        val level =
            ClassLevel
                .create(school.id, "5")
                .valid()
                .update("5", listOf(CurriculumEntry(math.id, 4)), setOf(math.id))
                .valid()
                .also { inTransaction { levels.insert(it) } }
        val class5a = SchoolClass.create(level.id, "a").valid().also { inTransaction { classes.insert(it) } }
        val schmidt =
            Teacher
                .create(school.id, "Anna Schmidt", "SCHA", 10, 40)
                .valid()
                .withQualifications(setOf(math.id), setOf(math.id))
                .valid()
                .also { inTransaction { teachers.insert(it) } }

        fun constraint(
            rule: ConstraintRule,
            kind: ConstraintKind = ConstraintKind.HARD,
            weight: SoftWeight? = null,
        ) = SchedulingConstraint(SchedulingConstraintId.new(), school.id, kind, weight, true, rule, 0)
    }

    @Test
    fun `every catalogue type round-trips with slots and scope`() {
        val data = Data()
        val rules =
            listOf(
                ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(data.class5a.id), 5),
                ConstraintRule.ClassMinPeriodsPerDay(ClassScope.AllClasses, 2),
                ConstraintRule.ClassNoGaps(ClassScope.AllClasses),
                ConstraintRule.TeacherMaxPeriodsPerDay(data.schmidt.id, 4),
                ConstraintRule.TeacherMaxGapsPerDay(data.schmidt.id, 1),
                ConstraintRule.SubjectMaxPerDayInClass(ClassScope.AllClasses, null, 2),
                ConstraintRule.SubjectMaxPerDayInClass(ClassScope.SpecificClass(data.class5a.id), data.math.id, 1),
                ConstraintRule.SubjectDoublePeriodsInClass(data.class5a.id, data.math.id, acrossBreaks = true),
                ConstraintRule.ClassExcludedSlots(data.class5a.id, data.slots.toSet()),
                ConstraintRule.TeacherExcludedSlots(data.schmidt.id, setOf(data.slots.first())),
                ConstraintRule.SubjectExcludedSlots(data.math.id, null, setOf(data.slots.last())),
            )
        val stored =
            rules.mapIndexed { index, rule ->
                val hard = index % 2 == 0

                data.constraint(rule, if (hard) ConstraintKind.HARD else ConstraintKind.SOFT, if (hard) null else SoftWeight.HIGH)
            }

        inTransaction { stored.forEach(constraints::insert) }

        assertEquals(stored.toSet(), inTransaction { constraints.findBySchool(data.school.id) }.toSet())
    }

    @Test
    fun `update checks the version and switching off is stored`() {
        val data = Data()
        val constraint = data.constraint(ConstraintRule.ClassNoGaps(ClassScope.AllClasses))
        inTransaction { constraints.insert(constraint) }

        assertEquals(true, inTransaction { constraints.update(constraint.copy(active = false)) })
        assertEquals(false, inTransaction { constraints.update(constraint.copy(active = true)) })
        assertEquals(false, inTransaction { constraints.findById(constraint.id) }!!.active)
    }

    @Test
    fun `constraints referring to an entry are found and block deleting it (FR-016)`() {
        val data = Data()
        val forClass = data.constraint(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(data.class5a.id), 5))
        val forTeacher = data.constraint(ConstraintRule.TeacherExcludedSlots(data.schmidt.id, setOf(data.slots.first())))
        val forSubject = data.constraint(ConstraintRule.SubjectMaxPerDayInClass(ClassScope.AllClasses, data.math.id, 2))
        inTransaction { listOf(forClass, forTeacher, forSubject).forEach(constraints::insert) }

        assertEquals(listOf(forClass.id), inTransaction { constraints.findReferencingClass(data.class5a.id) }.map { it.id })
        assertEquals(listOf(forTeacher.id), inTransaction { constraints.findReferencingTeacher(data.schmidt.id) }.map { it.id })
        assertEquals(listOf(forSubject.id), inTransaction { constraints.findReferencingSubject(data.math.id) }.map { it.id })
        assertEquals(listOf(forTeacher.id), inTransaction { constraints.findReferencingSlot(data.slots.first().slotId) }.map { it.id })
        assertFailsWith<ExposedSQLException> { inTransaction { classes.delete(data.class5a.id) } }
    }

    @Test
    fun `a soft constraint needs a weight, a hard one must not have one`() {
        val data = Data()

        // The domain cannot even build such a constraint; the database rejects it as well.
        assertFailsWith<ExposedSQLException> {
            inTransaction {
                TransactionManager.current().exec(
                    "INSERT INTO scheduling_constraint (id, school_id, type, kind, weight, active, all_classes, version) " +
                        "VALUES (gen_random_uuid(), '${data.school.id.value}', 'CLASS_NO_GAPS', 'HARD', 'LOW', true, true, 0)",
                )
            }
        }
    }

    @Test
    fun `deleting a constraint removes its slots`() {
        val data = Data()
        val constraint = data.constraint(ConstraintRule.ClassExcludedSlots(data.class5a.id, data.slots.toSet()))
        inTransaction { constraints.insert(constraint) }

        inTransaction { constraints.delete(constraint.id) }

        assertNull(inTransaction { constraints.findById(constraint.id) })
    }

    @Test
    fun `teacher fixations round-trip and are unique per class and subject (FR-013a)`() {
        val data = Data()
        val fixation = TeacherFixation(TeacherFixationId.new(), data.school.id, data.class5a.id, data.math.id, data.schmidt.id, 0)
        inTransaction { fixations.insert(fixation) }

        assertEquals(fixation, inTransaction { fixations.findByCourse(data.class5a.id, data.math.id) })
        assertEquals(listOf(fixation), inTransaction { fixations.findByTeacher(data.schmidt.id) })
        assertEquals(listOf(fixation), inTransaction { fixations.findByClass(data.class5a.id) })
        assertEquals(listOf(fixation), inTransaction { fixations.findBySubject(data.math.id) })

        assertFailsWith<ExposedSQLException> { inTransaction { fixations.insert(fixation.copy(id = TeacherFixationId.new())) } }

        assertEquals(true, inTransaction { fixations.update(fixation) })
        assertEquals(1, inTransaction { fixations.findById(fixation.id) }!!.version)
    }

    @Test
    fun `a deleted teacher fixation is gone`() {
        val data = Data()
        val fixation = TeacherFixation(TeacherFixationId.new(), data.school.id, data.class5a.id, data.math.id, data.schmidt.id, 0)
        inTransaction { fixations.insert(fixation) }

        inTransaction { fixations.delete(fixation.id) }

        assertNull(inTransaction { fixations.findById(fixation.id) })
        assertEquals(emptyList(), inTransaction { fixations.findBySubject(data.math.id) })
    }
}
