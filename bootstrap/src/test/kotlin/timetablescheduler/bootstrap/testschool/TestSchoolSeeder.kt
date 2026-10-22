package timetablescheduler.bootstrap.testschool

import org.slf4j.LoggerFactory
import timetablescheduler.application.MasterDataUseCases
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.scheduling.ConstraintInput
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.teacher.Unavailability

/** Seeding was refused because the school already has master data; test data is never mixed into real data. */
class TestSchoolSeedingRefused(message: String) : RuntimeException(message)

/** The ids of the seeded test school, by the names used in its [TestSchoolData]. */
data class SeededTestSchool(
    val schoolId: SchoolId,
    val slotIds: Map<String, TimeSlotId>,
    val roomIds: Map<String, RoomId>,
    val subjectIds: Map<String, SubjectId>,
    val classIds: Map<String, SchoolClassId>,
    val teacherIds: Map<String, TeacherId>,
) {
    fun daySlot(slot: TestDaySlot) = DaySlot(slot.day, slotIds.getValue(slot.slotLabel))
}

/**
 * Enters a test school ([BwGymnasiumTestSet] or a variant) into an installed, still empty school through the application use cases, as a planner
 * would in the user interface, so every validation of features 001 and 002 applies. Only counts are logged
 * (constitution III).
 */
class TestSchoolSeeder(private val useCases: MasterDataUseCases, private val testSet: TestSchoolData = BwGymnasiumTestSet) {

    fun seed(actor: AccountId, schoolId: SchoolId): SeededTestSchool {
        requireEmptySchool(actor, schoolId)

        renameSchool(actor, schoolId)
        val slotIds = createTimeGrid(actor, schoolId)

        val featureIds = createFeatures(actor, schoolId)
        val roomIds = createRooms(actor, schoolId, featureIds)
        val subjectIds = createSubjects(actor, schoolId, featureIds)
        val classIds = createClasses(actor, schoolId, subjectIds, roomIds)
        val teacherIds = createTeachers(actor, schoolId, subjectIds, slotIds)

        val seeded = SeededTestSchool(schoolId, slotIds, roomIds, subjectIds, classIds, teacherIds)
        createConstraints(actor, seeded)
        createFixations(actor, seeded)

        log.info("Test school seeded: {} rooms, {} subjects, {} classes, {} teachers", roomIds.size, subjectIds.size, classIds.size, teacherIds.size)
        return seeded
    }

    private fun requireEmptySchool(actor: AccountId, schoolId: SchoolId) {
        val hasRooms = useCases.rooms.listRooms.execute(actor, schoolId).required("list rooms").rooms.isNotEmpty()
        val hasSubjects = useCases.curriculum.listSubjects.execute(actor, schoolId).required("list subjects").subjects.isNotEmpty()
        val hasLevels = useCases.curriculum.listClassLevels.execute(actor, schoolId).required("list class levels").levels.isNotEmpty()
        val hasTeachers = useCases.teachers.list.execute(actor, schoolId).required("list teachers").isNotEmpty()

        if (hasRooms || hasSubjects || hasLevels || hasTeachers) {
            throw TestSchoolSeedingRefused("The school already has master data; the test school is only seeded into an empty school")
        }
    }

    private fun renameSchool(actor: AccountId, schoolId: SchoolId) {
        val school = useCases.timeGrid.getTimeGrid.execute(actor, schoolId).required("load school").school

        useCases.schools.rename.execute(actor, schoolId, testSet.schoolName, school.version).required("rename school")
    }

    /** Each time grid change raises the school's version, so the current version is read before every change. */
    private fun createTimeGrid(actor: AccountId, schoolId: SchoolId): Map<String, TimeSlotId> {
        val timeGrid = useCases.timeGrid

        timeGrid.setTeachingDays.execute(actor, schoolId, testSet.teachingDays, schoolVersion(actor, schoolId)).required("set teaching days")

        testSet.slots.forEach { slot ->
            timeGrid.addSlot.execute(actor, schoolId, slot.label, slot.start, slot.end, slot.kind, schoolVersion(actor, schoolId))
                .required("add time slot")
        }

        val slots = timeGrid.getTimeGrid.execute(actor, schoolId).required("load time grid").timeGrid.slots
        return slots.associate { it.label to it.id }
    }

    private fun schoolVersion(actor: AccountId, schoolId: SchoolId): Int =
        useCases.timeGrid.getTimeGrid.execute(actor, schoolId).required("load school").school.version

    private fun createFeatures(actor: AccountId, schoolId: SchoolId): Map<String, RoomFeatureId> =
        testSet.features.associateWith { feature ->
            useCases.rooms.createFeature.execute(actor, schoolId, feature).required("create room feature").id
        }

    private fun createRooms(actor: AccountId, schoolId: SchoolId, featureIds: Map<String, RoomFeatureId>): Map<String, RoomId> =
        testSet.rooms.associate { room ->
            val roomFeatures = setOfNotNull(room.feature?.let(featureIds::getValue))
            val created = useCases.rooms.createRoom.execute(actor, schoolId, room.identifier, roomFeatures).required("create room")

            room.identifier to created.id
        }

