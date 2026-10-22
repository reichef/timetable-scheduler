package timetablescheduler.application

import timetablescheduler.application.access.DependantRef
import timetablescheduler.application.access.DependantSource
import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.account.AccountUseCases
import timetablescheduler.application.account.ActivateAccount
import timetablescheduler.application.account.ChangeOwnLanguage
import timetablescheduler.application.account.ChangeOwnPassword
import timetablescheduler.application.account.CreateAccount
import timetablescheduler.application.account.DeactivateAccount
import timetablescheduler.application.account.GetAccount
import timetablescheduler.application.account.ListAccounts
import timetablescheduler.application.account.LoginUseCase
import timetablescheduler.application.account.ResetPassword
import timetablescheduler.application.account.UpdateAccountRole
import timetablescheduler.application.account.PasswordCredentialAuthenticator
import timetablescheduler.application.error.DependantType
import timetablescheduler.application.curriculum.CreateClassLevel
import timetablescheduler.application.curriculum.CreateSchoolClass
import timetablescheduler.application.curriculum.CreateSubject
import timetablescheduler.application.curriculum.CurriculumUseCases
import timetablescheduler.application.curriculum.DeleteClassLevel
import timetablescheduler.application.curriculum.DeleteSchoolClass
import timetablescheduler.application.curriculum.DeleteSubject
import timetablescheduler.application.curriculum.GetClassLevel
import timetablescheduler.application.curriculum.GetSchoolClass
import timetablescheduler.application.curriculum.GetSubject
import timetablescheduler.application.curriculum.ListClassLevels
import timetablescheduler.application.curriculum.ListSubjects
import timetablescheduler.application.curriculum.ProposeClassSuffix
import timetablescheduler.application.curriculum.UpdateClassLevel
import timetablescheduler.application.curriculum.UpdateSchoolClass
import timetablescheduler.application.curriculum.UpdateSubject
import timetablescheduler.application.port.ClassLevelRepository
import timetablescheduler.application.readiness.GetSchoolReadiness
import timetablescheduler.application.readiness.ReadinessReportLoader
import timetablescheduler.application.port.GenerationEventLog
import timetablescheduler.application.port.GenerationExecutor
import timetablescheduler.application.port.GenerationRepository
import timetablescheduler.application.port.TimetableSolver
import timetablescheduler.application.port.ChosenTimetableRepository
import timetablescheduler.application.port.SchedulingConstraintRepository
import timetablescheduler.application.port.TeacherFixationRepository
import timetablescheduler.application.scheduling.ChooseAlternative
import timetablescheduler.application.scheduling.CompareAlternatives
import timetablescheduler.application.scheduling.ConstraintContextLoader
import timetablescheduler.application.scheduling.GetChosenTimetable
import timetablescheduler.application.scheduling.CreateConstraint
import timetablescheduler.application.scheduling.DeleteConstraint
import timetablescheduler.application.scheduling.DeleteGeneration
import timetablescheduler.application.scheduling.GenerationControl
import timetablescheduler.application.scheduling.GetConstraint
import timetablescheduler.application.scheduling.ListConstraints
import timetablescheduler.application.scheduling.SchedulingConstraintUseCases
import timetablescheduler.application.scheduling.SetConstraintActive
import timetablescheduler.application.scheduling.TeacherFixationUseCases
import timetablescheduler.application.scheduling.UpdateConstraint
import timetablescheduler.application.scheduling.GenerationRunner
import timetablescheduler.application.scheduling.GetAlternative
import timetablescheduler.application.scheduling.GetGeneration
import timetablescheduler.application.scheduling.ListGenerations
import timetablescheduler.application.scheduling.RecoverInterruptedGenerations
import timetablescheduler.application.scheduling.SchedulingProblemBuilder
import timetablescheduler.application.scheduling.SchedulingUseCases
import timetablescheduler.application.scheduling.StartGeneration
import timetablescheduler.application.scheduling.StopGeneration
import timetablescheduler.application.scheduling.TimetableNamesLoader
import java.time.Clock
import timetablescheduler.application.port.PasswordCredentialRepository
import timetablescheduler.application.port.PasswordHasher
import timetablescheduler.application.port.RoomFeatureRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.application.port.SchoolClassRepository
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.SubjectRepository
import timetablescheduler.application.port.TeacherRepository
import timetablescheduler.application.port.TimeSlotUsage
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.application.room.CreateRoom
import timetablescheduler.application.room.CreateRoomFeature
import timetablescheduler.application.room.DeleteRoom
import timetablescheduler.application.room.DeleteRoomFeature
import timetablescheduler.application.room.GetRoom
import timetablescheduler.application.room.ListRooms
import timetablescheduler.application.room.RoomUseCases
import timetablescheduler.application.room.UpdateRoom
import timetablescheduler.application.school.AddTimeSlot
import timetablescheduler.application.school.DeleteTimeSlot
import timetablescheduler.application.school.GetSchoolDashboard
import timetablescheduler.application.school.GetTimeGrid
import timetablescheduler.application.school.ListAccessibleSchools
import timetablescheduler.application.school.PreviewTimeSlotDeletion
import timetablescheduler.application.school.RenameSchool
import timetablescheduler.application.school.SchoolUseCases
import timetablescheduler.application.school.SetSlotExclusions
import timetablescheduler.application.school.SetTeachingDays
import timetablescheduler.application.school.TimeGridUseCases
import timetablescheduler.application.school.UpdateTimeSlot
import timetablescheduler.application.teacher.CreateTeacher
import timetablescheduler.application.teacher.DeleteTeacher
import timetablescheduler.application.teacher.GetTeacher
import timetablescheduler.application.teacher.ListTeachers
import timetablescheduler.application.teacher.TeacherUseCases
import timetablescheduler.application.teacher.UpdateQualifications
import timetablescheduler.application.teacher.UpdateTeacher
import timetablescheduler.application.teacher.UpdateUnavailability
import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.constraint.type
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.common.SubjectId

