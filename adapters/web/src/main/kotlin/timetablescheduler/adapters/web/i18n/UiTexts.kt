package timetablescheduler.adapters.web.i18n

import timetablescheduler.application.error.DependantType
import timetablescheduler.domain.scheduling.evaluation.HardRule
import timetablescheduler.domain.account.InterfaceLanguage
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * All interface texts of one language (R18). Every text is a member, so a catalog that misses a text does not
 * compile (FR-045); `when` over sealed types in the implementations forces a text for every new case.
 * Texts never contain user data except values passed in as parameters (FR-044).
 */
interface UiTexts {
    val language: InterfaceLanguage
    val common: CommonTexts
    val auth: AuthTexts
    val errors: ErrorTexts
    val languageSwitch: LanguageSwitchTexts
    val school: SchoolTexts
    val rooms: RoomTexts
    val timeGrid: TimeGridTexts
    val subjects: SubjectTexts
    val classLevels: ClassLevelTexts
    val teachers: TeacherTexts
    val accounts: AccountTexts
    val readiness: ReadinessTexts
    val generations: GenerationTexts
    val constraints: ConstraintTexts
    val timetables: TimetableTexts
}

fun textsFor(language: InterfaceLanguage): UiTexts = when (language) {
    InterfaceLanguage.GERMAN -> GermanTexts
    InterfaceLanguage.ENGLISH -> EnglishTexts
}

interface CommonTexts {
    val appTitle: String
    val start: String
    val save: String
    val create: String
    val add: String
    val edit: String
    val delete: String
    val cancel: String
    val back: String
    val name: String
    val identifier: String
    val toSchool: String
    val navSchool: String
    val navAccounts: String
    val navChangePassword: String
    val logout: String
    val noticeTitle: String
    val errorTitle: String
    val roomFeatures: String
    fun dayName(day: DayOfWeek): String
    fun roleName(role: Role): String
}

interface AuthTexts {
    val loginTitle: String
    val loginFailed: String
    val username: String
    val password: String
    val teacherHome: String
    val plannerLinkToSchool: String
}

interface ErrorTexts {
    val unexpectedError: String
    val correlationId: String
    val notFound: String
    val accessDenied: String
    val versionConflict: String
    val reload: String
    val conflictTitle: String
    val deletionBlockedTitle: String
    val deletionBlocked: String
    val invalidInputTitle: String
    val invalidInput: String
    fun hiddenDependants(count: Int): String
    fun describe(error: ValidationError): String
    fun describe(type: DependantType): String
}

interface LanguageSwitchTexts {
    val label: String
    val unsavedWarning: String
}

interface SchoolTexts {
    val rename: String
    val subjectsLink: String
    val classLevelsLink: String
    val teachersLink: String
    val readinessLink: String
    val generationsLink: String
    val constraintsLink: String
    val chosenTimetableLink: String
    fun featuresLink(count: Int): String
    fun roomsLink(count: Int): String
    fun timeGridLink(teachingSlotsPerWeek: Int): String
    fun readinessSummary(issueCount: Int): String
}

interface RoomTexts {
    val roomsTitle: String
    val roomColumn: String
    val createRoom: String
    val manageFeatures: String
    val deleteRoom: String
    val toRoomList: String
    val createFeature: String
    val toRooms: String
    fun roomTitle(identifier: String): String
}

interface TimeGridTexts {
    val addSlot: String
    val teachingDays: String
    val saveTeachingDays: String
    val slotColumn: String
    val timeColumn: String
    val kindColumn: String
    val actionsColumn: String
    val label: String
    val start: String
    val end: String
    val kind: String
    val daysWithoutSlot: String
    val notHeldOn: String
    val deleteSlot: String
    val toTimeGrid: String
    val deleteSlotTitle: String
    val deletePermanently: String
    fun title(schoolName: String): String
    fun teachingSlotsPerWeek(count: Int): String
    fun slotKindName(kind: SlotKind): String
    fun slotTitle(label: String): String
    fun deleteQuestion(label: String, start: LocalTime, end: LocalTime): String
    fun deleteRemovals(exclusionCount: Int, unavailabilityCount: Int): String
}

interface SubjectTexts {
    val title: String
    val subjectColumn: String
    val requiredFeaturesColumn: String
    val createSubject: String
    val deleteSubject: String
    val toSubjects: String
    fun subjectTitle(name: String): String
}

