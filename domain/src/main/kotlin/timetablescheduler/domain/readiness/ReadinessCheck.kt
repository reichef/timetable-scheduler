package timetablescheduler.domain.readiness

import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.curriculum.ClassCurriculumResolver
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.constraint.TeacherFixationRules
import timetablescheduler.domain.teacher.Teacher

/** Everything of one school the readiness check looks at. */
data class ReadinessInput(
    val teachingSlotsPerWeek: Int,
    val rooms: List<Room>,
    val subjects: List<Subject>,
    val levels: List<ClassLevel>,
    val classes: List<SchoolClass>,
    val teachers: List<Teacher>,
    val fixations: List<TeacherFixation> = emptyList(),
)

/**
 * FR-024: finds the gaps that would prevent classes from being fully scheduled. Issues are listed rule by rule in
 * the order of the data-model table, each rule in the order of the given entries.
 *
 * The capacity rule is an upper-bound estimate: a teacher qualified for several subjects counts fully for each,
 * so a reported shortfall is a definite problem while no shortfall does not guarantee a feasible timetable.
 */
object ReadinessCheck {

    fun issuesOf(input: ReadinessInput): List<ReadinessIssue> {
        val taughtSubjects = subjectsInAnyCurriculum(input)
        return subjectsWithoutSuitableRoom(taughtSubjects, input.rooms) +
            subjectsWithoutQualifiedTeacher(taughtSubjects, input.teachers) +
            capacityShortfalls(taughtSubjects, input) +
            input.levels.mapNotNull { LevelExceedsGridRule.check(it, input.teachingSlotsPerWeek) } +
            teachersWithoutQualification(input.teachers) +
            teachersWithZeroLoad(input.teachers) +
            teachersAboveGrid(input.teachers, input.teachingSlotsPerWeek) +
            invalidFixations(input)
    }

    private fun subjectsInAnyCurriculum(input: ReadinessInput): List<Subject> {
        val usedIds = input.levels.flatMap { level -> level.curriculum.map { it.subjectId } }.toSet()
        return input.subjects.filter { it.id in usedIds }
    }

    /** `SUBJECT_NO_SUITABLE_ROOM`: no single room offers all required features; also names features no room has at all. */
    private fun subjectsWithoutSuitableRoom(subjects: List<Subject>, rooms: List<Room>): List<ReadinessIssue> {
        val offeredFeatures = rooms.flatMap { it.featureIds }.toSet()
        return subjects
            .filter { subject -> rooms.none { it.offersAll(subject.requiredFeatureIds) } }
            .map { ReadinessIssue.SubjectNoSuitableRoom(it.id, it.requiredFeatureIds - offeredFeatures) }
    }

    private fun subjectsWithoutQualifiedTeacher(subjects: List<Subject>, teachers: List<Teacher>): List<ReadinessIssue> =
        subjects
            .filter { subject -> teachers.none { subject.id in it.qualifiedSubjectIds } }
            .map { ReadinessIssue.SubjectNoQualifiedTeacher(it.id) }

    /**
     * `SUBJECT_TEACHER_CAPACITY_SHORTFALL`: periods all classes need (via [ClassCurriculumResolver]) against the summed
     * load of the qualified teachers. Subjects without any qualified teacher are already reported by the rule above.
     */
    private fun capacityShortfalls(subjects: List<Subject>, input: ReadinessInput): List<ReadinessIssue> {
        val requiredPeriods = requiredPeriodsBySubject(input)
        return subjects.mapNotNull { subject ->
            val qualifiedTeachers = input.teachers.filter { subject.id in it.qualifiedSubjectIds }
            val shortfall = (requiredPeriods[subject.id] ?: 0) - qualifiedTeachers.sumOf { it.weeklyLoad }
            if (qualifiedTeachers.isNotEmpty() && shortfall > 0) ReadinessIssue.SubjectTeacherCapacityShortfall(subject.id, shortfall) else null
        }
    }

    private fun requiredPeriodsBySubject(input: ReadinessInput): Map<SubjectId, Int> {
        val levelsById = input.levels.associateBy { it.id }
        return input.classes
            .flatMap { schoolClass ->
                val level = levelsById[schoolClass.classLevelId] ?: return@flatMap emptyList()
                ClassCurriculumResolver.curriculumOf(schoolClass, level)
            }
            .groupBy({ it.subjectId }, { it.periodsPerWeek })
            .mapValues { (_, periods) -> periods.sum() }
    }

    private fun teachersWithoutQualification(teachers: List<Teacher>): List<ReadinessIssue> =
        teachers.filter { it.qualifiedSubjectIds.isEmpty() }.map { ReadinessIssue.TeacherWithoutQualification(it.id) }

    private fun teachersWithZeroLoad(teachers: List<Teacher>): List<ReadinessIssue> =
        teachers.filter { it.weeklyLoad == 0 }.map { ReadinessIssue.TeacherZeroLoad(it.id) }

    /** `INVALID_TEACHER_FIXATION`: fixations made invalid by later master-data changes (002 FR-013a). */
    private fun invalidFixations(input: ReadinessInput): List<ReadinessIssue> {
        val levelsById = input.levels.associateBy { it.id }
        val curricula = input.classes.associate { schoolClass ->
            schoolClass.id to levelsById[schoolClass.classLevelId]?.let { ClassCurriculumResolver.curriculumOf(schoolClass, it) }.orEmpty()
        }
        fun periodsOf(fixation: TeacherFixation) = curricula[fixation.classId]?.firstOrNull { it.subjectId == fixation.subjectId }?.periodsPerWeek
        val teachersById = input.teachers.associateBy { it.id }
        return input.fixations.mapNotNull { fixation ->
            val teacher = teachersById[fixation.teacherId]
            val fixedPeriods = input.fixations.filter { it.teacherId == fixation.teacherId }.sumOf { periodsOf(it) ?: 0 }
            TeacherFixationRules.invalidityOf(periodsOf(fixation), fixation.subjectId, teacher?.qualifiedSubjectIds.orEmpty(), teacher?.weeklyLoad ?: 0, fixedPeriods)
                ?.let { ReadinessIssue.InvalidTeacherFixation(fixation.id, it) }
        }
    }

    /** `TEACHER_LOAD_EXCEEDS_GRID`: e.g. after the grid was reduced below a load that was valid when saved. */
    private fun teachersAboveGrid(teachers: List<Teacher>, teachingSlotsPerWeek: Int): List<ReadinessIssue> =
        teachers.mapNotNull { teacher ->
            val excess = teacher.loadExceedingGrid(teachingSlotsPerWeek)
            if (excess > 0) ReadinessIssue.TeacherLoadExceedsGrid(teacher.id, excess) else null
        }
}
