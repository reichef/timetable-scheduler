package timetablescheduler.application.readiness

import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.ClassLevelRepository
import timetablescheduler.application.port.RoomFeatureRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.application.port.SchoolClassRepository
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.SubjectRepository
import timetablescheduler.application.port.TeacherRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.readiness.ReadinessCheck
import timetablescheduler.domain.readiness.ReadinessInput
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.room.RoomFeature
import timetablescheduler.domain.teacher.Teacher

/** The readiness issues of a school and the entries they refer to, so they can be named and linked. */
data class ReadinessReport(
    val schoolId: SchoolId,
    val issues: List<ReadinessIssue>,
    private val subjects: Map<SubjectId, Subject>,
    private val levels: Map<ClassLevelId, ClassLevel>,
    private val teachers: Map<TeacherId, Teacher>,
    private val features: Map<RoomFeatureId, RoomFeature>,
) {
    fun subjectName(subjectId: SubjectId): String = subjects[subjectId]?.name.orEmpty()
    fun levelIdentifier(levelId: ClassLevelId): String = levels[levelId]?.identifier.orEmpty()
    fun teacherName(teacherId: TeacherId): String = teachers[teacherId]?.displayName.orEmpty()
    fun featureName(featureId: RoomFeatureId): String = features[featureId]?.name.orEmpty()
}

/** FR-024: computes the readiness overview on demand; nothing is stored and nothing blocks saving (FR-025). */
class GetSchoolReadiness(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
    private val rooms: RoomRepository,
    private val roomFeatures: RoomFeatureRepository,
    private val subjects: SubjectRepository,
    private val levels: ClassLevelRepository,
    private val classes: SchoolClassRepository,
    private val teachers: TeacherRepository,
) {
    fun execute(actor: AccountId, schoolId: SchoolId): UseCaseResult<ReadinessReport> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val schoolSubjects = subjects.findBySchool(schoolId).sortedBy { it.name.lowercase() }
                val schoolLevels = levels.findBySchool(schoolId).sortedBy { it.identifier.lowercase() }
                val schoolTeachers = teachers.findBySchool(schoolId).sortedWith(compareBy({ it.name.lowercase() }, { it.shortCode }))
                val input = ReadinessInput(
                    teachingSlotsPerWeek = schools.findTimeGrid(schoolId).teachingSlotsPerWeek(),
                    rooms = rooms.findBySchool(schoolId),
                    subjects = schoolSubjects,
                    levels = schoolLevels,
                    classes = classes.findBySchool(schoolId),
                    teachers = schoolTeachers,
                )
                ReadinessReport(
                    schoolId = schoolId,
                    issues = ReadinessCheck.issuesOf(input),
                    subjects = schoolSubjects.associateBy { it.id },
                    levels = schoolLevels.associateBy { it.id },
                    teachers = schoolTeachers.associateBy { it.id },
                    features = roomFeatures.findBySchool(schoolId).associateBy { it.id },
                )
            }
        }
}
