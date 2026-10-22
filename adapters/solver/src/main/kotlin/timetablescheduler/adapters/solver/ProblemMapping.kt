package timetablescheduler.adapters.solver

import timetablescheduler.adapters.solver.model.AppliedRuleFact
import timetablescheduler.adapters.solver.model.ClassFact
import timetablescheduler.adapters.solver.model.CourseAssignment
import timetablescheduler.adapters.solver.model.LessonPlacement
import timetablescheduler.adapters.solver.model.ObjectiveLayout
import timetablescheduler.adapters.solver.model.PassWeights
import timetablescheduler.adapters.solver.model.RoomFact
import timetablescheduler.adapters.solver.model.TeacherFact
import timetablescheduler.adapters.solver.model.TimeslotFact
import timetablescheduler.adapters.solver.model.TimetablePlan
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.AppliedConstraint
import timetablescheduler.domain.scheduling.constraint.EffectiveConstraints
import timetablescheduler.domain.scheduling.constraint.RuleViolations
import timetablescheduler.domain.scheduling.problem.ProblemClass
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import timetablescheduler.domain.school.SlotKind

/** Translates between the domain's [SchedulingProblem] / [TimetableCandidate] and the Timefold model (G5). */
object ProblemMapping {
    fun toPlan(problem: SchedulingProblem): TimetablePlan {
        val timeslots = timeslotsOf(problem)
        val weekIndexByDaySlot = timeslots.associate { it.daySlot to it.weekIndex }
        val rooms = problem.rooms.map { RoomFact(it.id, it.featureIds) }
        val teachers =
            problem.teachers.map { teacher ->
                TeacherFact(
                    teacher.id,
                    teacher.weeklyLoad,
                    teacher.qualifiedSubjectIds,
                    teacher.unavailable.mapNotNull { weekIndexByDaySlot[it] }.toSet(),
                )
            }
        val classes = problem.classes.map { ClassFact(it.id, it.priorityWeight) }
        val courses = problem.classes.flatMap { schoolClass -> coursesOf(schoolClass, classes, teachers) }
        pinFixedTeachers(problem, courses, teachers)
        var nextLessonId = 0
        val lessons =
            courses.flatMap { course ->
                val suitable = suitableRooms(problem, course, rooms)
                if (suitable.isEmpty()) emptyList() else List(course.requiredPeriods) { LessonPlacement(nextLessonId++, course, suitable) }
            }
        problem.reference?.let { startFromReference(it, timeslots, teachers, courses, lessons) }
        return TimetablePlan().apply {
            this.timeslots = timeslots
            this.rooms = rooms
            this.teachers = teachers
            this.classes = classes
            this.courses = courses
            this.lessons = lessons
            this.rules = ruleFactsOf(problem)
            this.passWeights =
                PassWeights(
                    weights = List(objectiveCount(problem)) { 1.0 / objectiveCount(problem) },
                    totalWeightedDemand = totalWeightedDemand(problem),
                    totalTeacherLoad = teachers.sumOf { it.weeklyLoad }.coerceAtLeast(1),
                    stabilityIndex = if (problem.reference != null) objectiveCount(problem) - 1 else -1,
                    totalReferenceLessons = problem.reference?.size?.coerceAtLeast(1) ?: 1,
                )
        }
    }

    /**
     * The objectives the solver weighs (G3): class fill, teacher load usage, one per soft constraint compared
     * separately (FR-006a), the other soft constraints combined if any is active and, when starting from the chosen
     * timetable, stability as the last one (FR-006b).
     */
    fun objectiveCount(problem: SchedulingProblem): Int =
        ObjectiveLayout.FIRST_SEPARATE + problem.separateObjectiveIds.size +
            (if (hasOtherSoftConstraints(problem)) 1 else 0) +
            (if (problem.reference != null) 1 else 0)

    /**
     * FR-006b: every lesson is matched with a reference lesson of its course and starts there (warm start), as far as
     * timeslot and room still exist; the course keeps the reference teacher unless it is fixed or no longer qualified.
     * Lessons that now break a hard constraint are moved by the solver; unmatched lessons are constructed as usual.
     */
    private fun startFromReference(
        reference: List<LessonAssignment>,
        timeslots: List<TimeslotFact>,
        teachers: List<TeacherFact>,
        courses: List<CourseAssignment>,
        lessons: List<LessonPlacement>,
    ) {
        val timeslotByDaySlot = timeslots.associateBy { it.daySlot }
        val referenceByCourse = reference.groupBy { it.course }
        val lessonsByCourse = lessons.groupBy { it.course }
        courses.forEach { course ->
            val referenceLessons = referenceByCourse[CourseKey(course.classFact.id, course.subjectId)].orEmpty()
            val referenceTeacher = referenceLessons.firstOrNull()?.let { lesson -> teachers.firstOrNull { it.id == lesson.teacherId } }
            if (!course.pinned && referenceTeacher != null && referenceTeacher in course.qualifiedTeachers) {
                course.teacher = referenceTeacher
            }
            lessonsByCourse[course].orEmpty().zip(referenceLessons).forEach { (lesson, referenceLesson) ->
                lesson.referenceTimeslot = timeslotByDaySlot[referenceLesson.daySlot]
                lesson.referenceRoom = lesson.suitableRooms.firstOrNull { it.id == referenceLesson.roomId }
                lesson.referenceTeacher = teachers.firstOrNull { it.id == referenceLesson.teacherId }
                if (lesson.referenceTimeslot != null && lesson.referenceRoom != null) {
                    lesson.timeslot = lesson.referenceTimeslot
                    lesson.room = lesson.referenceRoom
                }
            }
        }
    }

