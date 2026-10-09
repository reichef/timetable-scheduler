package timetablescheduler.adapters.persistence.curriculum

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import timetablescheduler.application.port.ClassLevelRepository
import timetablescheduler.application.port.SchoolClassRepository
import timetablescheduler.application.port.SubjectRepository
import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject
import java.util.UUID

class ExposedSubjectRepository : SubjectRepository {

    override fun insert(subject: Subject) {
        SubjectTable.insert {
            it[id] = subject.id.value
            it[schoolId] = subject.schoolId.value
            it[name] = subject.name
            it[version] = subject.version
        }
        insertRequiredFeatures(subject)
    }

    override fun update(subject: Subject): Boolean {
        val updatedRows = SubjectTable.update({ (SubjectTable.id eq subject.id.value) and (SubjectTable.version eq subject.version) }) {
            it[name] = subject.name
            it[version] = subject.version + 1
        }
        if (updatedRows != 1) return false
        SubjectRequiresFeatureTable.deleteWhere { SubjectRequiresFeatureTable.subjectId eq subject.id.value }
        insertRequiredFeatures(subject)
        return true
    }

    override fun delete(subjectId: SubjectId) {
        SubjectRequiresFeatureTable.deleteWhere { SubjectRequiresFeatureTable.subjectId eq subjectId.value }
        SubjectTable.deleteWhere { SubjectTable.id eq subjectId.value }
    }

    override fun findById(subjectId: SubjectId): Subject? =
        loadSubjects(SubjectTable.selectAll().where { SubjectTable.id eq subjectId.value }.toList()).singleOrNull()

    override fun findBySchool(schoolId: SchoolId): List<Subject> =
        loadSubjects(SubjectTable.selectAll().where { SubjectTable.schoolId eq schoolId.value }.toList())

    override fun findByNameIgnoreCase(schoolId: SchoolId, name: String): Subject? =
        loadSubjects(
            SubjectTable.selectAll()
                .where { (SubjectTable.schoolId eq schoolId.value) and (SubjectTable.name.lowerCase() eq name.trim().lowercase()) }
                .toList(),
        ).singleOrNull()

    override fun findRequiringFeature(featureId: RoomFeatureId): List<Subject> {
        val subjectIds = SubjectRequiresFeatureTable.select(SubjectRequiresFeatureTable.subjectId)
            .where { SubjectRequiresFeatureTable.featureId eq featureId.value }
            .map { it[SubjectRequiresFeatureTable.subjectId] }
        if (subjectIds.isEmpty()) return emptyList()
        return loadSubjects(SubjectTable.selectAll().where { SubjectTable.id inList subjectIds }.toList())
    }

    private fun insertRequiredFeatures(subject: Subject) {
        SubjectRequiresFeatureTable.batchInsert(subject.requiredFeatureIds) { featureId ->
            this[SubjectRequiresFeatureTable.subjectId] = subject.id.value
            this[SubjectRequiresFeatureTable.featureId] = featureId.value
        }
    }

    /** Loads the required features of all [rows] with one query instead of one per subject. */
    private fun loadSubjects(rows: List<ResultRow>): List<Subject> {
        if (rows.isEmpty()) return emptyList()
        val featuresBySubject: Map<UUID, Set<RoomFeatureId>> = SubjectRequiresFeatureTable.selectAll()
            .where { SubjectRequiresFeatureTable.subjectId inList rows.map { it[SubjectTable.id] } }
            .groupBy({ it[SubjectRequiresFeatureTable.subjectId] }, { RoomFeatureId(it[SubjectRequiresFeatureTable.featureId]) })
            .mapValues { it.value.toSet() }
        return rows.map { row ->
            Subject(
                id = SubjectId(row[SubjectTable.id]),
                schoolId = SchoolId(row[SubjectTable.schoolId]),
                name = row[SubjectTable.name],
                requiredFeatureIds = featuresBySubject[row[SubjectTable.id]].orEmpty(),
                version = row[SubjectTable.version],
            )
        }
    }
}

class ExposedClassLevelRepository : ClassLevelRepository {

    override fun insert(level: ClassLevel) {
        ClassLevelTable.insert {
            it[id] = level.id.value
            it[schoolId] = level.schoolId.value
            it[identifier] = level.identifier
            it[version] = level.version
        }
        insertCurriculum(level)
    }

    override fun update(level: ClassLevel): Boolean {
        val updatedRows = ClassLevelTable.update({ (ClassLevelTable.id eq level.id.value) and (ClassLevelTable.version eq level.version) }) {
            it[identifier] = level.identifier
            it[version] = level.version + 1
        }
        if (updatedRows != 1) return false
        CurriculumEntryTable.deleteWhere { CurriculumEntryTable.classLevelId eq level.id.value }
        insertCurriculum(level)
        return true
    }

    override fun delete(levelId: ClassLevelId) {
        CurriculumEntryTable.deleteWhere { CurriculumEntryTable.classLevelId eq levelId.value }
        ClassLevelTable.deleteWhere { ClassLevelTable.id eq levelId.value }
    }

    override fun findById(levelId: ClassLevelId): ClassLevel? =
        loadLevels(ClassLevelTable.selectAll().where { ClassLevelTable.id eq levelId.value }.toList()).singleOrNull()

