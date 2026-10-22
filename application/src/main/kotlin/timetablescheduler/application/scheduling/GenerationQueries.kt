package timetablescheduler.application.scheduling

import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.ChosenTimetableRepository
import timetablescheduler.application.port.ClassLevelRepository
import timetablescheduler.application.port.GenerationRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.application.port.SchedulingConstraintRepository
import timetablescheduler.application.port.SchoolClassRepository
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.SubjectRepository
import timetablescheduler.application.port.TeacherRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.application.readiness.ReadinessReport
import timetablescheduler.application.readiness.ReadinessReportLoader
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.evaluation.HardConstraintChecker
import timetablescheduler.domain.scheduling.evaluation.HardViolation
import timetablescheduler.domain.scheduling.generation.ChosenTimetable
import timetablescheduler.domain.scheduling.problem.SchedulingInputFingerprint
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationGate
import timetablescheduler.domain.scheduling.generation.GateResult
import timetablescheduler.domain.scheduling.generation.GenerationId
import timetablescheduler.domain.school.TimeGrid

/**
 * A generation as listed, with the username of the planner who started it and whether master data or constraints
 * changed since its start (US4 scenario 1, G8).
 */
data class GenerationSummary(val generation: Generation, val startedByUsername: String, val outdated: Boolean = false)

/** The generations of a school and what the start form needs: readiness gate and the running generation. */
data class GenerationOverview(
    val schoolId: SchoolId,
    val generations: List<GenerationSummary>,
    val gate: GateResult,
    val readiness: ReadinessReport,
    val softConstraints: List<SchedulingConstraint> = emptyList(),
    val names: TimetableNames? = null,
    val hasChosenTimetable: Boolean = false,
) {
    val running: GenerationSummary? get() = generations.firstOrNull { it.generation.isRunning }
}

/** A generation with its alternatives (FR-018) and names to describe its constraint snapshot (FR-022). */
data class GenerationDetails(val summary: GenerationSummary, val alternatives: List<Alternative>, val names: TimetableNames? = null) {
    val generation: Generation get() = summary.generation
}

/**
 * Names for showing timetables: entries are named as entered (001 FR-044); entries deleted since the generation have no
 * name and are shown as deleted. Also the current time grid, in which the weekly grids are drawn.
 */
data class TimetableNames(
    val timeGrid: TimeGrid,
    val classNames: Map<SchoolClassId, String>,
    val subjectNames: Map<SubjectId, String>,
    val teacherShortCodes: Map<TeacherId, String>,
    val teacherNames: Map<TeacherId, String>,
    val roomNames: Map<RoomId, String>,
)

/**
 * One alternative with its generation and everything needed to draw it per class, teacher or room (FR-019);
 * [number] is its position in the generation's list, as shown in the result table. [others] are the other
 * alternatives of the generation with their numbers (to compare with, FR-020), [chosen] the school's current choice
 * (its version is needed to choose this one, FR-028). [conflicts] are the hard-constraint violations of its lessons
 * under today's data — only an outdated alternative has any (FR-023); [constraints] name the planner constraints.
 */
data class AlternativeDetails(
    val generation: Generation,
    val alternative: Alternative,
    val names: TimetableNames,
    val number: Int,
    val others: List<Pair<Int, Alternative>> = emptyList(),
    val chosen: ChosenTimetable? = null,
    val conflicts: List<HardViolation> = emptyList(),
    val constraints: List<SchedulingConstraint> = emptyList(),
) {
    val isChosen: Boolean get() = chosen?.alternativeId == alternative.id
}