    private fun hasOtherSoftConstraints(problem: SchedulingProblem) =
        EffectiveConstraints.resolve(problem).any { !it.constraint.isHard && it.constraint.id !in problem.separateObjectiveIds }

    /**
     * One fact per applied constraint. A soft constraint compared separately is normalized by its own opportunities;
     * all other soft constraints share one objective normalized by the sum of weight times opportunities (G3), so the
     * solver's soft score moves exactly like the objective values of the domain.
     */
    private fun ruleFactsOf(problem: SchedulingProblem): List<AppliedRuleFact> {
        val applied = EffectiveConstraints.resolve(problem)

        fun opportunities(rule: AppliedConstraint) = RuleViolations.of(rule, problem, emptyList()).opportunities
        val separateOpportunities =
            applied
                .filter { it.constraint.id in problem.separateObjectiveIds }
                .groupBy { it.constraint.id }
                .mapValues { (_, rules) -> rules.sumOf(::opportunities).toDouble() }
        val others = applied.filter { !it.constraint.isHard && it.constraint.id !in problem.separateObjectiveIds }
        val otherNormalizer = others.sumOf { (it.constraint.weight?.factor ?: 1) * opportunities(it) }.toDouble().coerceAtLeast(1.0)
        val otherIndex = ObjectiveLayout.FIRST_SEPARATE + problem.separateObjectiveIds.size
        return applied.mapIndexed { index, rule ->
            val separatePosition = problem.separateObjectiveIds.indexOf(rule.constraint.id)
            when {
                rule.constraint.isHard -> {
                    AppliedRuleFact(index, rule, problem, objectiveIndex = -1, weightFactor = 0, normalizer = 1.0)
                }

                separatePosition >= 0 -> {
                    AppliedRuleFact(
                        index,
                        rule,
                        problem,
                        ObjectiveLayout.FIRST_SEPARATE + separatePosition,
                        1,
                        separateOpportunities.getValue(rule.constraint.id),
                    )
                }

                else -> {
                    AppliedRuleFact(index, rule, problem, otherIndex, rule.constraint.weight?.factor ?: 1, otherNormalizer)
                }
            }
        }
    }

    /** FR-013a: a fixed course keeps its teacher; it is pinned so the solver never changes it. */
    private fun pinFixedTeachers(
        problem: SchedulingProblem,
        courses: List<CourseAssignment>,
        teachers: List<TeacherFact>,
    ) {
        problem.fixations.forEach { fixation ->
            val course = courses.firstOrNull { it.classFact.id == fixation.classId && it.subjectId == fixation.subjectId } ?: return@forEach
            course.teacher = teachers.firstOrNull { it.id == fixation.teacherId } ?: return@forEach
            course.pinned = true
        }
    }

    /** Placed lessons with their teacher; the remaining periods of each course are reported as unplaced (FR-005). */
    fun toCandidate(plan: TimetablePlan): TimetableCandidate {
        val placed = plan.lessons.filter { it.isPlaced && it.course.teacher != null }
        val lessons =
            placed.map { lesson ->
                LessonAssignment(
                    lesson.course.classFact.id,
                    lesson.course.subjectId,
                    lesson.course.teacher!!.id,
                    lesson.room!!.id,
                    lesson.timeslot!!.daySlot,
                )
            }
        val placedByCourse = lessons.groupingBy { it.course }.eachCount()
        val unplaced =
            plan.courses
                .associate {
                    CourseKey(it.classFact.id, it.subjectId) to
                        it.requiredPeriods - (placedByCourse[CourseKey(it.classFact.id, it.subjectId)] ?: 0)
                }.filterValues { it > 0 }
        return TimetableCandidate(lessons, unplaced)
    }

    private fun timeslotsOf(problem: SchedulingProblem): List<TimeslotFact> {
        var weekIndex = 0
        return problem.days.sortedBy { it.day }.flatMap { day ->
            day.slots
                .withIndex()
                .filter { (_, slot) -> slot.kind == SlotKind.TEACHING }
                .map { (position, slot) -> TimeslotFact(weekIndex++, DaySlot(day.day, slot.slotId), position) }
        }
    }

    private fun coursesOf(
        schoolClass: ProblemClass,
        classes: List<ClassFact>,
        teachers: List<TeacherFact>,
    ): List<CourseAssignment> {
        val classFact = classes.single { it.id == schoolClass.id }
        return schoolClass.curriculum.map { entry ->
            CourseAssignment(
                classFact,
                entry.subjectId,
                entry.periodsPerWeek,
                teachers.filter { entry.subjectId in it.qualifiedSubjectIds },
            )
        }
    }

    /** Rooms offering all required features; for subjects without features only the home room, if the class has one. */
    private fun suitableRooms(
        problem: SchedulingProblem,
        course: CourseAssignment,
        rooms: List<RoomFact>,
    ): List<RoomFact> {
        val required = problem.subject(course.subjectId)?.requiredFeatureIds ?: return emptyList()
        val homeRoom = problem.schoolClass(course.classFact.id)?.homeRoomId
        if (required.isEmpty() && homeRoom != null) {
            return rooms.filter { it.id == homeRoom }
        }
        return rooms.filter { it.featureIds.containsAll(required) }
    }

    private fun totalWeightedDemand(problem: SchedulingProblem): Double =
        problem.classes
            .sumOf { schoolClass -> schoolClass.priorityWeight * schoolClass.curriculum.sumOf { it.periodsPerWeek } }
            .toDouble()
            .coerceAtLeast(1.0)
}
