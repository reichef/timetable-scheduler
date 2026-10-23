package timetablescheduler.application.scheduling

import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.ChosenTimetableRepository
import timetablescheduler.application.port.GenerationRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.evaluation.HardConstraintChecker
import timetablescheduler.domain.scheduling.evaluation.HardRule
import timetablescheduler.domain.scheduling.evaluation.HardViolation
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.generation.ChosenTimetable
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationId
import timetablescheduler.domain.scheduling.solution.ChosenTimetableDrift
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.Drift
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.Clock

/** What choosing an alternative led to. */
sealed interface ChooseOutcome {
    data class Chosen(
        val chosen: ChosenTimetable,
    ) : ChooseOutcome

    /** The alternative leaves lessons unplaced; the planner must confirm them first (FR-021). */
    data class ConfirmationRequired(
        val generation: Generation,
        val alternative: Alternative,
        val unplaced: Map<CourseKey, Int>,
        val names: TimetableNames,
    ) : ChooseOutcome
}

/**
 * The chosen timetable with today's names, who chose it and from which generation, the lessons that now violate a
 * hard constraint, and how it differs from today's curricula (FR-023). [chosen] is null while no timetable has been
 * chosen. Lessons no longer required are part of [drift] ("entfällt"), not of [conflicts].
 */
data class ChosenTimetableDetails(
    val schoolId: SchoolId,
    val chosen: ChosenTimetable?,
    val names: TimetableNames,
    val conflicts: List<HardViolation>,
    val drift: Drift?,
    val chosenByUsername: String = "",
    val sourceGeneration: Generation? = null,
    val constraints: List<SchedulingConstraint> = emptyList(),
) {
    val fitsCurrentData: Boolean get() = conflicts.isEmpty() && drift?.isEmpty != false
}

/** Two alternatives of a generation side by side (FR-020). */
data class AlternativeComparison(
    val generation: Generation,
    val first: Alternative,
    val firstNumber: Int,
    val second: Alternative,
    val secondNumber: Int,
    val names: TimetableNames,
)

/**
 * FR-021, FR-028: an alternative becomes the school's timetable if it violates no hard constraint under today's data
 * (otherwise the conflicts are named, US3 scenario 7) and, when it leaves lessons unplaced, after the planner confirmed
 * them. [expectedChosenVersion] is the version of the choice the planner saw (null when none existed).
 */
class ChooseAlternative(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val generations: GenerationRepository,
    private val chosenTimetables: ChosenTimetableRepository,
    private val problems: ConstraintContextLoader,
    private val names: TimetableNamesLoader,
    private val clock: Clock,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        generationId: GenerationId,
        alternativeId: AlternativeId,
        expectedChosenVersion: Int?,
        unplacedConfirmed: Boolean,
    ): UseCaseResult<ChooseOutcome> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()

                val generation = loadGenerationOfSchool(generations, schoolId, generationId)
                val alternative =
                    generations.findAlternative(alternativeId)?.takeIf { it.generationId == generationId }
                        ?: fail(ApplicationError.NotFound)

                val problem = problems.load(schoolId)
                val violations = HardConstraintChecker.violationsOf(problem, TimetableCandidate(alternative.lessons, alternative.unplaced))
                if (violations.isNotEmpty()) {
                    fail(ApplicationError.AlternativeConflicts(violations, names.load(schoolId), problem.constraints))
                }

                if (alternative.unplaced.isNotEmpty() && !unplacedConfirmed) {
                    return@outcome ChooseOutcome.ConfirmationRequired(generation, alternative, alternative.unplaced, names.load(schoolId))
                }

                val current = chosenTimetables.find(schoolId)
                if (current?.version != expectedChosenVersion) {
                    fail(ApplicationError.VersionConflict)
                }

                val chosen =
                    ChosenTimetable(
                        schoolId,
                        alternative.id,
                        generation.id,
                        actor,
                        clock.instant(),
                        alternative.lessons,
                        alternative.unplaced,
                        version = current?.version?.plus(1) ?: 0,
                    )

                requireUpdated(chosenTimetables.save(chosen, expectedChosenVersion))

                ChooseOutcome.Chosen(chosen)
            }
        }
}

class GetChosenTimetable(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val chosenTimetables: ChosenTimetableRepository,
    private val problems: ConstraintContextLoader,
    private val names: TimetableNamesLoader,
    private val generations: GenerationRepository,
    private val accounts: UserAccountRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
    ): UseCaseResult<ChosenTimetableDetails> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val chosen = chosenTimetables.find(schoolId)
                val problem = problems.load(schoolId)
                ChosenTimetableDetails(
                    schoolId = schoolId,
                    chosen = chosen,
                    names = names.load(schoolId),
                    conflicts =
                        chosen
                            ?.let { HardConstraintChecker.violationsOf(problem, TimetableCandidate(it.lessons, it.unplaced)) }
                            .orEmpty()
                            .filter { it.rule != HardRule.LESSON_NOT_REQUIRED },
                    drift = chosen?.let { ChosenTimetableDrift.of(it.lessons, problem) },
                    chosenByUsername = chosen?.let { accounts.findById(it.chosenBy)?.username }.orEmpty(),
                    sourceGeneration = chosen?.let { generations.findById(it.generationId) },
                    constraints = problem.constraints,
                )
            }
        }
}

class CompareAlternatives(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val generations: GenerationRepository,
    private val names: TimetableNamesLoader,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        generationId: GenerationId,
        first: AlternativeId,
        second: AlternativeId,
    ): UseCaseResult<AlternativeComparison> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()

                val generation = loadGenerationOfSchool(generations, schoolId, generationId)
                val alternatives = generations.findAlternatives(generationId)
                val firstIndex = alternatives.indexOfFirst { it.id == first }
                val secondIndex = alternatives.indexOfFirst { it.id == second }
                if (firstIndex < 0 || secondIndex < 0) {
                    fail(ApplicationError.NotFound)
                }

                AlternativeComparison(
                    generation,
                    alternatives[firstIndex],
                    firstIndex + 1,
                    alternatives[secondIndex],
                    secondIndex + 1,
                    names.load(schoolId),
                )
            }
        }
}
