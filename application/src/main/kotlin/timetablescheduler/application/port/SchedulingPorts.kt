package timetablescheduler.application.port

import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.Duration

/** Lets a planner end a running generation early (FR-009); polled by the solver. */
fun interface StopSignal {
    fun isStopRequested(): Boolean
}

/** Receives what the solver finds while it runs. */
interface SolverListener {
    /** A new best timetable of a solver pass; the application checks and evaluates it before keeping it (G4). */
    fun onCandidate(candidate: TimetableCandidate)

    /** A pass ended without meeting all hard constraints — a hint at contradicting constraints, not a failure (G4). */
    fun onInfeasiblePass()
}

/** Searches timetables for a problem within a time limit (G1, G2); implemented by the solver adapter. */
interface TimetableSolver {
    fun solve(problem: SchedulingProblem, timeLimit: Duration, stopSignal: StopSignal, listener: SolverListener)
}

/** Runs generations in the background, one after another (G6). */
fun interface GenerationExecutor {
    fun submit(task: () -> Unit)
}