    private fun createSubjects(actor: AccountId, schoolId: SchoolId, featureIds: Map<String, RoomFeatureId>): Map<String, SubjectId> =
        testSet.subjects.associate { subject ->
            val requiredFeatures = setOfNotNull(subject.requiredFeature?.let(featureIds::getValue))
            val created = useCases.curriculum.createSubject.execute(actor, schoolId, subject.name, requiredFeatures).required("create subject")

            subject.name to created.id
        }

    /** One class level per grade with its curriculum, then its classes, each with its own home room. */
    private fun createClasses(
        actor: AccountId,
        schoolId: SchoolId,
        subjectIds: Map<String, SubjectId>,
        roomIds: Map<String, RoomId>,
    ): Map<String, SchoolClassId> {
        val curriculum = useCases.curriculum

        return testSet.grades.flatMap { grade ->
            val identifier = "$grade"
            val level = curriculum.createClassLevel.execute(actor, schoolId, identifier).required("create class level")
            val entries = testSet.weeklyPeriods.getValue(grade).map { (subject, periods) -> CurriculumEntry(subjectIds.getValue(subject), periods) }

            curriculum.updateClassLevel.execute(actor, schoolId, level.id, identifier, entries, level.version).required("set curriculum")

            testSet.classes.filter { it.grade == grade }.map { schoolClass ->
                val homeRoom = roomIds.getValue(schoolClass.homeRoom)
                val created = curriculum.createSchoolClass
                    .execute(actor, schoolId, level.id, schoolClass.suffix, testSet.priorityWeight, homeRoom)
                    .required("create class")

                schoolClass.name to created.id
            }
        }.toMap()
    }

    private fun createTeachers(
        actor: AccountId,
        schoolId: SchoolId,
        subjectIds: Map<String, SubjectId>,
        slotIds: Map<String, TimeSlotId>,
    ): Map<String, TeacherId> {
        val teachers = useCases.teachers

        return testSet.teachers.associate { teacher ->
            val created = teachers.create.execute(actor, schoolId, teacher.name, teacher.code, teacher.weeklyLoad).required("create teacher")
            val qualifications = teacher.subjects.map(subjectIds::getValue).toSet()
            var current = teachers.updateQualifications
                .execute(actor, schoolId, created.id, qualifications, created.version)
                .required("set qualifications")

            if (teacher.unavailable.isNotEmpty()) {
                val unavailability = teacher.unavailable.map { Unavailability(it.day, slotIds.getValue(it.slotLabel)) }.toSet()

                current = teachers.updateUnavailability
                    .execute(actor, schoolId, created.id, unavailability, current.version)
                    .required("set unavailability")
            }

            teacher.code to current.id
        }
    }

    private fun createConstraints(actor: AccountId, seeded: SeededTestSchool) {
        testSet.constraints.forEach { constraint ->
            val input = ConstraintInput(constraint.kind, constraint.weight, ruleOf(constraint.rule, seeded))

            useCases.constraints.create.execute(actor, seeded.schoolId, input).required("create constraint")
        }
    }

    /** Translates a rule given by names into the domain rule with the seeded ids. */
    private fun ruleOf(rule: TestRule, seeded: SeededTestSchool): ConstraintRule = when (rule) {
        is TestRule.AllClassesMaxPeriodsPerDay ->
            ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.AllClasses, rule.limit)
        is TestRule.ClassExcludedSlots ->
            ConstraintRule.ClassExcludedSlots(seeded.classIds.getValue(rule.className), rule.slots.map(seeded::daySlot).toSet())
        is TestRule.DoublePeriods ->
            ConstraintRule.SubjectDoublePeriodsInClass(seeded.classIds.getValue(rule.className), seeded.subjectIds.getValue(rule.subject), acrossBreaks = false)
        is TestRule.TeacherExcludedSlots ->
            ConstraintRule.TeacherExcludedSlots(seeded.teacherIds.getValue(rule.teacherCode), rule.slots.map(seeded::daySlot).toSet())
        is TestRule.TeacherMaxPeriodsPerDay ->
            ConstraintRule.TeacherMaxPeriodsPerDay(seeded.teacherIds.getValue(rule.teacherCode), rule.limit)
        is TestRule.TeacherMaxGapsPerDay ->
            ConstraintRule.TeacherMaxGapsPerDay(seeded.teacherIds.getValue(rule.teacherCode), rule.limit)
        is TestRule.SubjectExcludedSlots ->
            ConstraintRule.SubjectExcludedSlots(seeded.subjectIds.getValue(rule.subject), null, rule.slots.map(seeded::daySlot).toSet())
    }

    private fun createFixations(actor: AccountId, seeded: SeededTestSchool) {
        testSet.fixations.forEach { fixation ->
            val classId = seeded.classIds.getValue(fixation.className)
            val subjectId = seeded.subjectIds.getValue(fixation.subject)
            val teacherId = seeded.teacherIds.getValue(fixation.teacherCode)

            useCases.fixations.create.execute(actor, seeded.schoolId, classId, subjectId, teacherId).required("create teacher fixation")
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(TestSchoolSeeder::class.java)
    }
}

/** A failed step stops the seeding; the message names the step and the kind of error, never entered names. */
internal fun <T> UseCaseResult<T>.required(step: String): T = when (this) {
    is UseCaseResult.Success -> value
    is UseCaseResult.Failure -> {
        val details = (error as? ApplicationError.Invalid)?.errors?.joinToString { it.javaClass.simpleName } ?: error.javaClass.simpleName

        throw IllegalStateException("Seeding step '$step' failed: $details")
    }
}
