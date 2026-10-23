package timetablescheduler.adapters.persistence.scheduling

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import timetablescheduler.adapters.persistence.account.UserAccountTable
import timetablescheduler.adapters.persistence.school.SchoolTable
import timetablescheduler.application.port.ChosenTimetableRepository
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.generation.ChosenTimetable
import timetablescheduler.domain.scheduling.generation.GenerationId
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import java.time.DayOfWeek
import java.time.ZoneOffset

object ChosenTimetableTable : Table("chosen_timetable") {
    val schoolId = javaUUID("school_id").references(SchoolTable.id)
    val alternativeId = javaUUID("alternative_id").references(AlternativeTable.id)
    val generationId = javaUUID("generation_id").references(GenerationTable.id)
    val chosenBy = javaUUID("chosen_by").references(UserAccountTable.id)
    val chosenAt = timestampWithTimeZone("chosen_at")
    val version = integer("version")
    override val primaryKey = PrimaryKey(schoolId)
}

/** Unlike alternatives, the chosen lessons reference master data, so it cannot be deleted while in use (FR-016). */
object ChosenLessonTable : Table("chosen_lesson") {
    val schoolId = javaUUID("school_id").references(ChosenTimetableTable.schoolId)
    val position = integer("position")
    val classId = javaUUID("class_id")
    val subjectId = javaUUID("subject_id")
    val teacherId = javaUUID("teacher_id")
    val roomId = javaUUID("room_id")
    val dayOfWeek = varchar("day_of_week", 9)
    val timeSlotId = javaUUID("time_slot_id")
    override val primaryKey = PrimaryKey(schoolId, position)
}

object ChosenUnplacedTable : Table("chosen_unplaced") {
    val schoolId = javaUUID("school_id").references(ChosenTimetableTable.schoolId)
    val classId = javaUUID("class_id")
    val subjectId = javaUUID("subject_id")
    val periods = integer("periods")
    override val primaryKey = PrimaryKey(schoolId, classId, subjectId)
}

class ExposedChosenTimetableRepository : ChosenTimetableRepository {
    override fun find(schoolId: SchoolId): ChosenTimetable? {
        val row = ChosenTimetableTable.selectAll().where { ChosenTimetableTable.schoolId eq schoolId.value }.singleOrNull() ?: return null
        val lessons =
            ChosenLessonTable
                .selectAll()
                .where { ChosenLessonTable.schoolId eq schoolId.value }
                .sortedBy { it[ChosenLessonTable.position] }
                .map { it.toLesson() }
        val unplaced =
            ChosenUnplacedTable
                .selectAll()
                .where { ChosenUnplacedTable.schoolId eq schoolId.value }
                .associate {
                    CourseKey(SchoolClassId(it[ChosenUnplacedTable.classId]), SubjectId(it[ChosenUnplacedTable.subjectId])) to
                        it[ChosenUnplacedTable.periods]
                }
        return ChosenTimetable(
            schoolId = schoolId,
            alternativeId = AlternativeId(row[ChosenTimetableTable.alternativeId]),
            generationId = GenerationId(row[ChosenTimetableTable.generationId]),
            chosenBy = AccountId(row[ChosenTimetableTable.chosenBy]),
            chosenAt = row[ChosenTimetableTable.chosenAt].toInstant(),
            lessons = lessons,
            unplaced = unplaced,
            version = row[ChosenTimetableTable.version],
        )
    }

    /** Inserts the first choice or replaces the current one when its version is still [expectedVersion] (FR-028). */
    override fun save(
        chosen: ChosenTimetable,
        expectedVersion: Int?,
    ): Boolean {
        val schoolId = chosen.schoolId.value
        val saved =
            if (expectedVersion == null) {
                ChosenTimetableTable
                    .insertIgnore {
                        it[ChosenTimetableTable.schoolId] = schoolId
                        it[alternativeId] = chosen.alternativeId.value
                        it[generationId] = chosen.generationId.value
                        it[chosenBy] = chosen.chosenBy.value
                        it[chosenAt] = chosen.chosenAt.atOffset(ZoneOffset.UTC)
                        it[version] = chosen.version
                    }.insertedCount > 0
            } else {
                ChosenTimetableTable.update(
                    { (ChosenTimetableTable.schoolId eq schoolId) and (ChosenTimetableTable.version eq expectedVersion) },
                ) {
                    it[alternativeId] = chosen.alternativeId.value
                    it[generationId] = chosen.generationId.value
                    it[chosenBy] = chosen.chosenBy.value
                    it[chosenAt] = chosen.chosenAt.atOffset(ZoneOffset.UTC)
                    it[version] = chosen.version
                } > 0
            }
        if (!saved) {
            return false
        }

        ChosenLessonTable.deleteWhere { ChosenLessonTable.schoolId eq schoolId }
        ChosenUnplacedTable.deleteWhere { ChosenUnplacedTable.schoolId eq schoolId }
        ChosenLessonTable.batchInsert(chosen.lessons.withIndex()) { (position, lesson) ->
            this[ChosenLessonTable.schoolId] = schoolId
            this[ChosenLessonTable.position] = position
            this[ChosenLessonTable.classId] = lesson.classId.value
            this[ChosenLessonTable.subjectId] = lesson.subjectId.value
            this[ChosenLessonTable.teacherId] = lesson.teacherId.value
            this[ChosenLessonTable.roomId] = lesson.roomId.value
            this[ChosenLessonTable.dayOfWeek] = lesson.daySlot.day.name
            this[ChosenLessonTable.timeSlotId] = lesson.daySlot.slotId.value
        }
        ChosenUnplacedTable.batchInsert(chosen.unplaced.entries) { (course, periods) ->
            this[ChosenUnplacedTable.schoolId] = schoolId
            this[ChosenUnplacedTable.classId] = course.classId.value
            this[ChosenUnplacedTable.subjectId] = course.subjectId.value
            this[ChosenUnplacedTable.periods] = periods
        }

        return true
    }

    private fun ResultRow.toLesson() =
        LessonAssignment(
            classId = SchoolClassId(this[ChosenLessonTable.classId]),
            subjectId = SubjectId(this[ChosenLessonTable.subjectId]),
            teacherId = TeacherId(this[ChosenLessonTable.teacherId]),
            roomId = RoomId(this[ChosenLessonTable.roomId]),
            daySlot = DaySlot(DayOfWeek.valueOf(this[ChosenLessonTable.dayOfWeek]), TimeSlotId(this[ChosenLessonTable.timeSlotId])),
        )
}
