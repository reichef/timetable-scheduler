package timetablescheduler.application

import timetablescheduler.application.access.DependantRef
import timetablescheduler.application.access.DependantSource
import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.account.LoginUseCase
import timetablescheduler.application.account.PasswordCredentialAuthenticator
import timetablescheduler.application.error.DependantType
import timetablescheduler.application.port.PasswordCredentialRepository
import timetablescheduler.application.port.PasswordHasher
import timetablescheduler.application.port.RoomFeatureRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.application.port.SchoolRepository
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
import timetablescheduler.domain.common.RoomFeatureId

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
