package timetablescheduler.domain.common

import java.util.UUID

@JvmInline
value class SchoolId(val value: UUID) {
    companion object {
        fun new() = SchoolId(UUID.randomUUID())
    }
}

@JvmInline
value class RoomId(val value: UUID) {
    companion object {
        fun new() = RoomId(UUID.randomUUID())
    }
}

@JvmInline
value class RoomFeatureId(val value: UUID) {
    companion object {
        fun new() = RoomFeatureId(UUID.randomUUID())
    }
}

@JvmInline
value class TimeSlotId(val value: UUID) {
    companion object {
        fun new() = TimeSlotId(UUID.randomUUID())
    }
}

@JvmInline
value class SubjectId(val value: UUID) {
    companion object {
        fun new() = SubjectId(UUID.randomUUID())
    }
}

@JvmInline
value class ClassLevelId(val value: UUID) {
    companion object {
        fun new() = ClassLevelId(UUID.randomUUID())
    }
}

@JvmInline
value class SchoolClassId(val value: UUID) {
    companion object {
        fun new() = SchoolClassId(UUID.randomUUID())
    }
}

@JvmInline
value class TeacherId(val value: UUID) {
    companion object {
        fun new() = TeacherId(UUID.randomUUID())
    }
}

@JvmInline
value class AccountId(val value: UUID) {
    companion object {
        fun new() = AccountId(UUID.randomUUID())
    }
}