/** All ports the use cases need; implemented by the persistence adapter or by in-memory fakes in tests. */
class MasterDataPorts(
    val transactions: TransactionRunner,
    val schools: SchoolRepository,
    val accounts: UserAccountRepository,
    val credentials: PasswordCredentialRepository,
    val hasher: PasswordHasher,
    val rooms: RoomRepository,
    val roomFeatures: RoomFeatureRepository,
    val timeSlotUsage: TimeSlotUsage,
    val subjects: SubjectRepository,
    val classLevels: ClassLevelRepository,
    val schoolClasses: SchoolClassRepository,
    val teachers: TeacherRepository,
)

/** The ports of feature 002 (timetable generation); implemented by the persistence and solver adapters or by fakes. */
class SchedulingPorts(
    val constraints: SchedulingConstraintRepository,
    val fixations: TeacherFixationRepository,
    val generations: GenerationRepository,
    val chosenTimetables: ChosenTimetableRepository,
    val solver: TimetableSolver,
    val executor: GenerationExecutor,
    val eventLog: GenerationEventLog,
    val clock: Clock,
)

/** Wires every use case from the ports in one place, so production and tests use identical wiring. */
class MasterDataUseCases(private val ports: MasterDataPorts, private val schedulingPorts: SchedulingPorts) {
    val access = SchoolAccessPolicy(ports.accounts)

    val login = LoginUseCase(
        ports.transactions,
        PasswordCredentialAuthenticator(ports.accounts, ports.credentials, ports.hasher),
        ports.accounts,
    )

    private val roomFeatureDependantSources: List<DependantSource<RoomFeatureId>> = with(ports) {
        listOf(
            DependantSource { featureId ->
                rooms.findUsingFeature(featureId).map { DependantRef(DependantType.ROOM, it.identifier, setOf(it.schoolId)) }
            },
            DependantSource { featureId ->
                subjects.findRequiringFeature(featureId).map { DependantRef(DependantType.SUBJECT, it.name, setOf(it.schoolId)) }
            },
        )
    }

