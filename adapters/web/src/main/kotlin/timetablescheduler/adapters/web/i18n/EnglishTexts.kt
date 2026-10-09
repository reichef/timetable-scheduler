package timetablescheduler.adapters.web.i18n

import timetablescheduler.application.error.DependantType
import timetablescheduler.domain.account.InterfaceLanguage
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** English interface texts (US6); school terms follow common English usage (teaching load, periods per week). */
object EnglishTexts : UiTexts {
    override val language = InterfaceLanguage.ENGLISH

    override val common = object : CommonTexts {
        override val appTitle = "Timetable Planner"
        override val start = "Home"
        override val save = "Save"
        override val create = "Create"
        override val add = "Add"
        override val edit = "Edit"
        override val delete = "Delete"
        override val cancel = "Cancel"
        override val back = "Go back"
        override val name = "Name"
        override val identifier = "Identifier"
        override val toSchool = "Back to the school"
        override val navSchool = "School"
        override val navAccounts = "Accounts"
        override val navChangePassword = "Change password"
        override val logout = "Log out"
        override val noticeTitle = "Notice"
        override val errorTitle = "Error"
        override val roomFeatures = "Room features"

        override fun dayName(day: DayOfWeek) = when (day) {
            DayOfWeek.MONDAY -> "Monday"
            DayOfWeek.TUESDAY -> "Tuesday"
            DayOfWeek.WEDNESDAY -> "Wednesday"
            DayOfWeek.THURSDAY -> "Thursday"
            DayOfWeek.FRIDAY -> "Friday"
            DayOfWeek.SATURDAY -> "Saturday"
            DayOfWeek.SUNDAY -> "Sunday"
        }

        override fun roleName(role: Role) = when (role) {
            Role.PLANNER -> "Planner"
            Role.TEACHER -> "Teacher"
        }
    }

    override val auth = object : AuthTexts {
        override val loginTitle = "Log in"
        override val loginFailed = "Username or password is wrong."
        override val username = "Username"
        override val password = "Password"
        override val teacherHome = "There is no view for teachers yet."
        override val plannerLinkToSchool = "Planners: go to the school"
    }

    override val errors = object : ErrorTexts {
        override val unexpectedError = "An unexpected error occurred. Please try again."
        override val correlationId = "Error reference"
        override val notFound = "The entry was not found."
        override val accessDenied = "You are not allowed to open this page."
        override val versionConflict = "The entry has been changed by someone else in the meantime."
        override val reload = "Reload"
        override val conflictTitle = "Conflict"
        override val deletionBlockedTitle = "Cannot delete"
        override val deletionBlocked = "The entry cannot be deleted because it is still used by:"
        override val invalidInputTitle = "Invalid input"
        override val invalidInput = "Please correct your input:"

        override fun hiddenDependants(count: Int) = "and $count entries of other schools"

        private val fieldLabels = mapOf(
            "name" to "Name",
            "username" to "Username",
            "role" to "Role",
            "password" to "Password",
            "identifier" to "Identifier",
            "label" to "Label",
            "suffix" to "Class suffix",
            "priorityWeight" to "Weight",
            "periodsPerWeek" to "Periods per week",
            "shortCode" to "Short code",
            "weeklyLoad" to "Teaching load",
            "startTime" to "Start",
            "endTime" to "End",
            "kind" to "Type",
            "features" to "Room features",
            "subjects" to "Subjects",
            "dayOfWeek" to "Weekday",
            "slot" to "Time slot",
            "timeLimitMinutes" to "Time limit",
            "homeRoomId" to "Home room",
        )

        private fun label(field: String) = fieldLabels[field] ?: field

        override fun describe(error: ValidationError) = when (error) {
            is ValidationError.BlankValue -> "${label(error.field)} must not be empty."
            is ValidationError.TooLong -> "${label(error.field)} must not be longer than ${error.maxLength} characters."
            is ValidationError.OutOfRange ->
                if (error.max == null) "${label(error.field)} must be at least ${error.min}."
                else "${label(error.field)} must be between ${error.min} and ${error.max}."
            is ValidationError.InvalidFormat -> "${label(error.field)} has an invalid format."
            is ValidationError.DuplicateValue -> "${label(error.field)} is already in use."
            is ValidationError.InvalidReference -> "${label(error.field)}: invalid selection."
            ValidationError.Overlap -> "The time slot overlaps an existing time slot."
            ValidationError.EndNotAfterStart -> "The end must be after the start."
            ValidationError.LastSchoolRemoved -> "At least one school must be assigned."
            is ValidationError.NoActivePlannerLeft -> "At least one active planner must remain."
            ValidationError.IncorrectCurrentPassword -> "The current password is wrong."
            is ValidationError.HomeRoomTaken -> "The room is already the home room of ${error.otherClassName}."
            is ValidationError.ConfirmationMissing -> "Please confirm the notes."
        }

        override fun describe(type: DependantType) = when (type) {
            DependantType.ROOM -> "Room"
            DependantType.ROOM_FEATURE -> "Room feature"
            DependantType.SUBJECT -> "Subject"
            DependantType.CLASS_LEVEL -> "Class level"
            DependantType.SCHOOL_CLASS -> "Class"
            DependantType.TIME_SLOT -> "Time slot"
            DependantType.TEACHER -> "Teacher"
        }
    }

