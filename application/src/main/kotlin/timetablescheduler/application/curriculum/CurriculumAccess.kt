package timetablescheduler.application.curriculum

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.port.ClassLevelRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.application.port.SchoolClassRepository
import timetablescheduler.application.port.SubjectRepository
import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.validation.ValidationError

// Entries are only reachable through their own school; entries of other schools answer "not found".

internal fun OutcomeScope.loadSubjectOfSchool(subjects: SubjectRepository, schoolId: SchoolId, subjectId: SubjectId): Subject {
    val subject = subjects.findById(subjectId)
    if (subject == null || subject.schoolId != schoolId) fail(ApplicationError.NotFound)
    return subject
}

internal fun OutcomeScope.loadLevelOfSchool(levels: ClassLevelRepository, schoolId: SchoolId, levelId: ClassLevelId): ClassLevel {
    val level = levels.findById(levelId)
    if (level == null || level.schoolId != schoolId) fail(ApplicationError.NotFound)
    return level
}

/** A class belongs to a school through its level. */
internal fun OutcomeScope.loadClassOfSchool(
    classes: SchoolClassRepository,
    levels: ClassLevelRepository,
    schoolId: SchoolId,
    classId: SchoolClassId,
): Pair<SchoolClass, ClassLevel> {
    val schoolClass = classes.findById(classId).orNotFound()
    return schoolClass to loadLevelOfSchool(levels, schoolId, schoolClass.classLevelId)
}

/** The school's rooms in the order planners know them, for choosing a home room. */
internal fun roomsByIdentifier(rooms: RoomRepository, schoolId: SchoolId): List<Room> =
    rooms.findBySchool(schoolId).sortedBy { it.identifier.lowercase() }

internal fun OutcomeScope.requireVersion(storedVersion: Int, expectedVersion: Int) {
    if (storedVersion != expectedVersion) fail(ApplicationError.VersionConflict)
}

internal fun OutcomeScope.failDuplicate(field: String): Nothing =
    fail(ApplicationError.Invalid(listOf(ValidationError.DuplicateValue(field))))
