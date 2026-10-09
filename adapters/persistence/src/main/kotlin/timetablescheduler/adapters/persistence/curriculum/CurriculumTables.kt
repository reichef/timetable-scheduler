package timetablescheduler.adapters.persistence.curriculum

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import timetablescheduler.adapters.persistence.room.RoomFeatureTable
import timetablescheduler.adapters.persistence.room.RoomTable
import timetablescheduler.adapters.persistence.school.SchoolTable
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject

object SubjectTable : Table("subject") {
    val id = javaUUID("id")
    val schoolId = javaUUID("school_id").references(SchoolTable.id)
    val name = varchar("name", Subject.NAME_MAX_LENGTH)
    val version = integer("version")
    override val primaryKey = PrimaryKey(id)
}

object SubjectRequiresFeatureTable : Table("subject_requires_feature") {
    val subjectId = javaUUID("subject_id").references(SubjectTable.id)
    val featureId = javaUUID("feature_id").references(RoomFeatureTable.id)
    override val primaryKey = PrimaryKey(subjectId, featureId)
}

object ClassLevelTable : Table("class_level") {
    val id = javaUUID("id")
    val schoolId = javaUUID("school_id").references(SchoolTable.id)
    val identifier = varchar("identifier", ClassLevel.IDENTIFIER_MAX_LENGTH)
    val version = integer("version")
    override val primaryKey = PrimaryKey(id)
}

object CurriculumEntryTable : Table("curriculum_entry") {
    val classLevelId = javaUUID("class_level_id").references(ClassLevelTable.id)
    val subjectId = javaUUID("subject_id").references(SubjectTable.id)
    val periodsPerWeek = integer("periods_per_week")
    val position = integer("position")
    override val primaryKey = PrimaryKey(classLevelId, subjectId)
}

object SchoolClassTable : Table("school_class") {
    val id = javaUUID("id")
    val classLevelId = javaUUID("class_level_id").references(ClassLevelTable.id)
    val suffix = varchar("suffix", SchoolClass.SUFFIX_MAX_LENGTH)
    val priorityWeight = integer("priority_weight")
    val version = integer("version")
    val homeRoomId = javaUUID("home_room_id").references(RoomTable.id).nullable()
    override val primaryKey = PrimaryKey(id)
}