    override val languageSwitch = object : LanguageSwitchTexts {
        override val label = "Language"
        override val unsavedWarning = "Unsaved input will be lost when you switch the language. Switch anyway?"
    }

    override val school = object : SchoolTexts {
        override val rename = "Rename"
        override val subjectsLink = "Subjects"
        override val classLevelsLink = "Class levels and classes"
        override val teachersLink = "Teachers"
        override val readinessLink = "Readiness for scheduling"
        override val generationsLink = "Generations"
        override fun featuresLink(count: Int) = "Room features ($count)"
        override fun roomsLink(count: Int) = "Rooms ($count)"
        override fun timeGridLink(teachingSlotsPerWeek: Int) = "Time grid ($teachingSlotsPerWeek teaching periods per week)"
        override fun readinessSummary(issueCount: Int) = when (issueCount) {
            0 -> ": no issues"
            1 -> ": 1 issue"
            else -> ": $issueCount issues"
        }
    }

    override val rooms = object : RoomTexts {
        override val roomsTitle = "Rooms"
        override val roomColumn = "Room"
        override val createRoom = "Add room"
        override val manageFeatures = "Manage room features"
        override val deleteRoom = "Delete room"
        override val toRoomList = "Back to the rooms"
        override val createFeature = "Add room feature"
        override val toRooms = "To the rooms"
        override fun roomTitle(identifier: String) = "Room $identifier"
    }

    override val timeGrid = object : TimeGridTexts {
        override val addSlot = "Add time slot"
        override val teachingDays = "Teaching days"
        override val saveTeachingDays = "Save teaching days"
        override val slotColumn = "Time slot"
        override val timeColumn = "Time"
        override val kindColumn = "Type"
        override val actionsColumn = "Actions"
        override val label = "Label"
        override val start = "Start"
        override val end = "End"
        override val kind = "Type"
        override val daysWithoutSlot = "Days without this time slot"
        override val notHeldOn = "Not held on"
        override val deleteSlot = "Delete time slot"
        override val toTimeGrid = "Back to the time grid"
        override val deleteSlotTitle = "Delete time slot"
        override val deletePermanently = "Delete permanently"
        override fun title(schoolName: String) = "Time grid – $schoolName"
        override fun teachingSlotsPerWeek(count: Int) = "Teaching periods per week: $count"
        override fun slotKindName(kind: SlotKind) = when (kind) {
            SlotKind.TEACHING -> "Lesson"
            SlotKind.BREAK -> "Break"
        }
        override fun slotTitle(label: String) = "Time slot $label"
        override fun deleteQuestion(label: String, start: LocalTime, end: LocalTime) = "Really delete time slot “$label” ($start–$end)?"
        override fun deleteRemovals(exclusionCount: Int, unavailabilityCount: Int) =
            "This removes $exclusionCount day exclusion(s) and $unavailabilityCount teacher unavailabilities."
    }

    override val subjects = object : SubjectTexts {
        override val title = "Subjects"
        override val subjectColumn = "Subject"
        override val requiredFeaturesColumn = "Required room features"
        override val createSubject = "Add subject"
        override val deleteSubject = "Delete subject"
        override val toSubjects = "Back to the subjects"
        override fun subjectTitle(name: String) = "Subject $name"
    }

