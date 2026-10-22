package timetablescheduler.application.port

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.generation.ChosenTimetable
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationId
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.Duration
import java.time.Instant

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

/** Generations and their alternatives (FR-022); every call runs inside the caller's transaction. */
interface GenerationRepository {
    fun insert(generation: Generation)
    fun findById(generationId: GenerationId): Generation?
    fun findBySchool(schoolId: SchoolId): List<Generation>
    fun findRunning(schoolId: SchoolId): Generation?

    /** Progress shown while a generation runs (FR-011). */
    fun updateProgress(generationId: GenerationId, alternativesFound: Int)

    fun saveEnd(generation: Generation)

    /** Ends every generation still marked running as failed, e.g. after a server restart; returns how many. */
    fun failRunning(at: Instant): Int

    fun addAlternative(alternative: Alternative)
    fun removeAlternative(alternativeId: AlternativeId)
    fun findAlternatives(generationId: GenerationId): List<Alternative>
    fun findAlternative(alternativeId: AlternativeId): Alternative?

    /** Removes the generation with its alternatives and snapshots (US4). */
    fun delete(generationId: GenerationId)
}

/**
 * Technical log of generations (G13, FR-026). Its methods take only identifiers, statuses, counts and durations,
 * so lesson data, names or constraint descriptions cannot reach the log by construction.
 */
interface GenerationEventLog {
    fun started(generationId: GenerationId)
    fun discarded(generationId: GenerationId, candidates: Int)
    fun finished(generationId: GenerationId, status: GenerationStatus, alternatives: Int, duration: Duration)
    fun failed(generationId: GenerationId, exceptionClass: String)
}

/** The school's chosen timetable (FR-021); at most one per school. */
interface ChosenTimetableRepository {
    fun find(schoolId: SchoolId): ChosenTimetable?

    /**
     * Stores [chosen] as the school's timetable: inserted when [expectedVersion] is null and none exists yet, otherwise
     * replaced if the stored version equals [expectedVersion]. Returns false on a concurrent choice (FR-028).
     */
    fun save(chosen: ChosenTimetable, expectedVersion: Int?): Boolean
}
