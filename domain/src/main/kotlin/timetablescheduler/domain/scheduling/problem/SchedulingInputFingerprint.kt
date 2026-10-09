package timetablescheduler.domain.scheduling.problem

import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import java.security.MessageDigest

/**
 * Identifies the scheduling input of a generation (G8): a generation is outdated as soon as the fingerprint of the
 * current master data and constraints differs from the one stored at its start. Entries are written in a canonical,
 * sorted text form so that only content — never list order — changes the hash.
 */
object SchedulingInputFingerprint {

    fun of(problem: SchedulingProblem): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalText(problem).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun canonicalText(problem: SchedulingProblem): String = buildList {
        problem.days.sortedBy { it.day }.forEach { day -> add("day ${day.day} " + day.slots.joinToString(",") { "${it.slotId.value}:${it.kind}" }) }
        problem.rooms.forEach { add("room ${it.id.value} ${it.featureIds.map { feature -> feature.value }.sorted()}") }
        problem.subjects.forEach { add("subject ${it.id.value} ${it.requiredFeatureIds.map { feature -> feature.value }.sorted()}") }
        problem.classes.forEach { schoolClass ->
            val curriculum = schoolClass.curriculum.map { "${it.subjectId.value}=${it.periodsPerWeek}" }.sorted()
            add("class ${schoolClass.id.value} ${schoolClass.priorityWeight} ${schoolClass.homeRoomId?.value} $curriculum")
        }
        problem.teachers.forEach { teacher ->
            val unavailable = teacher.unavailable.map(::daySlotText).sorted()
            add("teacher ${teacher.id.value} ${teacher.weeklyLoad} ${teacher.qualifiedSubjectIds.map { it.value }.sorted()} $unavailable")
        }
        problem.constraints.forEach { add("constraint " + constraintText(it)) }
        problem.fixations.forEach { add("fixation ${it.classId.value} ${it.subjectId.value} ${it.teacherId.value}") }
    }.sorted().joinToString("\n")

    private fun daySlotText(daySlot: DaySlot) = "${daySlot.day}:${daySlot.slotId.value}"

    /** The rule's data-class text form; rules keep their sets sorted so the text is canonical. */
    private fun constraintText(constraint: SchedulingConstraint): String =
        "${constraint.id.value} ${constraint.kind} ${constraint.weight} ${constraint.active} ${constraint.rule}"
}