    override val classLevels = object : ClassLevelTexts {
        override val title = "Class levels"
        override val levelColumn = "Level"
        override val weeklyPeriodsColumn = "Periods per week"
        override val classesColumn = "Classes"
        override val createLevel = "Add class level"
        override val levelIdentifierLabel = "Name (e.g. 6)"
        override val curriculum = "Curriculum"
        override val subjectColumn = "Subject"
        override val periodsInputColumn = "Periods per week (empty = not taught)"
        override val createClass = "Add class"
        override val deleteLevel = "Delete class level"
        override val toLevels = "Back to the class levels"
        override val suffixLabel = "Class suffix"
        override val weightLabel = "Weight (1–100)"
        override val toLevel = "Back to the class level"
        override val editClass = "Edit class"
        override val deleteClass = "Delete class"
        override val homeRoomLabel = "Home room"
        override val noHomeRoom = "no home room"
        override fun gridOffers(teachingSlotsPerWeek: Int) = "The time grid offers $teachingSlotsPerWeek teaching periods per week."
        override fun levelTitle(identifier: String) = "Class level $identifier"
        override fun gridWarning(weeklyPeriods: Int, teachingSlotsPerWeek: Int, excessPeriods: Int) =
            "The curriculum does not fit into the time grid: $weeklyPeriods periods per week, but only $teachingSlotsPerWeek " +
                "teaching periods per week ($excessPeriods too many). It has been saved anyway."
        override fun levelSum(weeklyPeriods: Int, teachingSlotsPerWeek: Int) =
            "Total: $weeklyPeriods periods per week (time grid: $teachingSlotsPerWeek teaching periods per week)"
        override fun newClassTitle(levelIdentifier: String) = "New class in level $levelIdentifier"
        override fun classTitle(displayName: String) = "Class $displayName"
        override fun curriculumFromLevel(levelIdentifier: String) = "Curriculum (from level $levelIdentifier)"
        override fun classSum(weeklyPeriods: Int) = "Total: $weeklyPeriods periods per week"
    }

    override val teachers = object : TeacherTexts {
        override val title = "Teachers"
        override val shortCodeColumn = "Short code"
        override val loadColumn = "Teaching load"
        override val createTeacher = "Add teacher"
        override val shortCodeLabel = "Short code (2–8 capital letters or digits)"
        override val general = "General"
        override val qualifications = "Qualifications"
        override val subjectsLegend = "Subjects"
        override val noSubjects = "No subjects have been created yet."
        override val saveQualifications = "Save qualifications"
        override val unavailabilityTitle = "Unavailability (checked = not available)"
        override val noTeachingSlots = "The time grid has no teaching periods yet."
        override val slotColumn = "Time slot"
        override val saveUnavailability = "Save unavailability"
        override val deleteTeacher = "Delete teacher"
        override val toTeachers = "Back to the teachers"
        override fun loadLabel(teachingSlotsPerWeek: Int) = "Teaching load (at most $teachingSlotsPerWeek periods per week)"
        override fun teacherTitle(displayName: String) = "Teacher $displayName"
    }

    override val accounts = object : AccountTexts {
        override val title = "Accounts"
        override val roleColumn = "Role"
        override val statusColumn = "Status"
        override val role = "Role"
        override val createAccount = "Add account"
        override val initialPassword = "Initial password (at least 10 characters)"
        override val deactivateHeading = "Deactivate"
        override val activateHeading = "Activate"
        override val deactivateButton = "Deactivate account"
        override val activateButton = "Activate account"
        override val resetPassword = "Reset password"
        override val newPassword = "New password (at least 10 characters)"
        override val resetButton = "Reset"
        override val toAccounts = "Back to the accounts"
        override val ownPasswordTitle = "Change password"
        override val passwordChanged = "The password has been changed."
        override val currentPassword = "Current password"
        override val changeButton = "Change"
        override fun accountTitle(username: String) = "Account $username"
        override fun stateName(active: Boolean) = if (active) "active" else "deactivated"
        override fun status(active: Boolean) = "Status: ${stateName(active)}"
    }

