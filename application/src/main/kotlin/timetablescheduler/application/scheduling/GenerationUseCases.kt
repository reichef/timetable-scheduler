package timetablescheduler.application.scheduling

import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.Dependant
import timetablescheduler.application.error.DependantType
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.ChosenTimetableRepository
import timetablescheduler.application.port.GenerationExecutor
import timetablescheduler.application.port.GenerationRepository
import timetablescheduler.application.port.StopSignal
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.application.readiness.ReadinessReportLoader
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.generation.ChosenTimetable
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationGate
import timetablescheduler.domain.scheduling.generation.GenerationId
import timetablescheduler.domain.scheduling.generation.InfeasibilityPreCheck
import timetablescheduler.domain.scheduling.problem.SchedulingInputFingerprint
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.validation.ValidationError
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** What the planner chose on the start form (FR-007a, FR-009, FR-010). */
data class StartRequest(
    val timeLimitMinutes: Int = Generation.DEFAULT_TIME_LIMIT_MINUTES,
    val warningsConfirmed: Boolean = false,
    val separateObjectiveIds: List<SchedulingConstraintId> = emptyList(),
    val startFromChosen: Boolean = false,
    val minimumPlacedShare: Int = Generation.DEFAULT_MINIMUM_PLACED_SHARE,
) {
    companion object {
        const val FIELD_WARNINGS_CONFIRMED = "warningsConfirmed"
        const val FIELD_SEPARATE_OBJECTIVES = "separateObjective"
        const val MAX_SEPARATE_OBJECTIVES = 3
        const val FIELD_START_FROM_CHOSEN = "startFromChosen"
        const val FIELD_MINIMUM_PLACED_SHARE = Generation.FIELD_MINIMUM_PLACED_SHARE
    }
}

/**
 * Stop requests of running generations, shared between the request that asks to stop and the background thread
 * that runs the generation. A request may arrive before the generation is picked up; it is then seen at its start.
 */
class GenerationControl {
    private val stopRequests = ConcurrentHashMap<GenerationId, AtomicBoolean>()

    fun stopSignalOf(generationId: GenerationId): StopSignal {
        val flag = stopRequests.computeIfAbsent(generationId) { AtomicBoolean(false) }
        return StopSignal { flag.get() }
    }

    fun requestStop(generationId: GenerationId) {
        stopRequests.computeIfAbsent(generationId) { AtomicBoolean(false) }.set(true)
    }

    fun release(generationId: GenerationId) {
        stopRequests.remove(generationId)
    }
}

/** A generation of the school, or not found — also for generations of other schools (001 R17). */
internal fun OutcomeScope.loadGenerationOfSchool(
    generations: GenerationRepository,
    schoolId: SchoolId,
    generationId: GenerationId,
): Generation {
    val generation = generations.findById(generationId)
    if (generation == null || generation.schoolId != schoolId) {
        fail(ApplicationError.NotFound)
    }
    return generation
}

/**
 * FR-001, FR-009 – FR-011: checks access, that no generation of the school runs, the readiness gate (G11) and the
 * time limit, stores the generation with the fingerprint of its input and hands it to the background executor
 * after the transaction committed, so the background thread always finds it.
 */