    private val subjectDependantSources: List<DependantSource<SubjectId>> = with(ports) {
        listOf(
            DependantSource { subjectId ->
                classLevels.findUsingSubject(subjectId).map { DependantRef(DependantType.CLASS_LEVEL, it.identifier, setOf(it.schoolId)) }
            },
            DependantSource { subjectId ->
                teachers.findQualifiedFor(subjectId).map { DependantRef(DependantType.TEACHER, it.displayName, setOf(it.schoolId)) }
            },
            DependantSource { subjectId ->
                constraintDependants(schedulingPorts.constraints.findReferencingSubject(subjectId), subjects.findById(subjectId)?.name.orEmpty())
            },
            DependantSource { subjectId -> fixationDependants(schedulingPorts.fixations.findBySubject(subjectId)) },
            DependantSource { subjectId -> chosenTimetableUsing(subjects.findById(subjectId)?.schoolId) { it.subjectId == subjectId } },
        )
    }

    private val roomDependantSources: List<DependantSource<RoomId>> = with(ports) {
        listOf(
            DependantSource { roomId -> chosenTimetableUsing(rooms.findById(roomId)?.schoolId) { it.roomId == roomId } },
            DependantSource { roomId ->
                val schoolClass = schoolClasses.findByHomeRoom(roomId) ?: return@DependantSource emptyList()
                val level = classLevels.findById(schoolClass.classLevelId) ?: return@DependantSource emptyList()
                listOf(DependantRef(DependantType.SCHOOL_CLASS, schoolClass.displayName(level), setOf(level.schoolId)))
            },
        )
    }

    /** Constraints and fixations referring to an entry block its deletion (002 FR-016). */
    private fun constraintDependants(constraints: List<SchedulingConstraint>, name: String) =
        constraints.map { DependantRef(DependantType.SCHEDULING_CONSTRAINT, name, setOf(it.schoolId), it.rule.type) }

    private fun fixationDependants(fixations: List<TeacherFixation>): List<DependantRef> =
        fixations.map { fixation ->
            val schoolClass = ports.schoolClasses.findById(fixation.classId)
            val className = schoolClass?.let { ports.classLevels.findById(it.classLevelId)?.let(schoolClass::displayName) }.orEmpty()
            val subject = ports.subjects.findById(fixation.subjectId)?.name.orEmpty()
            val teacher = ports.teachers.findById(fixation.teacherId)?.shortCode.orEmpty()
            DependantRef(DependantType.TEACHER_FIXATION, "$className $subject – $teacher", setOf(fixation.schoolId))
        }

    /** The chosen timetable keeps references to the master data it uses (G7); it blocks their deletion (002 FR-016). */
    private fun chosenTimetableUsing(schoolId: SchoolId?, uses: (LessonAssignment) -> Boolean): List<DependantRef> {
        if (schoolId == null) return emptyList()
        val chosen = schedulingPorts.chosenTimetables.find(schoolId) ?: return emptyList()
        return if (chosen.lessons.any(uses)) listOf(DependantRef(DependantType.CHOSEN_TIMETABLE, "", setOf(schoolId))) else emptyList()
    }

    private fun schoolOfClass(classId: SchoolClassId): SchoolId? =
        ports.schoolClasses.findById(classId)?.let { ports.classLevels.findById(it.classLevelId)?.schoolId }

    private val schoolClassDependantSources: List<DependantSource<SchoolClassId>> = with(ports) {
        listOf(
            DependantSource { classId -> chosenTimetableUsing(schoolOfClass(classId)) { it.classId == classId } },
            DependantSource { classId ->
                val schoolClass = schoolClasses.findById(classId) ?: return@DependantSource emptyList()
                val name = classLevels.findById(schoolClass.classLevelId)?.let(schoolClass::displayName).orEmpty()
                constraintDependants(schedulingPorts.constraints.findReferencingClass(classId), name)
            },
            DependantSource { classId -> fixationDependants(schedulingPorts.fixations.findByClass(classId)) },
        )
    }

    private val teacherDependantSources: List<DependantSource<TeacherId>> = with(ports) {
        listOf(
            DependantSource { teacherId -> chosenTimetableUsing(teachers.findById(teacherId)?.schoolId) { it.teacherId == teacherId } },
            DependantSource { teacherId ->
                val name = teachers.findById(teacherId)?.displayName.orEmpty()
                constraintDependants(schedulingPorts.constraints.findReferencingTeacher(teacherId), name)
            },
            DependantSource { teacherId -> fixationDependants(schedulingPorts.fixations.findByTeacher(teacherId)) },
        )
    }

