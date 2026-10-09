package timetablescheduler.application.scheduling

import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.port.GenerationEventLog
import timetablescheduler.application.port.GenerationRepository
import timetablescheduler.application.port.SolverListener
import timetablescheduler.application.port.TimetableSolver
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.domain.scheduling.evaluation.HardConstraintChecker
import timetablescheduler.domain.scheduling.evaluation.ObjectiveEvaluator
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.scheduling.pareto.ArchiveChange
import timetablescheduler.domain.scheduling.pareto.ParetoArchive
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.Clock
import java.time.Duration

/**
 * Runs one generation in the background (G2, G4, G6): every timetable the solver reports is checked against all
 * hard constraints by the domain, evaluated, and offered to the Pareto archive; archive changes are stored at once,
 * so a stop or crash keeps what was found. The end status follows G4: `FAILED_INFEASIBLE` only when not a single
 * valid alternative was found while the solver reported infeasible passes.
 */
class GenerationRunner(
    private val transactions: TransactionRunner,
    private val generations: GenerationRepository,
    private val solver: TimetableSolver,
    private val control: GenerationControl,
    private val eventLog: GenerationEventLog,
    private val clock: Clock,
) {
    fun run(generation: Generation, problem: SchedulingProblem) {
        eventLog.started(generation.id)
        val stopSignal = control.stopSignalOf(generation.id)
        val collector = AlternativeCollector(generation, problem)
        val status = try {
            solver.solve(problem, Duration.ofMinutes(generation.timeLimitMinutes.toLong()), stopSignal, collector)
            when {
                collector.keptCount() == 0 && collector.infeasiblePassSeen -> GenerationStatus.FAILED_INFEASIBLE
                stopSignal.isStopRequested() -> GenerationStatus.STOPPED
                else -> GenerationStatus.FINISHED
            }
        } catch (failure: Exception) {
            eventLog.failed(generation.id, failure.javaClass.name)
            GenerationStatus.FAILED
        } finally {
            control.release(generation.id)
        }
        val ended = generation.endAs(status, clock.instant())
        inTransaction { generations.saveEnd(ended.copy(alternativesFound = collector.keptCount())) }
        if (collector.discardedCount > 0) eventLog.discarded(generation.id, collector.discardedCount)
        eventLog.finished(generation.id, status, collector.keptCount(), Duration.between(generation.startedAt, checkNotNull(ended.finishedAt)))
    }

    private fun inTransaction(block: () -> Unit) {
        transactions.inTransaction {
            block()
            UseCaseResult.Success(Unit)
        }
    }

    /** Receives the solver's timetables for one generation and keeps the equally optimal valid ones. */
    private inner class AlternativeCollector(private val generation: Generation, private val problem: SchedulingProblem) : SolverListener {
        private val archive = ParetoArchive<Alternative>(generation.maxAlternatives) { first, second -> first.candidate.sameTimetableAs(second.candidate) }
        var discardedCount = 0
            private set
        var infeasiblePassSeen = false
            private set

        fun keptCount(): Int = archive.entries.size

        override fun onCandidate(candidate: TimetableCandidate) {
            if (HardConstraintChecker.violationsOf(problem, candidate).isNotEmpty()) {
                discardedCount++
                return
            }
            val evaluation = ObjectiveEvaluator.evaluate(problem, candidate)
            val alternative = Alternative(
                id = AlternativeId.new(),
                generationId = generation.id,
                objectiveValues = evaluation.values,
                lessons = candidate.lessons,
                unplaced = candidate.unplaced.filterValues { it > 0 },
                softViolations = evaluation.softViolations,
            )
            store(archive.offer(alternative, alternative.objectiveValues.asVector()), alternative)
        }

        override fun onInfeasiblePass() {
            infeasiblePassSeen = true
        }

        private fun store(change: ArchiveChange<Alternative>, alternative: Alternative) {
            if (!change.admitted && change.evicted.isEmpty()) return
            inTransaction {
                change.evicted.forEach { generations.removeAlternative(it.id) }
                if (change.admitted) generations.addAlternative(alternative)
                generations.updateProgress(generation.id, keptCount())
            }
        }
    }
}
