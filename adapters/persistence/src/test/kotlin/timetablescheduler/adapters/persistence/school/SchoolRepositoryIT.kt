package timetablescheduler.adapters.persistence.school

import timetablescheduler.adapters.persistence.PostgresTestBase
import timetablescheduler.adapters.persistence.account.ExposedUserAccountRepository
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.school.School
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.validation.ValidationResult
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchoolRepositoryIT : PostgresTestBase() {

    private val schools = ExposedSchoolRepository()
    private val accounts = ExposedUserAccountRepository()

    private fun <T> ValidationResult<T>.valid(): T = (this as ValidationResult.Valid).value

    private fun storedSchool(name: String): School = School.create(name).valid().also { inTransaction { schools.insert(it) } }

    private fun weekGrid(): TimeGrid =
        TimeGrid.empty().withTeachingDays(DayOfWeek.entries.filter { it <= FRIDAY }.toSet())
            .withSlotAdded("1. Stunde", LocalTime.of(8, 0), LocalTime.of(8, 45), SlotKind.TEACHING).valid()
            .withSlotAdded("Pause", LocalTime.of(8, 45), LocalTime.of(9, 0), SlotKind.BREAK).valid()
            .withSlotAdded("2. Stunde", LocalTime.of(9, 0), LocalTime.of(9, 45), SlotKind.TEACHING).valid()

    @Test
    fun `stores and reloads a time grid with exclusions`() {
        val school = storedSchool("Gymnasium Nord")
        val grid = weekGrid().let { it.withExclusions(it.slots.last().id, setOf(FRIDAY)).valid() }

        inTransaction { schools.saveTimeGrid(school.id, grid) }

        assertEquals(grid, inTransaction { schools.findTimeGrid(school.id) })
    }

    @Test
    fun `saving again keeps slot identities and applies changes`() {
        val school = storedSchool("Gymnasium Nord")
        val grid = weekGrid()
        inTransaction { schools.saveTimeGrid(school.id, grid) }
        val first = grid.slots.first()

        val changed = grid.withSlotRemoved(grid.slots.last().id)
            .withSlotUpdated(first.id, "Erste", first.startTime, first.endTime, SlotKind.TEACHING).valid()
            .withTeachingDays(setOf(DayOfWeek.MONDAY))
        inTransaction { schools.saveTimeGrid(school.id, changed) }

        val reloaded = inTransaction { schools.findTimeGrid(school.id) }
        assertEquals(changed, reloaded)
        assertEquals(first.id, reloaded.slots.first().id)
    }

    @Test
    fun `school without grid has an empty grid`() {
        val school = storedSchool("Gymnasium Nord")

        assertEquals(TimeGrid.empty(), inTransaction { schools.findTimeGrid(school.id) })
    }

    @Test
    fun `finds school by name case-insensitively and rejects duplicates`() {
        storedSchool("Gymnasium Nord")

        assertEquals("Gymnasium Nord", inTransaction { schools.findByNameIgnoreCase("GYMNASIUM NORD") }?.name)
        assertTrue(runCatching { storedSchool("gymnasium nord") }.isFailure)
    }

    @Test
    fun `update uses optimistic locking`() {
        val school = storedSchool("Gymnasium Nord")

        assertTrue(inTransaction { schools.update(school.rename("Neu").valid()) })
        assertFalse(inTransaction { schools.update(school.rename("Veraltet").valid()) })
        assertEquals(1, inTransaction { schools.findById(school.id) }?.version)
    }

    @Test
    fun `accounts with only this school are found and assignments can be removed before deleting`() {
        val nord = storedSchool("Gymnasium Nord")
        val sued = storedSchool("Realschule Süd")
        val onlySued = UserAccount.create("mueller", Role.PLANNER, setOf(sued.id)).valid()
        val both = UserAccount.create("admin", Role.PLANNER, setOf(nord.id, sued.id)).valid()
        inTransaction {
            accounts.insert(onlySued)
            accounts.insert(both)
            schools.saveTimeGrid(sued.id, TimeGrid.empty().withTeachingDays(setOf(DayOfWeek.MONDAY)))
        }

        assertEquals(listOf("mueller"), inTransaction { accounts.findAccountsWithOnlySchool(sued.id) }.map { it.username })

        inTransaction {
            accounts.removeSchoolAssignments(sued.id)
            schools.delete(sued.id)
        }
        assertNull(inTransaction { schools.findById(sued.id) })
        assertEquals(setOf(nord.id), inTransaction { accounts.findById(both.id) }?.schoolIds)
    }
}
