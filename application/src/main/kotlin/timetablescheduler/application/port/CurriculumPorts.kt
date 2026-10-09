package timetablescheduler.application.port

import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject

interface SubjectRepository {
    fun insert(subject: Subject)

    /** Saves [subject] if its stored version still equals `subject.version`; returns false otherwise. */
    fun update(subject: Subject): Boolean

    fun delete(subjectId: SubjectId)
    fun findById(subjectId: SubjectId): Subject?
    fun findBySchool(schoolId: SchoolId): List<Subject>
    fun findByNameIgnoreCase(schoolId: SchoolId, name: String): Subject?
    fun findRequiringFeature(featureId: RoomFeatureId): List<Subject>
}

interface ClassLevelRepository {
    fun insert(level: ClassLevel)

    /** Saves [level] with its curriculum if its stored version still equals `level.version`; returns false otherwise. */
    fun update(level: ClassLevel): Boolean

    fun delete(levelId: ClassLevelId)
    fun findById(levelId: ClassLevelId): ClassLevel?
    fun findBySchool(schoolId: SchoolId): List<ClassLevel>
    fun findByIdentifierIgnoreCase(schoolId: SchoolId, identifier: String): ClassLevel?
    fun findUsingSubject(subjectId: SubjectId): List<ClassLevel>
}

interface SchoolClassRepository {
    fun insert(schoolClass: SchoolClass)

    /** Saves [schoolClass] if its stored version still equals `schoolClass.version`; returns false otherwise. */
    fun update(schoolClass: SchoolClass): Boolean

    fun delete(classId: SchoolClassId)
    fun findById(classId: SchoolClassId): SchoolClass?
    fun findByLevel(levelId: ClassLevelId): List<SchoolClass>
    fun findBySchool(schoolId: SchoolId): List<SchoolClass>

    /** The class using [roomId] as its home room, if any (002 FR-002a). */
    fun findByHomeRoom(roomId: RoomId): SchoolClass?
}
