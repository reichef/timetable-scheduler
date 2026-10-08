package timetablescheduler.adapters.persistence.school

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.school.School
import timetablescheduler.domain.school.TimeGrid

object SchoolTable : Table("school") {
    val id = javaUUID("id")
    val name = varchar("name", School.NAME_MAX_LENGTH)
    val version = integer("version")
    override val primaryKey = PrimaryKey(id)
}

class ExposedSchoolRepository : SchoolRepository {

    override fun insert(school: School) {
        SchoolTable.insert {
            it[id] = school.id.value
            it[name] = school.name
            it[version] = school.version
        }
    }

    override fun update(school: School): Boolean =
        SchoolTable.update({ (SchoolTable.id eq school.id.value) and (SchoolTable.version eq school.version) }) {
            it[name] = school.name
            it[version] = school.version + 1
        } == 1

    override fun findById(schoolId: SchoolId): School? =
        SchoolTable.selectAll().where { SchoolTable.id eq schoolId.value }.singleOrNull()?.toSchool()

    override fun findByIds(schoolIds: Set<SchoolId>): List<School> =
        if (schoolIds.isEmpty()) emptyList()
        else SchoolTable.selectAll().where { SchoolTable.id inList schoolIds.map { it.value } }
            .map { it.toSchool() }
            .sortedBy { it.name.lowercase() }

    override fun findByNameIgnoreCase(name: String): School? =
        SchoolTable.selectAll().where { SchoolTable.name.lowerCase() eq name.trim().lowercase() }
            .singleOrNull()?.toSchool()

    override fun delete(schoolId: SchoolId) {
        TimeGridStore.deleteAll(schoolId)
        SchoolTable.deleteWhere { SchoolTable.id eq schoolId.value }
    }

    override fun findTimeGrid(schoolId: SchoolId): TimeGrid = TimeGridStore.load(schoolId)

    override fun saveTimeGrid(schoolId: SchoolId, timeGrid: TimeGrid) = TimeGridStore.save(schoolId, timeGrid)

    private fun ResultRow.toSchool() =
        School(id = SchoolId(this[SchoolTable.id]), name = this[SchoolTable.name], version = this[SchoolTable.version])
}
