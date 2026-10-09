package timetablescheduler.adapters.persistence.scheduling

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import timetablescheduler.application.port.GenerationRepository
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintType
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.constraint.TeacherFixationId
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationId
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.ObjectiveValues
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedGenerationRepository : GenerationRepository {

    override fun insert(generation: Generation) {
        GenerationTable.insert {
            it[id] = generation.id.value
            it[schoolId] = generation.schoolId.value
            it[startedBy] = generation.startedBy.value
            it[startedAt] = generation.startedAt.atOffset(ZoneOffset.UTC)
            it[finishedAt] = generation.finishedAt?.atOffset(ZoneOffset.UTC)
            it[timeLimitMinutes] = generation.timeLimitMinutes
            it[maxAlternatives] = generation.maxAlternatives
            it[status] = generation.status.name
            it[inputFingerprint] = generation.inputFingerprint
            it[alternativesFound] = generation.alternativesFound
        }
        GenerationSeparateObjectiveTable.batchInsert(generation.separateObjectiveIds.withIndex()) { (index, constraintId) ->
            this[GenerationSeparateObjectiveTable.generationId] = generation.id.value
            this[GenerationSeparateObjectiveTable.position] = (index + 1).toShort()
            this[GenerationSeparateObjectiveTable.originalConstraintId] = constraintId.value
        }
        insertConstraintSnapshot(generation)
        GenerationFixationTable.batchInsert(generation.fixationSnapshot.withIndex()) { (position, fixation) ->
            this[GenerationFixationTable.generationId] = generation.id.value
            this[GenerationFixationTable.position] = position
            this[GenerationFixationTable.originalFixationId] = fixation.id.value
            this[GenerationFixationTable.classId] = fixation.classId.value
            this[GenerationFixationTable.subjectId] = fixation.subjectId.value
            this[GenerationFixationTable.teacherId] = fixation.teacherId.value
            this[GenerationFixationTable.version] = fixation.version
        }
    }

    private fun insertConstraintSnapshot(generation: Generation) {
        generation.constraintSnapshot.forEachIndexed { position, constraint ->
            val columns = RuleColumns.of(constraint.rule)
            GenerationConstraintTable.insert {
                it[generationId] = generation.id.value
                it[GenerationConstraintTable.position] = position
                it[originalConstraintId] = constraint.id.value
                it[type] = columns.type.name
                it[kind] = constraint.kind.name
                it[weight] = constraint.weight?.name
                it[active] = constraint.active
                it[allClasses] = columns.allClasses
                it[classId] = columns.classId
                it[teacherId] = columns.teacherId
                it[subjectId] = columns.subjectId
                it[limitValue] = columns.limit
                it[acrossBreaks] = columns.acrossBreaks
                it[version] = constraint.version
            }
            GenerationConstraintSlotTable.batchInsert(columns.slots) { slot ->
                this[GenerationConstraintSlotTable.generationId] = generation.id.value
                this[GenerationConstraintSlotTable.position] = position
                this[GenerationConstraintSlotTable.dayOfWeek] = slot.day.name
                this[GenerationConstraintSlotTable.slotId] = slot.slotId.value
            }
        }
    }

    override fun findById(generationId: GenerationId): Generation? =
        GenerationTable.selectAll().where { GenerationTable.id eq generationId.value }.singleOrNull()?.toGeneration()

    override fun findBySchool(schoolId: SchoolId): List<Generation> =
        GenerationTable.selectAll().where { GenerationTable.schoolId eq schoolId.value }.map { it.toGeneration() }

    override fun findRunning(schoolId: SchoolId): Generation? =
        GenerationTable.selectAll()
            .where { (GenerationTable.schoolId eq schoolId.value) and (GenerationTable.status eq GenerationStatus.RUNNING.name) }
            .singleOrNull()
            ?.toGeneration()

    override fun updateProgress(generationId: GenerationId, alternativesFound: Int) {
        GenerationTable.update({ GenerationTable.id eq generationId.value }) { it[GenerationTable.alternativesFound] = alternativesFound }
    }

    override fun saveEnd(generation: Generation) {
        GenerationTable.update({ GenerationTable.id eq generation.id.value }) {
            it[status] = generation.status.name
            it[finishedAt] = generation.finishedAt?.atOffset(ZoneOffset.UTC)
            it[alternativesFound] = generation.alternativesFound
        }
        GenerationInfeasibleConstraintTable.batchInsert(generation.infeasibleConstraintIds) { constraintId ->
            this[GenerationInfeasibleConstraintTable.generationId] = generation.id.value
            this[GenerationInfeasibleConstraintTable.originalConstraintId] = constraintId.value
        }
    }

    override fun failRunning(at: Instant): Int =
        GenerationTable.update({ GenerationTable.status eq GenerationStatus.RUNNING.name }) {
            it[status] = GenerationStatus.FAILED.name
            it[finishedAt] = at.atOffset(ZoneOffset.UTC)
        }

    override fun addAlternative(alternative: Alternative) {
        val values = alternative.objectiveValues
        AlternativeTable.insert {
            it[id] = alternative.id.value
            it[generationId] = alternative.generationId.value
            it[classFill] = decimalOf(values.classFill)
            it[teacherLoadUsage] = decimalOf(values.teacherLoadUsage)
            it[separate1] = values.separate.getOrNull(0)?.let(::decimalOf)
            it[separate2] = values.separate.getOrNull(1)?.let(::decimalOf)
            it[separate3] = values.separate.getOrNull(2)?.let(::decimalOf)
            it[otherSoft] = decimalOf(values.otherSoft)
        }
        insertLessons(alternative)
        AlternativeUnplacedTable.batchInsert(alternative.unplaced.entries) { (course, periods) ->
            this[AlternativeUnplacedTable.alternativeId] = alternative.id.value
            this[AlternativeUnplacedTable.classId] = course.classId.value
            this[AlternativeUnplacedTable.subjectId] = course.subjectId.value
            this[AlternativeUnplacedTable.periods] = periods
        }
        AlternativeSoftViolationTable.batchInsert(alternative.softViolations.entries) { (constraintId, violations) ->
            this[AlternativeSoftViolationTable.alternativeId] = alternative.id.value
            this[AlternativeSoftViolationTable.constraintId] = constraintId.value
            this[AlternativeSoftViolationTable.violations] = violations
        }
    }

    override fun removeAlternative(alternativeId: AlternativeId) {
        AlternativeTable.deleteWhere { AlternativeTable.id eq alternativeId.value }
    }

    override fun findAlternatives(generationId: GenerationId): List<Alternative> =
        loadAlternatives(AlternativeTable.selectAll().where { AlternativeTable.generationId eq generationId.value }.toList())

    override fun findAlternative(alternativeId: AlternativeId): Alternative? =
        loadAlternatives(AlternativeTable.selectAll().where { AlternativeTable.id eq alternativeId.value }.toList()).singleOrNull()

    private fun insertLessons(alternative: Alternative) {
        AlternativeLessonTable.batchInsert(alternative.lessons.withIndex()) { (position, lesson) ->
            this[AlternativeLessonTable.alternativeId] = alternative.id.value
            this[AlternativeLessonTable.position] = position
            this[AlternativeLessonTable.classId] = lesson.classId.value
            this[AlternativeLessonTable.subjectId] = lesson.subjectId.value
            this[AlternativeLessonTable.teacherId] = lesson.teacherId.value
            this[AlternativeLessonTable.roomId] = lesson.roomId.value
            this[AlternativeLessonTable.dayOfWeek] = lesson.daySlot.day.name
            this[AlternativeLessonTable.timeSlotId] = lesson.daySlot.slotId.value
        }
    }

    /** Loads lessons, unplaced periods and soft violations of all [rows] with one query each. */
    private fun loadAlternatives(rows: List<ResultRow>): List<Alternative> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it[AlternativeTable.id] }
        val lessons = AlternativeLessonTable.selectAll().where { AlternativeLessonTable.alternativeId inList ids }
            .sortedBy { it[AlternativeLessonTable.position] }
            .groupBy({ it[AlternativeLessonTable.alternativeId] }, { it.toLesson() })
        val unplaced = AlternativeUnplacedTable.selectAll().where { AlternativeUnplacedTable.alternativeId inList ids }
            .groupBy({ it[AlternativeUnplacedTable.alternativeId] }, {
                CourseKey(SchoolClassId(it[AlternativeUnplacedTable.classId]), SubjectId(it[AlternativeUnplacedTable.subjectId])) to it[AlternativeUnplacedTable.periods]
            })
        val softViolations = AlternativeSoftViolationTable.selectAll().where { AlternativeSoftViolationTable.alternativeId inList ids }
            .groupBy({ it[AlternativeSoftViolationTable.alternativeId] }, {
                SchedulingConstraintId(it[AlternativeSoftViolationTable.constraintId]) to it[AlternativeSoftViolationTable.violations]
            })
        return rows.map { row ->
            val id = row[AlternativeTable.id]
            Alternative(
                id = AlternativeId(id),
                generationId = GenerationId(row[AlternativeTable.generationId]),
                objectiveValues = row.toObjectiveValues(),
                lessons = lessons[id].orEmpty(),
                unplaced = unplaced[id].orEmpty().toMap(),
                softViolations = softViolations[id].orEmpty().toMap(),
            )
        }
    }

    private fun ResultRow.toObjectiveValues() = ObjectiveValues(
        classFill = this[AlternativeTable.classFill].toDouble(),
        teacherLoadUsage = this[AlternativeTable.teacherLoadUsage].toDouble(),
        separate = listOfNotNull(this[AlternativeTable.separate1], this[AlternativeTable.separate2], this[AlternativeTable.separate3]).map { it.toDouble() },
        otherSoft = this[AlternativeTable.otherSoft].toDouble(),
        stability = null,
    )

    private fun ResultRow.toLesson() = LessonAssignment(
        classId = SchoolClassId(this[AlternativeLessonTable.classId]),
        subjectId = SubjectId(this[AlternativeLessonTable.subjectId]),
        teacherId = TeacherId(this[AlternativeLessonTable.teacherId]),
        roomId = RoomId(this[AlternativeLessonTable.roomId]),
        daySlot = DaySlot(DayOfWeek.valueOf(this[AlternativeLessonTable.dayOfWeek]), TimeSlotId(this[AlternativeLessonTable.timeSlotId])),
    )

    private fun ResultRow.toGeneration(): Generation {
        val id = this[GenerationTable.id]
        return toGenerationWithoutSnapshot().copy(
            separateObjectiveIds = GenerationSeparateObjectiveTable.selectAll().where { GenerationSeparateObjectiveTable.generationId eq id }
                .sortedBy { it[GenerationSeparateObjectiveTable.position] }
                .map { SchedulingConstraintId(it[GenerationSeparateObjectiveTable.originalConstraintId]) },
            constraintSnapshot = constraintSnapshotOf(id),
            fixationSnapshot = GenerationFixationTable.selectAll().where { GenerationFixationTable.generationId eq id }
                .sortedBy { it[GenerationFixationTable.position] }
                .map {
                    TeacherFixation(
                        TeacherFixationId(it[GenerationFixationTable.originalFixationId]), SchoolId(this[GenerationTable.schoolId]),
                        SchoolClassId(it[GenerationFixationTable.classId]), SubjectId(it[GenerationFixationTable.subjectId]),
                        TeacherId(it[GenerationFixationTable.teacherId]), it[GenerationFixationTable.version],
                    )
                },
            infeasibleConstraintIds = GenerationInfeasibleConstraintTable.selectAll().where { GenerationInfeasibleConstraintTable.generationId eq id }
                .map { SchedulingConstraintId(it[GenerationInfeasibleConstraintTable.originalConstraintId]) },
        )
    }

    private fun constraintSnapshotOf(generationId: java.util.UUID): List<SchedulingConstraint> {
        val slots = GenerationConstraintSlotTable.selectAll().where { GenerationConstraintSlotTable.generationId eq generationId }
            .groupBy({ it[GenerationConstraintSlotTable.position] }, { RuleColumns.slotOf(it[GenerationConstraintSlotTable.dayOfWeek], it[GenerationConstraintSlotTable.slotId]) })
        return GenerationConstraintTable.selectAll().where { GenerationConstraintTable.generationId eq generationId }
            .sortedBy { it[GenerationConstraintTable.position] }
            .map { row ->
                val columns = RuleColumns(
                    type = ConstraintType.valueOf(row[GenerationConstraintTable.type]),
                    allClasses = row[GenerationConstraintTable.allClasses],
                    classId = row[GenerationConstraintTable.classId],
                    teacherId = row[GenerationConstraintTable.teacherId],
                    subjectId = row[GenerationConstraintTable.subjectId],
                    limit = row[GenerationConstraintTable.limitValue],
                    acrossBreaks = row[GenerationConstraintTable.acrossBreaks],
                    slots = slots[row[GenerationConstraintTable.position]].orEmpty().toSet(),
                )
                SchedulingConstraint(
                    id = SchedulingConstraintId(row[GenerationConstraintTable.originalConstraintId]),
                    schoolId = SchoolId(this@ExposedGenerationRepository.schoolIdOf(generationId)),
                    kind = ConstraintKind.valueOf(row[GenerationConstraintTable.kind]),
                    weight = row[GenerationConstraintTable.weight]?.let(SoftWeight::valueOf),
                    active = row[GenerationConstraintTable.active],
                    rule = columns.toRule(),
                    version = row[GenerationConstraintTable.version],
                )
            }
    }

    private fun schoolIdOf(generationId: java.util.UUID): java.util.UUID =
        GenerationTable.select(GenerationTable.schoolId).where { GenerationTable.id eq generationId }.single()[GenerationTable.schoolId]

    private fun ResultRow.toGenerationWithoutSnapshot() = Generation(
        id = GenerationId(this[GenerationTable.id]),
        schoolId = SchoolId(this[GenerationTable.schoolId]),
        startedBy = AccountId(this[GenerationTable.startedBy]),
        startedAt = this[GenerationTable.startedAt].toInstant(),
        finishedAt = this[GenerationTable.finishedAt]?.let(OffsetDateTime::toInstant),
        timeLimitMinutes = this[GenerationTable.timeLimitMinutes],
        maxAlternatives = this[GenerationTable.maxAlternatives],
        status = GenerationStatus.valueOf(this[GenerationTable.status]),
        inputFingerprint = this[GenerationTable.inputFingerprint],
        alternativesFound = this[GenerationTable.alternativesFound],
    )

    private fun decimalOf(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP)
}
