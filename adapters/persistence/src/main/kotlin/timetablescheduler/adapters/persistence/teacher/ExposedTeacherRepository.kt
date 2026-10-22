package timetablescheduler.adapters.persistence.teacher

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.upperCase
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import timetablescheduler.adapters.persistence.curriculum.SubjectTable
import timetablescheduler.adapters.persistence.school.SchoolTable
import timetablescheduler.adapters.persistence.school.TimeSlotTable
import timetablescheduler.application.port.TeacherRepository
import timetablescheduler.application.port.TimeSlotUsage
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.teacher.Teacher
import timetablescheduler.domain.teacher.Unavailability
import java.time.DayOfWeek
import java.util.UUID

object TeacherTable : Table("teacher") {
    val id = javaUUID("id")
    val schoolId = javaUUID("school_id").references(SchoolTable.id)
    val name = varchar("name", Teacher.NAME_MAX_LENGTH)
    val shortCode = varchar("short_code", Teacher.SHORT_CODE_MAX_LENGTH)
    val weeklyLoad = integer("weekly_load")
    val version = integer("version")
    override val primaryKey = PrimaryKey(id)
}

object TeacherQualificationTable : Table("teacher_qualification") {
    val teacherId = javaUUID("teacher_id").references(TeacherTable.id)
    val subjectId = javaUUID("subject_id").references(SubjectTable.id)
    override val primaryKey = PrimaryKey(teacherId, subjectId)
}

object TeacherUnavailabilityTable : Table("teacher_unavailability") {
    val teacherId = javaUUID("teacher_id").references(TeacherTable.id)
    val dayOfWeek = varchar("day_of_week", 9)
    val slotId = javaUUID("slot_id").references(TimeSlotTable.id)
    override val primaryKey = PrimaryKey(teacherId, dayOfWeek, slotId)
}

class ExposedTeacherRepository : TeacherRepository {
    override fun insert(teacher: Teacher) {
        TeacherTable.insert {
            it[id] = teacher.id.value
            it[schoolId] = teacher.schoolId.value
            it[name] = teacher.name
            it[shortCode] = teacher.shortCode
            it[weeklyLoad] = teacher.weeklyLoad
            it[version] = teacher.version
        }
        insertDetails(teacher)
    }

    override fun update(teacher: Teacher): Boolean {
        val updatedRows =
            TeacherTable.update({ (TeacherTable.id eq teacher.id.value) and (TeacherTable.version eq teacher.version) }) {
                it[name] = teacher.name
                it[shortCode] = teacher.shortCode
                it[weeklyLoad] = teacher.weeklyLoad
                it[version] = teacher.version + 1
            }
        if (updatedRows != 1) {
            return false
        }
        deleteDetails(teacher.id.value)
        insertDetails(teacher)
        return true
    }

    override fun delete(teacherId: TeacherId) {
        deleteDetails(teacherId.value)
        TeacherTable.deleteWhere { TeacherTable.id eq teacherId.value }
    }

    override fun findById(teacherId: TeacherId): Teacher? =
        loadTeachers(TeacherTable.selectAll().where { TeacherTable.id eq teacherId.value }.toList()).singleOrNull()

    override fun findBySchool(schoolId: SchoolId): List<Teacher> =
        loadTeachers(TeacherTable.selectAll().where { TeacherTable.schoolId eq schoolId.value }.toList())

    override fun findByShortCode(shortCode: String): Teacher? =
        loadTeachers(TeacherTable.selectAll().where { TeacherTable.shortCode.upperCase() eq shortCode.trim().uppercase() }.toList())
            .singleOrNull()

    override fun findQualifiedFor(subjectId: SubjectId): List<Teacher> {
        val teacherIds =
            TeacherQualificationTable
                .select(TeacherQualificationTable.teacherId)
                .where { TeacherQualificationTable.subjectId eq subjectId.value }
                .map { it[TeacherQualificationTable.teacherId] }
        if (teacherIds.isEmpty()) {
            return emptyList()
        }
        return loadTeachers(TeacherTable.selectAll().where { TeacherTable.id inList teacherIds }.toList())
    }

    private fun deleteDetails(teacherId: UUID) {
        TeacherQualificationTable.deleteWhere { TeacherQualificationTable.teacherId eq teacherId }
        TeacherUnavailabilityTable.deleteWhere { TeacherUnavailabilityTable.teacherId eq teacherId }
    }

    private fun insertDetails(teacher: Teacher) {
        TeacherQualificationTable.batchInsert(teacher.qualifiedSubjectIds) { subjectId ->
            this[TeacherQualificationTable.teacherId] = teacher.id.value
            this[TeacherQualificationTable.subjectId] = subjectId.value
        }
        TeacherUnavailabilityTable.batchInsert(teacher.unavailability) { entry ->
            this[TeacherUnavailabilityTable.teacherId] = teacher.id.value
            this[TeacherUnavailabilityTable.dayOfWeek] = entry.dayOfWeek.name
            this[TeacherUnavailabilityTable.slotId] = entry.slotId.value
        }
    }

    /** Loads qualifications and unavailability of all [rows] with two queries instead of two per teacher. */
    private fun loadTeachers(rows: List<ResultRow>): List<Teacher> {
        if (rows.isEmpty()) {
            return emptyList()
        }
        val teacherIds = rows.map { it[TeacherTable.id] }
        val qualifications =
            TeacherQualificationTable
                .selectAll()
                .where { TeacherQualificationTable.teacherId inList teacherIds }
                .groupBy({ it[TeacherQualificationTable.teacherId] }, { SubjectId(it[TeacherQualificationTable.subjectId]) })
        val unavailability =
            TeacherUnavailabilityTable
                .selectAll()
                .where { TeacherUnavailabilityTable.teacherId inList teacherIds }
                .groupBy(
                    { it[TeacherUnavailabilityTable.teacherId] },
                    {
                        Unavailability(
                            DayOfWeek.valueOf(it[TeacherUnavailabilityTable.dayOfWeek]),
                            TimeSlotId(it[TeacherUnavailabilityTable.slotId]),
                        )
                    },
                )
        return rows.map { row ->
            val teacherId = row[TeacherTable.id]
            Teacher(
                id = TeacherId(teacherId),
                schoolId = SchoolId(row[TeacherTable.schoolId]),
                name = row[TeacherTable.name],
                shortCode = row[TeacherTable.shortCode],
                weeklyLoad = row[TeacherTable.weeklyLoad],
                qualifiedSubjectIds = qualifications[teacherId].orEmpty().toSet(),
                unavailability = unavailability[teacherId].orEmpty().toSet(),
                version = row[TeacherTable.version],
            )
        }
    }
}

/** Teacher unavailabilities of a time slot, counted for the deletion confirmation and removed with the slot. */
class ExposedTimeSlotUsage : TimeSlotUsage {
    override fun countUnavailabilities(slotId: TimeSlotId): Int =
        TeacherUnavailabilityTable
            .selectAll()
            .where { TeacherUnavailabilityTable.slotId eq slotId.value }
            .count()
            .toInt()

    override fun deleteUnavailabilities(slotId: TimeSlotId) {
        TeacherUnavailabilityTable.deleteWhere { TeacherUnavailabilityTable.slotId eq slotId.value }
    }
}
