package timetablescheduler.application.curriculum

import timetablescheduler.application.access.DependantSource
import timetablescheduler.application.access.DependantVisibilityFilter
import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.access.dependantsOf
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.RoomFeatureRepository
import timetablescheduler.application.port.SubjectRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.application.room.featureIdsOf
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.room.RoomFeature

data class SubjectOverview(
    val subjects: List<Subject>,
    val features: List<RoomFeature>,
)

data class SubjectDetails(
    val subject: Subject,
    val features: List<RoomFeature>,
)

/** Rejects a name another subject of the same school already uses (FR-008, case-insensitive). */
private fun OutcomeScope.requireUniqueName(
    subjects: SubjectRepository,
    subject: Subject,
) {
    val existing = subjects.findByNameIgnoreCase(subject.schoolId, subject.name)
    if (existing != null && existing.id != subject.id) {
        failDuplicate(Subject.FIELD_NAME)
    }
}

private fun RoomFeatureRepository.sortedFeaturesOf(schoolId: SchoolId): List<RoomFeature> =
    findBySchool(schoolId).sortedBy { it.name.lowercase() }

class CreateSubject(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val subjects: SubjectRepository,
    private val roomFeatures: RoomFeatureRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        name: String,
        requiredFeatureIds: Set<RoomFeatureId>,
    ): UseCaseResult<Subject> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val subject = Subject.create(schoolId, name, requiredFeatureIds, roomFeatures.featureIdsOf(schoolId)).bind()
                requireUniqueName(subjects, subject)
                subjects.insert(subject)
                subject
            }
        }
}

class UpdateSubject(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val subjects: SubjectRepository,
    private val roomFeatures: RoomFeatureRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        subjectId: SubjectId,
        name: String,
        requiredFeatureIds: Set<RoomFeatureId>,
        expectedVersion: Int,
    ): UseCaseResult<Subject> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()

                val subject = loadSubjectOfSchool(subjects, schoolId, subjectId).copy(version = expectedVersion)
                val updated = subject.update(name, requiredFeatureIds, roomFeatures.featureIdsOf(schoolId)).bind()
                requireUniqueName(subjects, updated)

                requireUpdated(subjects.update(updated))

                updated.copy(version = expectedVersion + 1)
            }
        }
}

/** Blocked while a curriculum or a teacher qualification refers to the subject (FR-023). */
class DeleteSubject(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val subjects: SubjectRepository,
    private val dependantSources: List<DependantSource<SubjectId>>,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        subjectId: SubjectId,
        expectedVersion: Int,
    ): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                val planner = access.requireSchoolAccess(actor, schoolId).bind()
                requireVersion(loadSubjectOfSchool(subjects, schoolId, subjectId).version, expectedVersion)
                val dependants = dependantSources.dependantsOf(subjectId)
                if (dependants.isNotEmpty()) {
                    fail(DependantVisibilityFilter.deletionBlocked(dependants, planner))
                }
                subjects.delete(subjectId)
            }
        }
}

class ListSubjects(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val subjects: SubjectRepository,
    private val roomFeatures: RoomFeatureRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
    ): UseCaseResult<SubjectOverview> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                SubjectOverview(subjects.findBySchool(schoolId).sortedBy { it.name.lowercase() }, roomFeatures.sortedFeaturesOf(schoolId))
            }
        }
}

class GetSubject(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val subjects: SubjectRepository,
    private val roomFeatures: RoomFeatureRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        subjectId: SubjectId,
    ): UseCaseResult<SubjectDetails> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                SubjectDetails(loadSubjectOfSchool(subjects, schoolId, subjectId), roomFeatures.sortedFeaturesOf(schoolId))
            }
        }
}