class ListGenerations(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val generations: GenerationRepository,
    private val accounts: UserAccountRepository,
    private val readiness: ReadinessReportLoader,
    private val constraints: SchedulingConstraintRepository,
    private val names: TimetableNamesLoader,
    private val chosenTimetables: ChosenTimetableRepository,
    private val problems: ConstraintContextLoader,
) {
    fun execute(actor: AccountId, schoolId: SchoolId): UseCaseResult<GenerationOverview> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val report = readiness.load(schoolId)
                GenerationOverview(
                    schoolId = schoolId,
                    generations = currentFingerprint(problems, schoolId).let { fingerprint ->
                        generations.findBySchool(schoolId).sortedByDescending { it.startedAt }.map { summaryOf(it, accounts, fingerprint) }
                    },
                    gate = GenerationGate.classify(report.issues),
                    readiness = report,
                    softConstraints = constraints.findBySchool(schoolId).filter { it.active && !it.isHard },
                    names = names.load(schoolId),
                    hasChosenTimetable = chosenTimetables.find(schoolId) != null,
                )
            }
        }
}

class GetGeneration(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val generations: GenerationRepository,
    private val accounts: UserAccountRepository,
    private val names: TimetableNamesLoader,
    private val problems: ConstraintContextLoader,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, generationId: GenerationId): UseCaseResult<GenerationDetails> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val generation = loadGenerationOfSchool(generations, schoolId, generationId)
                GenerationDetails(
                    summaryOf(generation, accounts, currentFingerprint(problems, schoolId)),
                    generations.findAlternatives(generationId),
                    names.load(schoolId),
                )
            }
        }
}

class GetAlternative(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val generations: GenerationRepository,
    private val names: TimetableNamesLoader,
    private val chosenTimetables: ChosenTimetableRepository,
    private val problems: ConstraintContextLoader,
) {
    fun execute(actor: AccountId, schoolId: SchoolId, generationId: GenerationId, alternativeId: AlternativeId): UseCaseResult<AlternativeDetails> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val generation = loadGenerationOfSchool(generations, schoolId, generationId)
                val alternatives = generations.findAlternatives(generationId)
                val position = alternatives.indexOfFirst { it.id == alternativeId }
                if (position < 0) fail(ApplicationError.NotFound)
                val problem = problems.load(schoolId)
                val alternative = alternatives[position]
                AlternativeDetails(
                    generation,
                    alternatives[position],
                    names.load(schoolId),
                    number = position + 1,
                    others = alternatives.mapIndexed { index, alternative -> index + 1 to alternative }.filter { it.second.id != alternativeId },
                    chosen = chosenTimetables.find(schoolId),
                    conflicts = HardConstraintChecker.violationsOf(problem, TimetableCandidate(alternative.lessons, alternative.unplaced)),
                    constraints = problem.constraints,
                )
            }
        }
}

/** Loads the current names of a school's entries for timetable pages. */
class TimetableNamesLoader(
    private val schools: SchoolRepository,
    private val rooms: RoomRepository,
    private val subjects: SubjectRepository,
    private val levels: ClassLevelRepository,
    private val classes: SchoolClassRepository,
    private val teachers: TeacherRepository,
) {
    fun load(schoolId: SchoolId): TimetableNames {
        val levelsById = levels.findBySchool(schoolId).associateBy { it.id }
        val schoolTeachers = teachers.findBySchool(schoolId)
        return TimetableNames(
            timeGrid = schools.findTimeGrid(schoolId),
            classNames = classes.findBySchool(schoolId)
                .mapNotNull { schoolClass -> levelsById[schoolClass.classLevelId]?.let { schoolClass.id to schoolClass.displayName(it) } }
                .toMap(),
            subjectNames = subjects.findBySchool(schoolId).associate { it.id to it.name },
            teacherShortCodes = schoolTeachers.associate { it.id to it.shortCode },
            teacherNames = schoolTeachers.associate { it.id to it.displayName },
            roomNames = rooms.findBySchool(schoolId).associate { it.id to it.identifier },
        )
    }
}

private fun summaryOf(generation: Generation, accounts: UserAccountRepository, currentFingerprint: () -> String) =
    GenerationSummary(generation, accounts.findById(generation.startedBy)?.username.orEmpty(), generation.inputFingerprint != currentFingerprint())

/** G8: the fingerprint of today's master data and constraints, computed once on first use. */
private fun currentFingerprint(problems: ConstraintContextLoader, schoolId: SchoolId): () -> String {
    val fingerprint by lazy { SchedulingInputFingerprint.of(problems.load(schoolId)) }
    return { fingerprint }
}
