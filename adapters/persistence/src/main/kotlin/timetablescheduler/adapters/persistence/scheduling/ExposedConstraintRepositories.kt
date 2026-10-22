package timetablescheduler.adapters.persistence.scheduling

import org.jetbrains.exposed.v1.core.Op
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
import timetablescheduler.application.port.SchedulingConstraintRepository
import timetablescheduler.application.port.TeacherFixationRepository
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintType
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.constraint.TeacherFixationId

class ExposedSchedulingConstraintRepository : SchedulingConstraintRepository {
    override fun insert(constraint: SchedulingConstraint) {
        val columns = RuleColumns.of(constraint.rule)
        SchedulingConstraintTable.insert {
            it[id] = constraint.id.value
            it[schoolId] = constraint.schoolId.value
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
        insertSlots(constraint.id, columns)
    }

    override fun update(constraint: SchedulingConstraint): Boolean {
        val columns = RuleColumns.of(constraint.rule)
        val updatedRows =
            SchedulingConstraintTable.update({
                (SchedulingConstraintTable.id eq constraint.id.value) and (SchedulingConstraintTable.version eq constraint.version)
            }) {
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
                it[version] = constraint.version + 1
            }
        if (updatedRows != 1) {
            return false
        }
        SchedulingConstraintSlotTable.deleteWhere { SchedulingConstraintSlotTable.constraintId eq constraint.id.value }
        insertSlots(constraint.id, columns)
        return true
    }

    override fun delete(constraintId: SchedulingConstraintId) {
        SchedulingConstraintTable.deleteWhere { SchedulingConstraintTable.id eq constraintId.value }
    }

    override fun findById(constraintId: SchedulingConstraintId): SchedulingConstraint? =
        load(SchedulingConstraintTable.id eq constraintId.value).singleOrNull()

    override fun findBySchool(schoolId: SchoolId): List<SchedulingConstraint> = load(SchedulingConstraintTable.schoolId eq schoolId.value)

    override fun findReferencingClass(classId: SchoolClassId): List<SchedulingConstraint> =
        load(SchedulingConstraintTable.classId eq classId.value)

    override fun findReferencingTeacher(teacherId: TeacherId): List<SchedulingConstraint> =
        load(SchedulingConstraintTable.teacherId eq teacherId.value)

    override fun findReferencingSubject(subjectId: SubjectId): List<SchedulingConstraint> =
        load(SchedulingConstraintTable.subjectId eq subjectId.value)

    override fun findReferencingSlot(slotId: TimeSlotId): List<SchedulingConstraint> {
        val ids =
            SchedulingConstraintSlotTable
                .select(SchedulingConstraintSlotTable.constraintId)
                .where { SchedulingConstraintSlotTable.slotId eq slotId.value }
                .map { it[SchedulingConstraintSlotTable.constraintId] }
                .distinct()
        return if (ids.isEmpty()) emptyList() else load(SchedulingConstraintTable.id inList ids)
    }

    private fun insertSlots(
        constraintId: SchedulingConstraintId,
        columns: RuleColumns,
    ) {
        SchedulingConstraintSlotTable.batchInsert(columns.slots) { slot ->
            this[SchedulingConstraintSlotTable.constraintId] = constraintId.value
            this[SchedulingConstraintSlotTable.dayOfWeek] = slot.day.name
            this[SchedulingConstraintSlotTable.slotId] = slot.slotId.value
        }
    }

    /** Loads the constraints matching [condition] with their slots in one further query. */
    private fun load(condition: Op<Boolean>): List<SchedulingConstraint> {
        val rows = SchedulingConstraintTable.selectAll().where { condition }.toList()
        if (rows.isEmpty()) {
            return emptyList()
        }
        val slotsByConstraint =
            SchedulingConstraintSlotTable
                .selectAll()
                .where { SchedulingConstraintSlotTable.constraintId inList rows.map { it[SchedulingConstraintTable.id] } }
                .groupBy(
                    { it[SchedulingConstraintSlotTable.constraintId] },
                    { RuleColumns.slotOf(it[SchedulingConstraintSlotTable.dayOfWeek], it[SchedulingConstraintSlotTable.slotId]) },
                )
        return rows.map { it.toConstraint(slotsByConstraint[it[SchedulingConstraintTable.id]].orEmpty().toSet()) }
    }

    private fun ResultRow.toConstraint(slots: Set<timetablescheduler.domain.scheduling.DaySlot>): SchedulingConstraint {
        val columns =
            RuleColumns(
                type = ConstraintType.valueOf(this[SchedulingConstraintTable.type]),
                allClasses = this[SchedulingConstraintTable.allClasses],
                classId = this[SchedulingConstraintTable.classId],
                teacherId = this[SchedulingConstraintTable.teacherId],
                subjectId = this[SchedulingConstraintTable.subjectId],
                limit = this[SchedulingConstraintTable.limitValue],
                acrossBreaks = this[SchedulingConstraintTable.acrossBreaks],
                slots = slots,
            )
        return SchedulingConstraint(
            id = SchedulingConstraintId(this[SchedulingConstraintTable.id]),
            schoolId = SchoolId(this[SchedulingConstraintTable.schoolId]),
            kind = ConstraintKind.valueOf(this[SchedulingConstraintTable.kind]),
            weight = this[SchedulingConstraintTable.weight]?.let(SoftWeight::valueOf),
            active = this[SchedulingConstraintTable.active],
            rule = columns.toRule(),
            version = this[SchedulingConstraintTable.version],
        )
    }
}

class ExposedTeacherFixationRepository : TeacherFixationRepository {
    override fun insert(fixation: TeacherFixation) {
        TeacherFixationTable.insert {
            it[id] = fixation.id.value
            it[schoolId] = fixation.schoolId.value
            it[classId] = fixation.classId.value
            it[subjectId] = fixation.subjectId.value
            it[teacherId] = fixation.teacherId.value
            it[version] = fixation.version
        }
    }

