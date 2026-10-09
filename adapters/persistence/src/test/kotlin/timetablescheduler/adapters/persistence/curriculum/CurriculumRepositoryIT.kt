package timetablescheduler.adapters.persistence.curriculum

import timetablescheduler.adapters.persistence.PostgresTestBase
import timetablescheduler.adapters.persistence.room.ExposedRoomFeatureRepository
import timetablescheduler.adapters.persistence.school.ExposedSchoolRepository
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.room.RoomFeature
import timetablescheduler.domain.school.School
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurriculumRepositoryIT : PostgresTestBase() {

    private val schools = ExposedSchoolRepository()
    private val features = ExposedRoomFeatureRepository()
    private val subjects = ExposedSubjectRepository()
    private val levels = ExposedClassLevelRepository()
    private val classes = ExposedSchoolClassRepository()

    private fun <T> ValidationResult<T>.valid(): T = (this as ValidationResult.Valid).value

    private fun storedSchool(): School = School.create("Gymnasium Nord").valid().also { inTransaction { schools.insert(it) } }

    private fun storedFeature(schoolId: SchoolId, name: String): RoomFeature =
        RoomFeature.create(schoolId, name).valid().also { inTransaction { features.insert(it) } }

    private fun storedSubject(schoolId: SchoolId, name: String, featureIds: Set<RoomFeatureId> = emptySet()): Subject =
        Subject.create(schoolId, name, featureIds, featureIds).valid().also { inTransaction { subjects.insert(it) } }

    private fun storedLevel(schoolId: SchoolId, identifier: String, vararg entries: CurriculumEntry): ClassLevel {
        val level = ClassLevel.create(schoolId, identifier).valid()
            .update(identifier, entries.toList(), entries.map { it.subjectId }.toSet()).valid()
        inTransaction { levels.insert(level) }
        return level
    }

    private fun storedClass(level: ClassLevel, suffix: String): SchoolClass =
        SchoolClass.create(level.id, suffix).valid().also { inTransaction { classes.insert(it) } }

    @Test
    fun `subject round trip with required features and lookup by feature`() {
        val school = storedSchool()
        val lab = storedFeature(school.id, "Chemieraum")
        val chemie = storedSubject(school.id, "Chemie", setOf(lab.id))
        storedSubject(school.id, "Mathematik")

        assertEquals(chemie, inTransaction { subjects.findById(chemie.id) })
        assertEquals(chemie, inTransaction { subjects.findByNameIgnoreCase(school.id, "CHEMIE") })
        assertEquals(listOf(chemie), inTransaction { subjects.findRequiringFeature(lab.id) })
        assertEquals(2, inTransaction { subjects.findBySchool(school.id) }.size)
    }

    @Test
    fun `subject update uses optimistic locking and replaces features`() {
        val school = storedSchool()
        val lab = storedFeature(school.id, "Chemieraum")
        val chemie = storedSubject(school.id, "Chemie", setOf(lab.id))

        assertTrue(inTransaction { subjects.update(chemie.update("Chemie", emptySet(), setOf(lab.id)).valid()) })
        assertFalse(inTransaction { subjects.update(chemie) })

        val reloaded = inTransaction { subjects.findById(chemie.id) }!!
        assertEquals(emptySet(), reloaded.requiredFeatureIds)
        assertEquals(1, reloaded.version)
    }

    @Test
    fun `subject name is unique per school in the database`() {
        val school = storedSchool()
        storedSubject(school.id, "Mathematik")

        assertTrue(runCatching { storedSubject(school.id, "mathematik") }.isFailure)
    }

    @Test
    fun `class level keeps its curriculum in order and is found by subject`() {
        val school = storedSchool()
        val mathe = storedSubject(school.id, "Mathematik")
        val deutsch = storedSubject(school.id, "Deutsch")
        val level = storedLevel(school.id, "6", CurriculumEntry(mathe.id, 4), CurriculumEntry(deutsch.id, 3))

        assertEquals(level, inTransaction { levels.findById(level.id) })
        assertEquals(level, inTransaction { levels.findByIdentifierIgnoreCase(school.id, "6") })
        assertEquals(listOf(level), inTransaction { levels.findUsingSubject(deutsch.id) })
    }

    @Test
    fun `class level update replaces the curriculum with version check`() {
        val school = storedSchool()
        val mathe = storedSubject(school.id, "Mathematik")
        val level = storedLevel(school.id, "6", CurriculumEntry(mathe.id, 4))
        val changed = level.update("6", listOf(CurriculumEntry(mathe.id, 5)), setOf(mathe.id)).valid()

        assertTrue(inTransaction { levels.update(changed) })
        assertFalse(inTransaction { levels.update(changed) })
        assertEquals(listOf(CurriculumEntry(mathe.id, 5)), inTransaction { levels.findById(level.id) }!!.curriculum)
    }

    @Test
    fun `classes are stored per level with unique suffix and can be deleted`() {
        val school = storedSchool()
        val level = storedLevel(school.id, "6")
        val class6a = storedClass(level, "a")
        val class6b = storedClass(level, "b")

        assertEquals(setOf(class6a, class6b), inTransaction { classes.findByLevel(level.id) }.toSet())
        assertEquals(setOf(class6a, class6b), inTransaction { classes.findBySchool(school.id) }.toSet())
        assertTrue(runCatching { storedClass(level, "A") }.isFailure)

        assertTrue(inTransaction { classes.update(class6a.update("c", 20).valid()) })
        assertEquals(20, inTransaction { classes.findById(class6a.id) }!!.priorityWeight)

        inTransaction { classes.delete(class6b.id) }
        assertNull(inTransaction { classes.findById(class6b.id) })
    }

    @Test
    fun `deleting a level and a subject removes them`() {
        val school = storedSchool()
        val mathe = storedSubject(school.id, "Mathematik")
        val level = storedLevel(school.id, "6", CurriculumEntry(mathe.id, 4))

        inTransaction { levels.delete(level.id) }
        inTransaction { subjects.delete(mathe.id) }

        assertNull(inTransaction { levels.findById(level.id) })
        assertNull(inTransaction { subjects.findById(mathe.id) })
    }
}