interface ClassLevelTexts {
    val title: String
    val levelColumn: String
    val weeklyPeriodsColumn: String
    val classesColumn: String
    val createLevel: String
    val levelIdentifierLabel: String
    val curriculum: String
    val subjectColumn: String
    val periodsInputColumn: String
    val createClass: String
    val deleteLevel: String
    val toLevels: String
    val suffixLabel: String
    val weightLabel: String
    val toLevel: String
    val editClass: String
    val deleteClass: String
    val homeRoomLabel: String
    val noHomeRoom: String
    fun gridOffers(teachingSlotsPerWeek: Int): String
    fun levelTitle(identifier: String): String
    fun gridWarning(weeklyPeriods: Int, teachingSlotsPerWeek: Int, excessPeriods: Int): String
    fun levelSum(weeklyPeriods: Int, teachingSlotsPerWeek: Int): String
    fun newClassTitle(levelIdentifier: String): String
    fun classTitle(displayName: String): String
    fun curriculumFromLevel(levelIdentifier: String): String
    fun classSum(weeklyPeriods: Int): String
}

interface TeacherTexts {
    val title: String
    val shortCodeColumn: String
    val loadColumn: String
    val createTeacher: String
    val shortCodeLabel: String
    val general: String
    val qualifications: String
    val subjectsLegend: String
    val noSubjects: String
    val saveQualifications: String
    val unavailabilityTitle: String
    val noTeachingSlots: String
    val slotColumn: String
    val saveUnavailability: String
    val deleteTeacher: String
    val toTeachers: String
    fun loadLabel(teachingSlotsPerWeek: Int): String
    fun teacherTitle(displayName: String): String
}

interface AccountTexts {
    val title: String
    val roleColumn: String
    val statusColumn: String
    val role: String
    val createAccount: String
    val initialPassword: String
    val deactivateHeading: String
    val activateHeading: String
    val deactivateButton: String
    val activateButton: String
    val resetPassword: String
    val newPassword: String
    val resetButton: String
    val toAccounts: String
    val ownPasswordTitle: String
    val passwordChanged: String
    val currentPassword: String
    val changeButton: String
    fun accountTitle(username: String): String
    fun stateName(active: Boolean): String
    fun status(active: Boolean): String
}

interface ReadinessTexts {
    val title: String
    val noIssues: String
    val noRoomOffersAll: String
    fun heading(issue: ReadinessIssue): String
    fun missingFeatures(featureNames: List<String>): String
    fun missingPeriods(periods: Int): String
    fun excessPeriods(periods: Int): String
    fun levelName(identifier: String): String
}

/** Generations and their alternatives (002 US1). */
interface GenerationTexts {
    val title: String
    val startHeading: String
    val timeLimitLabel: String
    val startButton: String
    val warningsIntro: String
    val warningsConfirm: String
    val blockedTitle: String
    val blockedIntro: String
    val toReadiness: String
    val runningTitle: String
    val runningExists: String
    val toRunning: String
    val startedColumn: String
    val startedByColumn: String
    val statusColumn: String
    val alternativesColumn: String
    val noGenerations: String
    val stopButton: String
    val alternativesHeading: String
    val noAlternatives: String
    val unplacedColumn: String
    val softViolationsColumn: String
    val openAlternative: String
    val toGenerations: String
    val infeasibleHint: String
    val classFill: String
    val teacherLoad: String
    val otherSoft: String
    val stability: String
    val startFromChosen: String
    val compareLink: String
    val compareTitle: String
    val xAxis: String
    val yAxis: String
    val showChart: String
    val compareWith: String
    val diffHint: String
    val chooseButton: String
    val chosenMarker: String
    val confirmChooseTitle: String
    val confirmChooseIntro: String
    val confirmChooseButton: String
    val cancel: String
    val conflictsTitle: String
    val conflictsIntro: String
    fun changedLessons(count: Int): String
    fun chartCaption(xTitle: String, yTitle: String): String
    fun diffTitle(first: Int, second: Int): String
    fun hardRuleName(rule: HardRule): String
    fun statusName(status: GenerationStatus): String
    fun generationTitle(startedAt: String): String
    fun progress(elapsed: String, alternativesFound: Int): String
    fun alternativeTitle(number: Int): String
    fun percent(value: Double): String
    fun dateTime(at: java.time.Instant): String
    fun duration(seconds: Long): String
}

/** Weekly grids of alternatives (002 FR-019). */
interface TimetableTexts {
    val viewClass: String
    val viewTeacher: String
    val viewRoom: String
    val show: String
    val objectivesHeading: String
    val unplacedHeading: String
    val deletedEntry: String
    val slotColumn: String
    val chosenTitle: String
    val noChosen: String
    val toSourceGeneration: String
    val noLongerFits: String
    val startFromChosenLink: String
    val conflictsHeading: String
    val noLongerRequired: String
    val noLongerRequiredHeading: String
    val notPlacedHeading: String
    fun chosenInfo(chosenAt: String, username: String): String
    fun lessonAt(className: String, subjectName: String, dayName: String, slotLabel: String): String
    fun unplacedEntry(className: String, subjectName: String, periods: Int): String
}
