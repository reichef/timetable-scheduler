package timetablescheduler.adapters.solver

import ai.timefold.solver.core.api.solver.Solver
import ai.timefold.solver.core.api.solver.SolverConfigOverride
import ai.timefold.solver.core.api.solver.SolverFactory
import ai.timefold.solver.core.config.solver.SolverConfig
import ai.timefold.solver.core.config.solver.termination.TerminationConfig
import timetablescheduler.adapters.solver.constraints.TimetableConstraintProvider
import timetablescheduler.adapters.solver.model.CourseAssignment
import timetablescheduler.adapters.solver.model.LessonPlacement
import timetablescheduler.adapters.solver.model.TimetablePlan
import timetablescheduler.application.port.SolverListener
import timetablescheduler.application.port.StopSignal
import timetablescheduler.application.port.TimetableSolver
import timetablescheduler.domain.scheduling.pareto.WeightVectors
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Timefold implementation of [TimetableSolver] (G1, G2, G12): a sequence of solver passes, each optimizing the weighted
 * sum of the objectives with another weight vector and starting from the best timetable of the previous pass. Every new
 * feasible best timetable is reported to the listener, which keeps the equally optimal ones.
 *
 * [minimumFirstPass] guarantees the first pass enough time to construct a first timetable (60 s in production).
 */
class TimefoldTimetableSolver(private val minimumFirstPass: Duration = Duration.ofSeconds(60)) : TimetableSolver {

    private val solverFactory: SolverFactory<TimetablePlan> by lazy {
        SolverFactory.create(
            SolverConfig()
                .withSolutionClass(TimetablePlan::class.java)
                .withEntityClasses(CourseAssignment::class.java, LessonPlacement::class.java)
                .withConstraintProviderClass(TimetableConstraintProvider::class.java)
                .withTerminationSpentLimit(minimumFirstPass),
        )
    }

    override fun solve(problem: SchedulingProblem, timeLimit: Duration, stopSignal: StopSignal, listener: SolverListener) {
        val deadline = Instant.now().plus(timeLimit)
        val weightVectors = WeightVectors.forObjectives(SOLVER_OBJECTIVES)
        var plan = ProblemMapping.toPlan(problem)
        StopWatcher(stopSignal).use { watcher ->
            for ((passIndex, weights) in weightVectors.withIndex()) {
                val remaining = Duration.between(Instant.now(), deadline)
                if (stopSignal.isStopRequested() || remaining <= Duration.ZERO) break
                plan.passWeights = plan.passWeights.copy(classFill = weights[0], teacherLoad = weights[1])
                plan.score = null
                val solver = buildPassSolver(passDuration(passIndex, weightVectors.size, timeLimit, remaining), listener)
                watcher.watch(solver)
                val best = solver.solve(plan)
                watcher.watch(null)
                if (best.score?.isFeasible == true) listener.onCandidate(ProblemMapping.toCandidate(best)) else listener.onInfeasiblePass()
                plan = best
            }
        }
    }

    /** A solver for one pass that reports every new feasible, fully constructed best timetable. */
    private fun buildPassSolver(limit: Duration, listener: SolverListener): Solver<TimetablePlan> {
        val solver = solverFactory.buildSolver(SolverConfigOverride().withTerminationConfig(TerminationConfig().withSpentLimit(limit)))
        solver.addEventListener { event ->
            if (event.isNewBestSolutionInitialized && event.newBestScore.isFeasible) listener.onCandidate(ProblemMapping.toCandidate(event.newBestSolution))
        }
        return solver
    }

    /**
     * G12: the first pass gets at least [minimumFirstPass] (but never more than the time left), so the first timetable
     * is available early; the remaining time is shared evenly by the passes still to come.
     */
    private fun passDuration(passIndex: Int, passCount: Int, timeLimit: Duration, remaining: Duration): Duration {
        val share = if (passIndex == 0) maxOf(minimumFirstPass, timeLimit.dividedBy(passCount.toLong())) else remaining.dividedBy((passCount - passIndex).toLong())
        // Timefold accepts whole milliseconds only.
        return Duration.ofMillis(minOf(share, remaining).toMillis().coerceAtLeast(MINIMUM_PASS_MILLIS))
    }

    /** Polls the planner's stop request and ends the running pass early; Timefold must be stopped from another thread. */
    private class StopWatcher(private val stopSignal: StopSignal) : AutoCloseable {
        @Volatile
        private var current: Solver<TimetablePlan>? = null
        private val poller: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "generation-stop-watcher").apply { isDaemon = true }
        }

        init {
            poller.scheduleAtFixedRate({ if (stopSignal.isStopRequested()) current?.terminateEarly() }, POLL_MILLIS, POLL_MILLIS, TimeUnit.MILLISECONDS)
        }

        fun watch(solver: Solver<TimetablePlan>?) {
            current = solver
            if (solver != null && stopSignal.isStopRequested()) solver.terminateEarly()
        }

        override fun close() {
            poller.shutdownNow()
        }
    }

    private companion object {
        /** Class fill and teacher load usage; further objectives come with planner constraints (US2) and the reference (US3). */
        const val SOLVER_OBJECTIVES = 2
        const val POLL_MILLIS = 100L
        const val MINIMUM_PASS_MILLIS = 200L
    }
}
