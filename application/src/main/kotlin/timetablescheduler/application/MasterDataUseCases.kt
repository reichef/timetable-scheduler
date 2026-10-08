package timetablescheduler.application

import timetablescheduler.application.access.DependantRef
import timetablescheduler.application.access.DependantSource
import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.account.AccountUseCases
import timetablescheduler.application.account.ActivateAccount
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
import timetablescheduler.application.port.PasswordCredentialRepository
import timetablescheduler.application.port.PasswordHasher
import timetablescheduler.application.port.RoomFeatureRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.application.port.SchoolClassRepository
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.SubjectRepository
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
import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.RoomFeatureId
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
)

/** Wires every use case from the ports in one place, so production and tests use identical wiring. */
class MasterDataUseCases(ports: MasterDataPorts) {
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

    /** US3 adds teacher qualifications as a further source. */
    private val subjectDependantSources: List<DependantSource<SubjectId>> = with(ports) {
        listOf(
            DependantSource { subjectId ->
                classLevels.findUsingSubject(subjectId).map { DependantRef(DependantType.CLASS_LEVEL, it.identifier, setOf(it.schoolId)) }
            },
        )
    }

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
            deleteRoom = DeleteRoom(transactions, access, rooms),
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
            proposeClassSuffix = ProposeClassSuffix(transactions, access, classLevels, schoolClasses),
            createSchoolClass = CreateSchoolClass(transactions, access, classLevels, schoolClasses),
            updateSchoolClass = UpdateSchoolClass(transactions, access, classLevels, schoolClasses),
            deleteSchoolClass = DeleteSchoolClass(transactions, access, classLevels, schoolClasses),
            getSchoolClass = GetSchoolClass(transactions, access, classLevels, schoolClasses, subjects),
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
            deleteSlot = DeleteTimeSlot(transactions, access, schools, timeSlotUsage),
        )
    }
}