    private val timeSlotDependantSources: List<DependantSource<TimeSlotId>> = listOf(
        DependantSource { slotId ->
            ports.schools.findAllIds().flatMap { schoolId -> chosenTimetableUsing(schoolId) { it.daySlot.slotId == slotId } }
        },
        DependantSource { slotId ->
            schedulingPorts.constraints.findReferencingSlot(slotId).map { constraint ->
                val label = ports.schools.findTimeGrid(constraint.schoolId).slotById(slotId)?.label.orEmpty()
                DependantRef(DependantType.SCHEDULING_CONSTRAINT, label, setOf(constraint.schoolId), constraint.rule.type)
            }
        },
    )

    private val classLevelDependantSources: List<DependantSource<ClassLevelId>> = with(ports) {
        listOf(
            DependantSource { levelId ->
                val level = classLevels.findById(levelId) ?: return@DependantSource emptyList()
                schoolClasses.findByLevel(levelId).map { DependantRef(DependantType.SCHOOL_CLASS, it.displayName(level), setOf(level.schoolId)) }
            },
        )
    }

    val accounts = with(ports) {
        AccountUseCases(
            list = ListAccounts(transactions, access, accounts),
            get = GetAccount(transactions, access, accounts),
            create = CreateAccount(transactions, access, accounts, credentials, hasher),
            updateRole = UpdateAccountRole(transactions, access, accounts),
            deactivate = DeactivateAccount(transactions, access, accounts),
            activate = ActivateAccount(transactions, access, accounts),
            resetPassword = ResetPassword(transactions, access, accounts, credentials, hasher),
            changeOwnPassword = ChangeOwnPassword(transactions, accounts, credentials, hasher),
            changeOwnLanguage = ChangeOwnLanguage(transactions, accounts),
        )
    }

    val schools = with(ports) {
        SchoolUseCases(
            list = ListAccessibleSchools(transactions, access, schools),
            rename = RenameSchool(transactions, access, schools),
            dashboard = GetSchoolDashboard(transactions, access, schools, rooms, roomFeatures),
        )
    }

    val rooms = with(ports) {
        RoomUseCases(
            createFeature = CreateRoomFeature(transactions, access, roomFeatures),
            deleteFeature = DeleteRoomFeature(transactions, access, roomFeatures, roomFeatureDependantSources),
            createRoom = CreateRoom(transactions, access, rooms, roomFeatures),
            updateRoom = UpdateRoom(transactions, access, rooms, roomFeatures),
            deleteRoom = DeleteRoom(transactions, access, rooms, roomDependantSources),
            listRooms = ListRooms(transactions, access, rooms, roomFeatures),
            getRoom = GetRoom(transactions, access, rooms, roomFeatures),
        )
    }

    val curriculum = with(ports) {
        CurriculumUseCases(
            createSubject = CreateSubject(transactions, access, subjects, roomFeatures),
            updateSubject = UpdateSubject(transactions, access, subjects, roomFeatures),
            deleteSubject = DeleteSubject(transactions, access, subjects, subjectDependantSources),
            listSubjects = ListSubjects(transactions, access, subjects, roomFeatures),
            getSubject = GetSubject(transactions, access, subjects, roomFeatures),
            createClassLevel = CreateClassLevel(transactions, access, classLevels),
            updateClassLevel = UpdateClassLevel(transactions, access, schools, classLevels, subjects),
            deleteClassLevel = DeleteClassLevel(transactions, access, classLevels, classLevelDependantSources),
            listClassLevels = ListClassLevels(transactions, access, schools, classLevels, schoolClasses),
            getClassLevel = GetClassLevel(transactions, access, schools, classLevels, schoolClasses, subjects),
            proposeClassSuffix = ProposeClassSuffix(transactions, access, classLevels, schoolClasses, rooms),
            createSchoolClass = CreateSchoolClass(transactions, access, classLevels, schoolClasses, rooms),
            updateSchoolClass = UpdateSchoolClass(transactions, access, classLevels, schoolClasses, rooms),
            deleteSchoolClass = DeleteSchoolClass(transactions, access, classLevels, schoolClasses, schoolClassDependantSources),
            getSchoolClass = GetSchoolClass(transactions, access, classLevels, schoolClasses, subjects, rooms),
        )
    }