    override fun findBySchool(schoolId: SchoolId): List<ClassLevel> =
        loadLevels(ClassLevelTable.selectAll().where { ClassLevelTable.schoolId eq schoolId.value }.toList())

    override fun findByIdentifierIgnoreCase(schoolId: SchoolId, identifier: String): ClassLevel? =
        loadLevels(
            ClassLevelTable.selectAll()
                .where { (ClassLevelTable.schoolId eq schoolId.value) and (ClassLevelTable.identifier.lowerCase() eq identifier.trim().lowercase()) }
                .toList(),
        ).singleOrNull()

    override fun findUsingSubject(subjectId: SubjectId): List<ClassLevel> {
        val levelIds = CurriculumEntryTable.select(CurriculumEntryTable.classLevelId)
            .where { CurriculumEntryTable.subjectId eq subjectId.value }
            .map { it[CurriculumEntryTable.classLevelId] }
        if (levelIds.isEmpty()) return emptyList()
        return loadLevels(ClassLevelTable.selectAll().where { ClassLevelTable.id inList levelIds }.toList())
    }

    private fun insertCurriculum(level: ClassLevel) {
        CurriculumEntryTable.batchInsert(level.curriculum.withIndex()) { (position, entry) ->
            this[CurriculumEntryTable.classLevelId] = level.id.value
            this[CurriculumEntryTable.subjectId] = entry.subjectId.value
            this[CurriculumEntryTable.periodsPerWeek] = entry.periodsPerWeek
            this[CurriculumEntryTable.position] = position
        }
    }

    /** Loads the curricula of all [rows] with one query, keeping the entered order. */
    private fun loadLevels(rows: List<ResultRow>): List<ClassLevel> {
        if (rows.isEmpty()) return emptyList()
        val curriculumByLevel: Map<UUID, List<CurriculumEntry>> = CurriculumEntryTable.selectAll()
            .where { CurriculumEntryTable.classLevelId inList rows.map { it[ClassLevelTable.id] } }
            .sortedBy { it[CurriculumEntryTable.position] }
            .groupBy({ it[CurriculumEntryTable.classLevelId] }, { CurriculumEntry(SubjectId(it[CurriculumEntryTable.subjectId]), it[CurriculumEntryTable.periodsPerWeek]) })
        return rows.map { row ->
            ClassLevel(
                id = ClassLevelId(row[ClassLevelTable.id]),
                schoolId = SchoolId(row[ClassLevelTable.schoolId]),
                identifier = row[ClassLevelTable.identifier],
                curriculum = curriculumByLevel[row[ClassLevelTable.id]].orEmpty(),
                version = row[ClassLevelTable.version],
            )
        }
    }
}

class ExposedSchoolClassRepository : SchoolClassRepository {

    override fun insert(schoolClass: SchoolClass) {
        SchoolClassTable.insert {
            it[id] = schoolClass.id.value
            it[classLevelId] = schoolClass.classLevelId.value
            it[suffix] = schoolClass.suffix
            it[priorityWeight] = schoolClass.priorityWeight
            it[version] = schoolClass.version
            it[homeRoomId] = schoolClass.homeRoomId?.value
        }
    }

    override fun update(schoolClass: SchoolClass): Boolean =
        SchoolClassTable.update({ (SchoolClassTable.id eq schoolClass.id.value) and (SchoolClassTable.version eq schoolClass.version) }) {
            it[suffix] = schoolClass.suffix
            it[priorityWeight] = schoolClass.priorityWeight
            it[homeRoomId] = schoolClass.homeRoomId?.value
            it[version] = schoolClass.version + 1
        } == 1

    override fun delete(classId: SchoolClassId) {
        SchoolClassTable.deleteWhere { SchoolClassTable.id eq classId.value }
    }

    override fun findById(classId: SchoolClassId): SchoolClass? =
        SchoolClassTable.selectAll().where { SchoolClassTable.id eq classId.value }.singleOrNull()?.toSchoolClass()

    override fun findByLevel(levelId: ClassLevelId): List<SchoolClass> =
        SchoolClassTable.selectAll().where { SchoolClassTable.classLevelId eq levelId.value }.map { it.toSchoolClass() }

    override fun findBySchool(schoolId: SchoolId): List<SchoolClass> =
        (SchoolClassTable innerJoin ClassLevelTable)
            .selectAll()
            .where { ClassLevelTable.schoolId eq schoolId.value }
            .map { it.toSchoolClass() }

    override fun findByHomeRoom(roomId: RoomId): SchoolClass? =
        SchoolClassTable.selectAll().where { SchoolClassTable.homeRoomId eq roomId.value }.singleOrNull()?.toSchoolClass()

    private fun ResultRow.toSchoolClass() =
        SchoolClass(
            id = SchoolClassId(this[SchoolClassTable.id]),
            classLevelId = ClassLevelId(this[SchoolClassTable.classLevelId]),
            suffix = this[SchoolClassTable.suffix],
            priorityWeight = this[SchoolClassTable.priorityWeight],
            version = this[SchoolClassTable.version],
            homeRoomId = this[SchoolClassTable.homeRoomId]?.let(::RoomId),
        )
}