    override val readiness = object : ReadinessTexts {
        override val title = "Readiness for scheduling"
        override val noIssues = "No blocking issues found."
        override val noRoomOffersAll = ": no room offers all required room features together"
        override fun heading(issue: ReadinessIssue) = when (issue) {
            is ReadinessIssue.SubjectNoSuitableRoom -> "Subjects without a suitable room"
            is ReadinessIssue.SubjectNoQualifiedTeacher -> "Subjects without a qualified teacher"
            is ReadinessIssue.SubjectTeacherCapacityShortfall -> "Subjects with too few teacher periods"
            is ReadinessIssue.LevelExceedsGrid -> "Class levels exceeding the time grid"
            is ReadinessIssue.TeacherWithoutQualification -> "Teachers without qualification"
            is ReadinessIssue.TeacherZeroLoad -> "Teachers without teaching load"
            is ReadinessIssue.TeacherLoadExceedsGrid -> "Teachers with a teaching load above the time grid"
        }
        override fun missingFeatures(featureNames: List<String>) = " – missing room features: ${featureNames.joinToString(", ")}"
        override fun missingPeriods(periods: Int) = ": $periods periods per week missing"
        override fun excessPeriods(periods: Int) = ": $periods periods per week too many"
        override fun levelName(identifier: String) = "Level $identifier"
    }

    override val generations = object : GenerationTexts {
        override val title = "Generations"
        override val startHeading = "Start a new generation"
        override val timeLimitLabel = "Time limit in minutes (1–60)"
        override val startButton = "Start generation"
        override val warningsIntro = "Notes from the readiness check:"
        override val warningsConfirm = "I have read the notes and want to start anyway."
        override val blockedTitle = "Cannot start"
        override val blockedIntro = "These problems must be fixed before scheduling:"
        override val toReadiness = "To the readiness overview"
        override val runningTitle = "Generation already running"
        override val runningExists = "A generation is already running for the school. Wait until it ends or stop it."
        override val toRunning = "To the running generation"
        override val startedColumn = "Started"
        override val startedByColumn = "By"
        override val statusColumn = "Status"
        override val alternativesColumn = "Alternatives"
        override val noGenerations = "No generations yet."
        override val stopButton = "Stop"
        override val alternativesHeading = "Equally optimal alternatives"
        override val noAlternatives = "No alternatives found."
        override val unplacedColumn = "Not placed"
        override val softViolationsColumn = "Soft violations"
        override val openAlternative = "Open"
        override val toGenerations = "To the generations"
        override val infeasibleHint = "No valid timetable could be built with the hard constraints."
        override val classFill = "Classes filled"
        override val teacherLoad = "Teaching loads used"
        override val otherSoft = "Soft constraints met"
        override fun statusName(status: GenerationStatus) = when (status) {
            GenerationStatus.RUNNING -> "running"
            GenerationStatus.FINISHED -> "finished"
            GenerationStatus.STOPPED -> "stopped early"
            GenerationStatus.FAILED -> "failed"
            GenerationStatus.FAILED_INFEASIBLE -> "failed – hard constraints cannot be met"
        }
        override fun generationTitle(startedAt: String) = "Generation of $startedAt"
        override fun progress(elapsed: String, alternativesFound: Int) =
            "Running for $elapsed – ${if (alternativesFound == 1) "1 alternative" else "$alternativesFound alternatives"} found so far."
        override fun alternativeTitle(number: Int) = "Alternative $number"
        override fun percent(value: Double) = String.format(Locale.UK, "%.1f%%", value * 100)
        override fun dateTime(at: Instant): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(at)
        override fun duration(seconds: Long) = if (seconds < 60) "$seconds s" else "${seconds / 60} min ${seconds % 60} s"
    }

    override val timetables = object : TimetableTexts {
        override val viewClass = "Class"
        override val viewTeacher = "Teacher"
        override val viewRoom = "Room"
        override val show = "Show"
        override val objectivesHeading = "Objective values"
        override val unplacedHeading = "Lessons not placed"
        override val deletedEntry = "deleted entry"
        override val slotColumn = "Time"
        override fun unplacedEntry(className: String, subjectName: String, periods: Int) =
            "$className: $subjectName – ${if (periods == 1) "1 period" else "$periods periods"}"
    }
}