    val teachers = with(ports) {
        TeacherUseCases(
            create = CreateTeacher(transactions, access, schools, teachers),
            update = UpdateTeacher(transactions, access, schools, teachers),
            updateQualifications = UpdateQualifications(transactions, access, subjects, teachers),
            updateUnavailability = UpdateUnavailability(transactions, access, schools, teachers),
            delete = DeleteTeacher(transactions, access, teachers, teacherDependantSources),
            list = ListTeachers(transactions, access, teachers),
            get = GetTeacher(transactions, access, schools, subjects, teachers),
        )
    }

    private val readinessReports = with(ports) {
        ReadinessReportLoader(schools, rooms, roomFeatures, subjects, classLevels, schoolClasses, teachers, schedulingPorts.fixations)
    }

    val readiness = GetSchoolReadiness(ports.transactions, access, readinessReports)

    private val timetableNames = with(ports) { TimetableNamesLoader(schools, rooms, subjects, classLevels, schoolClasses, teachers) }

    private val constraintContext = with(ports) {
        ConstraintContextLoader(
            SchedulingProblemBuilder(schools, rooms, subjects, classLevels, schoolClasses, teachers),
            schedulingPorts.constraints,
            schedulingPorts.fixations,
        )
    }

    val constraints = with(ports) {
        SchedulingConstraintUseCases(
            list = ListConstraints(transactions, access, constraintContext, timetableNames),
            get = GetConstraint(transactions, access, schedulingPorts.constraints),
            create = CreateConstraint(transactions, access, schedulingPorts.constraints, constraintContext),
            update = UpdateConstraint(transactions, access, schedulingPorts.constraints, constraintContext),
            setActive = SetConstraintActive(transactions, access, schedulingPorts.constraints),
            delete = DeleteConstraint(transactions, access, schedulingPorts.constraints),
        )
    }

    val fixations = TeacherFixationUseCases(ports.transactions, access, schedulingPorts.fixations, constraintContext)

    val scheduling = with(ports) {
        val control = GenerationControl()
        val runner = GenerationRunner(transactions, schedulingPorts.generations, schedulingPorts.solver, control, schedulingPorts.eventLog, schedulingPorts.clock)
        val names = timetableNames
        SchedulingUseCases(
            start = StartGeneration(
                transactions, access, readinessReports, constraintContext, names, schedulingPorts.generations, schedulingPorts.chosenTimetables,
                schedulingPorts.executor, runner, schedulingPorts.clock,
            ),
            stop = StopGeneration(transactions, access, schedulingPorts.generations, control),
            list = ListGenerations(transactions, access, schedulingPorts.generations, accounts, readinessReports, schedulingPorts.constraints, names, schedulingPorts.chosenTimetables, constraintContext),
            get = GetGeneration(transactions, access, schedulingPorts.generations, accounts, names, constraintContext),
            getAlternative = GetAlternative(transactions, access, schedulingPorts.generations, names, schedulingPorts.chosenTimetables, constraintContext),
            recoverInterrupted = RecoverInterruptedGenerations(transactions, schedulingPorts.generations, schedulingPorts.clock),
            choose = ChooseAlternative(transactions, access, schedulingPorts.generations, schedulingPorts.chosenTimetables, constraintContext, names, schedulingPorts.clock),
            getChosen = GetChosenTimetable(transactions, access, schedulingPorts.chosenTimetables, constraintContext, names, schedulingPorts.generations, accounts),
            compare = CompareAlternatives(transactions, access, schedulingPorts.generations, names),
            delete = DeleteGeneration(transactions, access, schedulingPorts.generations, schedulingPorts.chosenTimetables),
        )
    }

    val timeGrid = with(ports) {
        TimeGridUseCases(
            getTimeGrid = GetTimeGrid(transactions, access, schools),
            setTeachingDays = SetTeachingDays(transactions, access, schools),
            addSlot = AddTimeSlot(transactions, access, schools),
            updateSlot = UpdateTimeSlot(transactions, access, schools),
            setExclusions = SetSlotExclusions(transactions, access, schools),
            previewDeletion = PreviewTimeSlotDeletion(transactions, access, schools, timeSlotUsage),
            deleteSlot = DeleteTimeSlot(transactions, access, schools, timeSlotUsage, timeSlotDependantSources),
        )
    }
}