class StartGeneration(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val readiness: ReadinessReportLoader,
    private val problems: ConstraintContextLoader,
    private val names: TimetableNamesLoader,
    private val generations: GenerationRepository,
    private val chosenTimetables: ChosenTimetableRepository,
    private val executor: GenerationExecutor,
    private val runner: GenerationRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        request: StartRequest,
    ): UseCaseResult<Generation> {
        var problem: SchedulingProblem? = null
        val result =
            transactions.inTransaction {
                outcome {
                    access.requireSchoolAccess(actor, schoolId).bind()
                    generations.findRunning(schoolId)?.let { fail(ApplicationError.GenerationRunning(it.id)) }
                    requireReady(schoolId, request)
                    val stored = problems.load(schoolId)
                    requireValidSeparateObjectives(request, stored)
                    requireNoContradictions(schoolId, stored)
                    val reference = if (request.startFromChosen) requireChosen(schoolId) else null
                    val schedulingProblem = stored.copy(separateObjectiveIds = request.separateObjectiveIds, reference = reference?.lessons)
                    val fingerprint = SchedulingInputFingerprint.of(schedulingProblem)
                    val generation =
                        Generation
                            .start(
                                schoolId,
                                actor,
                                clock.instant(),
                                request.timeLimitMinutes,
                                fingerprint,
                                request.minimumPlacedShare,
                            ).bind()
                            .copy(
                                separateObjectiveIds = request.separateObjectiveIds,
                                constraintSnapshot = stored.constraints.filter { it.active },
                                fixationSnapshot = stored.fixations,
                                referenceAlternativeId = reference?.alternativeId,
                            )
                    generations.insert(generation)
                    problem = schedulingProblem
                    generation
                }
            }
        if (result is UseCaseResult.Success) {
            val startedProblem = checkNotNull(problem)
            executor.submit { runner.run(result.value, startedProblem) }
        }
        return result
    }

    /** FR-006b: starting from the chosen timetable needs one. */
    private fun OutcomeScope.requireChosen(schoolId: SchoolId): ChosenTimetable =
        chosenTimetables.find(schoolId)
            ?: fail(ApplicationError.Invalid(listOf(ValidationError.InvalidReference(StartRequest.FIELD_START_FROM_CHOSEN))))

    /** FR-006a: at most three active soft constraints of the school can be compared separately. */
    private fun OutcomeScope.requireValidSeparateObjectives(
        request: StartRequest,
        problem: SchedulingProblem,
    ) {
        val ids = request.separateObjectiveIds
        if (ids.size > StartRequest.MAX_SEPARATE_OBJECTIVES || ids.toSet().size != ids.size) {
            fail(
                ApplicationError.Invalid(
                    listOf(ValidationError.OutOfRange(StartRequest.FIELD_SEPARATE_OBJECTIVES, 0, StartRequest.MAX_SEPARATE_OBJECTIVES)),
                ),
            )
        }
        val activeSoft =
            problem.constraints
                .filter { it.active && !it.isHard }
                .map { it.id }
                .toSet()
        if (!activeSoft.containsAll(ids)) {
            fail(ApplicationError.Invalid(listOf(ValidationError.InvalidReference(StartRequest.FIELD_SEPARATE_OBJECTIVES))))
        }
    }

    /** FR-017: hard constraints that can never be met with the master data refuse the start and are named. */
    private fun OutcomeScope.requireNoContradictions(
        schoolId: SchoolId,
        problem: SchedulingProblem,
    ) {
        val contradicting = InfeasibilityPreCheck.contradictionsOf(problem).toSet()
        if (contradicting.isNotEmpty()) {
            fail(ApplicationError.ContradictingConstraints(problem.constraints.filter { it.id in contradicting }, names.load(schoolId)))
        }
    }

    /** Blocking issues refuse the start and are named; warnings need the planner's confirmation (FR-010). */
    private fun OutcomeScope.requireReady(
        schoolId: SchoolId,
        request: StartRequest,
    ) {
        val report = readiness.load(schoolId)
        val gate = GenerationGate.classify(report.issues)
        if (gate.blocking.isNotEmpty()) {
            fail(ApplicationError.GenerationBlocked(gate.blocking, report))
        }
        if (gate.warnings.isNotEmpty() && !request.warningsConfirmed) {
            fail(ApplicationError.Invalid(listOf(ValidationError.ConfirmationMissing(StartRequest.FIELD_WARNINGS_CONFIRMED))))
        }
    }
}

/** FR-009: asks a running generation to stop; the alternatives found so far remain its result. */
class StopGeneration(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val generations: GenerationRepository,
    private val control: GenerationControl,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        generationId: GenerationId,
    ): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val generation = loadGenerationOfSchool(generations, schoolId, generationId)
                if (generation.isRunning) {
                    control.requestStop(generationId)
                }
            }
        }
}

/** Edge case "server restart": generations still marked running cannot continue and are ended as failed. */
class RecoverInterruptedGenerations(
    private val transactions: TransactionRunner,
    private val generations: GenerationRepository,
    private val clock: Clock,
) {
    fun execute(): Int {
        var recovered = 0
        transactions.inTransaction {
            recovered = generations.failRunning(clock.instant())
            UseCaseResult.Success(Unit)
        }
        return recovered
    }
}

/**
 * US4: deletes a generation the planner no longer needs, with its alternatives. A running generation must be stopped
 * first; the generation of the chosen timetable is kept (FR-024).
 */
class DeleteGeneration(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val generations: GenerationRepository,
    private val chosenTimetables: ChosenTimetableRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        generationId: GenerationId,
    ): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val generation = loadGenerationOfSchool(generations, schoolId, generationId)
                if (generation.isRunning) {
                    fail(ApplicationError.GenerationRunning(generationId))
                }
                if (chosenTimetables.find(schoolId)?.generationId == generationId) {
                    fail(ApplicationError.DeletionBlocked(listOf(Dependant(DependantType.CHOSEN_TIMETABLE, "")), hiddenDependantCount = 0))
                }
                generations.delete(generationId)
            }
        }
}

class SchedulingUseCases(
    val start: StartGeneration,
    val stop: StopGeneration,
    val list: ListGenerations,
    val get: GetGeneration,
    val getAlternative: GetAlternative,
    val recoverInterrupted: RecoverInterruptedGenerations,
    val choose: ChooseAlternative,
    val getChosen: GetChosenTimetable,
    val compare: CompareAlternatives,
    val delete: DeleteGeneration,
)