    override fun update(fixation: TeacherFixation): Boolean =
        TeacherFixationTable.update(
            { (TeacherFixationTable.id eq fixation.id.value) and (TeacherFixationTable.version eq fixation.version) },
        ) {
            it[teacherId] = fixation.teacherId.value
            it[version] = fixation.version + 1
        } == 1

    override fun delete(fixationId: TeacherFixationId) {
        TeacherFixationTable.deleteWhere { TeacherFixationTable.id eq fixationId.value }
    }

    override fun findById(fixationId: TeacherFixationId): TeacherFixation? =
        load(TeacherFixationTable.id eq fixationId.value).singleOrNull()

    override fun findBySchool(schoolId: SchoolId): List<TeacherFixation> = load(TeacherFixationTable.schoolId eq schoolId.value)

    override fun findByCourse(
        classId: SchoolClassId,
        subjectId: SubjectId,
    ): TeacherFixation? =
        load((TeacherFixationTable.classId eq classId.value) and (TeacherFixationTable.subjectId eq subjectId.value)).singleOrNull()

    override fun findByClass(classId: SchoolClassId): List<TeacherFixation> = load(TeacherFixationTable.classId eq classId.value)

    override fun findByTeacher(teacherId: TeacherId): List<TeacherFixation> = load(TeacherFixationTable.teacherId eq teacherId.value)

    override fun findBySubject(subjectId: SubjectId): List<TeacherFixation> = load(TeacherFixationTable.subjectId eq subjectId.value)

    private fun load(condition: Op<Boolean>): List<TeacherFixation> =
        TeacherFixationTable.selectAll().where { condition }.map {
            TeacherFixation(
                id = TeacherFixationId(it[TeacherFixationTable.id]),
                schoolId = SchoolId(it[TeacherFixationTable.schoolId]),
                classId = SchoolClassId(it[TeacherFixationTable.classId]),
                subjectId = SubjectId(it[TeacherFixationTable.subjectId]),
                teacherId = TeacherId(it[TeacherFixationTable.teacherId]),
                version = it[TeacherFixationTable.version],
            )
        }
}
