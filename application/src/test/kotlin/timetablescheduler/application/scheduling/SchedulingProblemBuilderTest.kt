package timetablescheduler.application.scheduling

import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.teacher.Unavailability
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import kotlin.test.Test
import kotlin.test.assertEquals

class SchedulingProblemBuilderTest {

    private val world = MasterDataWorld()
    private val nord = world.school("Gymnasium Nord")
    private val builder get() = SchedulingProblemBuilder(world.schools, world.rooms, world.subjects, world.classLevels, world.schoolClasses, world.teachers)

    @Test
    fun `teaching day-slots exclude breaks and slots removed for a day`() {
        world.teachingDays(nord.id, MONDAY, FRIDAY)
        val first = world.slot(nord.id, "1. Stunde", "08:00", "08:45")
        val pause = world.slot(nord.id, "Pause", "08:45", "09:00", SlotKind.BREAK)
        val second = world.slot(nord.id, "2. Stunde", "09:00", "09:45")
        world.excludeOn(nord.id, second, FRIDAY)

        val problem = builder.build(nord.id)

        assertEquals(
            listOf(DaySlot(MONDAY, first.id), DaySlot(MONDAY, second.id), DaySlot(FRIDAY, first.id)),
            problem.teachingDaySlots(),
        )
        assertEquals(listOf(first.id, pause.id, second.id), problem.dayStructure(MONDAY)!!.slots.map { it.slotId })
        assertEquals(listOf(first.id, pause.id), problem.dayStructure(FRIDAY)!!.slots.map { it.slotId })
    }

    @Test
    fun `classes carry weight, home room and the curriculum of their level`() {
        val room = world.room(nord.id, "A-101")
        val math = world.subject(nord.id, "Mathematik")
        val level = world.classLevel(nord.id, "6", math to 4)
        val class6b = world.schoolClass(level, "b", priorityWeight = 20)
        world.schoolClasses.update(class6b.withHomeRoom(room.id))

        val problemClass = builder.build(nord.id).classes.single()

        assertEquals(class6b.id, problemClass.id)
        assertEquals(20, problemClass.priorityWeight)
        assertEquals(room.id, problemClass.homeRoomId)
        assertEquals(listOf(CurriculumEntry(math.id, 4)), problemClass.curriculum)
    }

    @Test
    fun `teachers carry qualifications, load and unavailability on current teaching days only`() {
        world.teachingDays(nord.id, MONDAY)
        val first = world.slot(nord.id, "1. Stunde", "08:00", "08:45")
        val math = world.subject(nord.id, "Mathematik")
        val schmidt = world.teacher(nord.id, "Anna Schmidt", "SCHA", weeklyLoad = 20, qualifiedSubjects = setOf(math.id))
        world.markUnavailable(schmidt, Unavailability(MONDAY, first.id), Unavailability(WEDNESDAY, first.id))

        val teacher = builder.build(nord.id).teachers.single()

        assertEquals(20, teacher.weeklyLoad)
        assertEquals(setOf(math.id), teacher.qualifiedSubjectIds)
        assertEquals(setOf(DaySlot(MONDAY, first.id)), teacher.unavailable)
    }

    @Test
    fun `rooms and subjects carry their features`() {
        val lab = world.feature(nord.id, "Chemieraum")
        val room = world.room(nord.id, "C-1", lab.id)
        val chemistry = world.subject(nord.id, "Chemie", setOf(lab.id))

        val problem = builder.build(nord.id)

        assertEquals(setOf(lab.id), problem.room(room.id)!!.featureIds)
        assertEquals(setOf(lab.id), problem.subject(chemistry.id)!!.requiredFeatureIds)
    }
}
