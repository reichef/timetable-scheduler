package timetablescheduler.adapters.persistence.scheduling

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import timetablescheduler.adapters.persistence.account.UserAccountTable
import timetablescheduler.adapters.persistence.school.SchoolTable

object GenerationTable : Table("generation") {
    val id = javaUUID("id")
    val schoolId = javaUUID("school_id").references(SchoolTable.id)
    val startedBy = javaUUID("started_by").references(UserAccountTable.id)
    val startedAt = timestampWithTimeZone("started_at")
    val finishedAt = timestampWithTimeZone("finished_at").nullable()
    val timeLimitMinutes = integer("time_limit_minutes")
    val maxAlternatives = integer("max_alternatives")
    val status = varchar("status", 17)
    val inputFingerprint = char("input_fingerprint", 64)
    val alternativesFound = integer("alternatives_found")
    val referenceAlternativeId = javaUUID("reference_alternative_id").nullable()
    val minimumPlacedShare = short("minimum_placed_share")
    override val primaryKey = PrimaryKey(id)
}

object AlternativeTable : Table("alternative") {
    val id = javaUUID("id")
    val generationId = javaUUID("generation_id").references(GenerationTable.id)
    val classFill = decimal("class_fill", 5, 4)
    val teacherLoadUsage = decimal("teacher_load_usage", 5, 4)
    val separate1 = decimal("separate_1", 5, 4).nullable()
    val separate2 = decimal("separate_2", 5, 4).nullable()
    val separate3 = decimal("separate_3", 5, 4).nullable()
    val otherSoft = decimal("other_soft", 5, 4)
    val stability = decimal("stability", 5, 4).nullable()
    val changedLessons = integer("changed_lessons").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** Lessons keep plain ids without references to master data (G7). */
object AlternativeLessonTable : Table("alternative_lesson") {
    val alternativeId = javaUUID("alternative_id").references(AlternativeTable.id)
    val position = integer("position")
    val classId = javaUUID("class_id")
    val subjectId = javaUUID("subject_id")
    val teacherId = javaUUID("teacher_id")
    val roomId = javaUUID("room_id")
    val dayOfWeek = varchar("day_of_week", 9)
    val timeSlotId = javaUUID("time_slot_id")
    override val primaryKey = PrimaryKey(alternativeId, position)
}

object AlternativeUnplacedTable : Table("alternative_unplaced") {
    val alternativeId = javaUUID("alternative_id").references(AlternativeTable.id)
    val classId = javaUUID("class_id")
    val subjectId = javaUUID("subject_id")
    val periods = integer("periods")
    override val primaryKey = PrimaryKey(alternativeId, classId, subjectId)
}

object AlternativeSoftViolationTable : Table("alternative_soft_violation") {
    val alternativeId = javaUUID("alternative_id").references(AlternativeTable.id)
    val constraintId = javaUUID("constraint_id")
    val violations = integer("violations")
    override val primaryKey = PrimaryKey(alternativeId, constraintId)
}

object GenerationSeparateObjectiveTable : Table("generation_separate_objective") {
    val generationId = javaUUID("generation_id").references(GenerationTable.id)
    val position = short("position")
    val originalConstraintId = javaUUID("original_constraint_id")
    override val primaryKey = PrimaryKey(generationId, position)
}

object GenerationInfeasibleConstraintTable : Table("generation_infeasible_constraint") {
    val generationId = javaUUID("generation_id").references(GenerationTable.id)
    val originalConstraintId = javaUUID("original_constraint_id")
    override val primaryKey = PrimaryKey(generationId, originalConstraintId)
}

/** Snapshot of the constraints in effect at the start (FR-022); plain ids, no references (G7). */
object GenerationConstraintTable : Table("generation_constraint") {
    val generationId = javaUUID("generation_id").references(GenerationTable.id)
    val position = integer("position")
    val originalConstraintId = javaUUID("original_constraint_id")
    val type = varchar("type", 40)
    val kind = varchar("kind", 4)
    val weight = varchar("weight", 6).nullable()
    val active = bool("active")
    val allClasses = bool("all_classes")
    val classId = javaUUID("class_id").nullable()
    val teacherId = javaUUID("teacher_id").nullable()
    val subjectId = javaUUID("subject_id").nullable()
    val limitValue = integer("limit_value").nullable()
    val acrossBreaks = bool("across_breaks").nullable()
    val version = integer("version")
    override val primaryKey = PrimaryKey(generationId, position)
}

object GenerationConstraintSlotTable : Table("generation_constraint_slot") {
    val generationId = javaUUID("generation_id")
    val position = integer("position")
    val dayOfWeek = varchar("day_of_week", 9)
    val slotId = javaUUID("slot_id")
    override val primaryKey = PrimaryKey(generationId, position, dayOfWeek, slotId)
}

object GenerationFixationTable : Table("generation_fixation") {
    val generationId = javaUUID("generation_id").references(GenerationTable.id)
    val position = integer("position")
    val originalFixationId = javaUUID("original_fixation_id")
    val classId = javaUUID("class_id")
    val subjectId = javaUUID("subject_id")
    val teacherId = javaUUID("teacher_id")
    val version = integer("version")
    override val primaryKey = PrimaryKey(generationId, position)
}
