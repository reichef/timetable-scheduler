package timetablescheduler.application.fakes

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
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject

class InMemorySubjectRepository : VersionedInMemoryStore<SubjectId, Subject>(
    keyOf = { it.id },
    versionOf = { it.version },
    withVersion = { subject, version -> subject.copy(version = version) },
), SubjectRepository {
    override fun insert(subject: Subject) = super.insert(subject)
    override fun update(subject: Subject): Boolean = super.update(subject)

    override fun delete(subjectId: SubjectId) {
        entries.remove(subjectId)
    }

    override fun findById(subjectId: SubjectId): Subject? = entries[subjectId]
    override fun findBySchool(schoolId: SchoolId): List<Subject> = entries.values.filter { it.schoolId == schoolId }
    override fun findByNameIgnoreCase(schoolId: SchoolId, name: String): Subject? =
        findBySchool(schoolId).firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    override fun findRequiringFeature(featureId: RoomFeatureId): List<Subject> = entries.values.filter { featureId in it.requiredFeatureIds }
}

class InMemoryClassLevelRepository : VersionedInMemoryStore<ClassLevelId, ClassLevel>(
    keyOf = { it.id },
    versionOf = { it.version },
    withVersion = { level, version -> level.copy(version = version) },
), ClassLevelRepository {
    override fun insert(level: ClassLevel) = super.insert(level)
    override fun update(level: ClassLevel): Boolean = super.update(level)

    override fun delete(levelId: ClassLevelId) {
        entries.remove(levelId)
    }

    override fun findById(levelId: ClassLevelId): ClassLevel? = entries[levelId]
    override fun findBySchool(schoolId: SchoolId): List<ClassLevel> = entries.values.filter { it.schoolId == schoolId }
    override fun findByIdentifierIgnoreCase(schoolId: SchoolId, identifier: String): ClassLevel? =
        findBySchool(schoolId).firstOrNull { it.identifier.equals(identifier.trim(), ignoreCase = true) }
    override fun findUsingSubject(subjectId: SubjectId): List<ClassLevel> =
        entries.values.filter { level -> level.curriculum.any { it.subjectId == subjectId } }
}

class InMemorySchoolClassRepository(private val levels: InMemoryClassLevelRepository) :
    VersionedInMemoryStore<SchoolClassId, SchoolClass>(
        keyOf = { it.id },
        versionOf = { it.version },
        withVersion = { schoolClass, version -> schoolClass.copy(version = version) },
    ),
    SchoolClassRepository {
    override fun insert(schoolClass: SchoolClass) = super.insert(schoolClass)
    override fun update(schoolClass: SchoolClass): Boolean = super.update(schoolClass)

    override fun delete(classId: SchoolClassId) {
        entries.remove(classId)
    }

    override fun findById(classId: SchoolClassId): SchoolClass? = entries[classId]
    override fun findByLevel(levelId: ClassLevelId): List<SchoolClass> = entries.values.filter { it.classLevelId == levelId }
    override fun findBySchool(schoolId: SchoolId): List<SchoolClass> =
        entries.values.filter { levels.findById(it.classLevelId)?.schoolId == schoolId }

    override fun findByHomeRoom(roomId: RoomId): SchoolClass? = entries.values.firstOrNull { it.homeRoomId == roomId }
}
