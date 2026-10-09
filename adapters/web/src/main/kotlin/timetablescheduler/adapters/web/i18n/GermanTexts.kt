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

/** German interface texts — the reference language with the school terminology (R14). */
object GermanTexts : UiTexts {
    override val language = InterfaceLanguage.GERMAN

    override val common = object : CommonTexts {
        override val appTitle = "Stundenplaner"
        override val start = "Start"
        override val save = "Speichern"
        override val create = "Anlegen"
        override val add = "Hinzufügen"
        override val edit = "Bearbeiten"
        override val delete = "Löschen"
        override val cancel = "Abbrechen"
        override val back = "Zurück"
        override val name = "Name"
        override val identifier = "Bezeichnung"
        override val toSchool = "Zur Schule"
        override val navSchool = "Schule"
        override val navAccounts = "Konten"
        override val navChangePassword = "Passwort ändern"
        override val logout = "Abmelden"
        override val noticeTitle = "Hinweis"
        override val errorTitle = "Fehler"
        override val roomFeatures = "Ausstattung"

        override fun dayName(day: DayOfWeek) = when (day) {
            DayOfWeek.MONDAY -> "Montag"
            DayOfWeek.TUESDAY -> "Dienstag"
            DayOfWeek.WEDNESDAY -> "Mittwoch"
            DayOfWeek.THURSDAY -> "Donnerstag"
            DayOfWeek.FRIDAY -> "Freitag"
            DayOfWeek.SATURDAY -> "Samstag"
            DayOfWeek.SUNDAY -> "Sonntag"
        }

        override fun roleName(role: Role) = when (role) {
            Role.PLANNER -> "Planer"
            Role.TEACHER -> "Lehrkraft"
        }
    }

    override val auth = object : AuthTexts {
        override val loginTitle = "Anmelden"
        override val loginFailed = "Benutzername oder Passwort ist falsch."
        override val username = "Benutzername"
        override val password = "Passwort"
        override val teacherHome = "Für Lehrkräfte ist noch keine Ansicht verfügbar."
        override val plannerLinkToSchool = "Planer: zur Schule"
    }

    override val errors = object : ErrorTexts {
        override val unexpectedError = "Ein unerwarteter Fehler ist aufgetreten. Bitte versuchen Sie es erneut."
        override val correlationId = "Fehlerkennung"
        override val notFound = "Der Eintrag wurde nicht gefunden."
        override val accessDenied = "Sie haben keine Berechtigung für diese Seite."
        override val versionConflict = "Der Eintrag wurde inzwischen von jemand anderem geändert."
        override val reload = "Neu laden"
        override val conflictTitle = "Konflikt"
        override val deletionBlockedTitle = "Löschen nicht möglich"
        override val deletionBlocked = "Der Eintrag kann nicht gelöscht werden, weil er noch verwendet wird von:"
        override val invalidInputTitle = "Ungültige Eingabe"
        override val invalidInput = "Bitte korrigieren Sie die Eingaben:"

        override fun hiddenDependants(count: Int) = "und $count Einträge anderer Schulen"

        private val fieldLabels = mapOf(
            "name" to "Name",
            "username" to "Benutzername",
            "role" to "Rolle",
            "password" to "Passwort",
            "identifier" to "Bezeichnung",
            "label" to "Bezeichnung",
            "suffix" to "Klassenzusatz",
            "priorityWeight" to "Gewichtung",
            "periodsPerWeek" to "Wochenstunden",
            "shortCode" to "Kürzel",
            "weeklyLoad" to "Deputat",
            "startTime" to "Beginn",
            "endTime" to "Ende",
            "kind" to "Art",
            "features" to "Ausstattung",
            "subjects" to "Fächer",
            "dayOfWeek" to "Wochentag",
            "slot" to "Zeitfenster",
            "timeLimitMinutes" to "Zeitlimit",
            "homeRoomId" to "Klassenraum",
            "limit" to "Grenzwert",
            "classId" to "Klasse",
            "teacherId" to "Lehrkraft",
            "subjectId" to "Fach",
            "course" to "Fach dieser Klasse",
            "separateObjective" to "Gesondert verglichene Soll-Bedingungen",
        )

        private fun label(field: String) = fieldLabels[field] ?: field

        override fun describe(error: ValidationError) = when (error) {
            is ValidationError.BlankValue -> "${label(error.field)} darf nicht leer sein."
            is ValidationError.TooLong -> "${label(error.field)} darf höchstens ${error.maxLength} Zeichen lang sein."
            is ValidationError.OutOfRange ->
                if (error.max == null) "${label(error.field)} muss mindestens ${error.min} sein."
                else "${label(error.field)} muss zwischen ${error.min} und ${error.max} liegen."
            is ValidationError.InvalidFormat -> "${label(error.field)} hat ein ungültiges Format."
            is ValidationError.DuplicateValue -> "${label(error.field)} ist bereits vergeben."
            is ValidationError.InvalidReference -> "${label(error.field)}: ungültige Auswahl."
            ValidationError.Overlap -> "Das Zeitfenster überschneidet sich mit einem vorhandenen Zeitfenster."
            ValidationError.EndNotAfterStart -> "Das Ende muss nach dem Beginn liegen."
            ValidationError.LastSchoolRemoved -> "Es muss mindestens eine Schule zugeordnet sein."
            is ValidationError.NoActivePlannerLeft -> "Es muss mindestens ein aktiver Planer bleiben."
            ValidationError.IncorrectCurrentPassword -> "Das aktuelle Passwort ist falsch."
            is ValidationError.HomeRoomTaken -> "Der Raum ist bereits Klassenraum von ${error.otherClassName}."
            is ValidationError.ConfirmationMissing -> "Bitte bestätigen Sie die Hinweise."
            ValidationError.TeacherNotQualified -> "Die Lehrkraft ist für das Fach nicht qualifiziert."
            ValidationError.SubjectNotInCurriculum -> "Das Fach steht nicht im Lehrplan der Klasse."
            is ValidationError.FixedPeriodsExceedLoad -> "Die festgelegten ${error.fixedPeriods} Stunden übersteigen das Deputat von ${error.weeklyLoad} Stunden."
        }

        override fun describe(type: DependantType) = when (type) {
            DependantType.ROOM -> "Raum"
            DependantType.ROOM_FEATURE -> "Ausstattung"
            DependantType.SUBJECT -> "Fach"
            DependantType.CLASS_LEVEL -> "Klassenstufe"
            DependantType.SCHOOL_CLASS -> "Klasse"
            DependantType.TIME_SLOT -> "Zeitfenster"
            DependantType.TEACHER -> "Lehrkraft"
            DependantType.SCHEDULING_CONSTRAINT -> "Bedingung"
            DependantType.TEACHER_FIXATION -> "Festgelegte Lehrkraft"
        }
    }

    override val languageSwitch = object : LanguageSwitchTexts {
        override val label = "Sprache"
        override val unsavedWarning = "Ungespeicherte Eingaben gehen beim Wechsel der Sprache verloren. Trotzdem wechseln?"
    }

    override val school = object : SchoolTexts {
        override val rename = "Umbenennen"
        override val subjectsLink = "Fächer"
        override val classLevelsLink = "Klassenstufen und Klassen"
        override val teachersLink = "Lehrkräfte"
        override val readinessLink = "Bereitschaft für die Planung"
        override val generationsLink = "Stundenplanläufe"
        override val constraintsLink = "Bedingungen"
        override fun featuresLink(count: Int) = "Ausstattung ($count)"
        override fun roomsLink(count: Int) = "Räume ($count)"
        override fun timeGridLink(teachingSlotsPerWeek: Int) = "Zeitraster ($teachingSlotsPerWeek Unterrichtsstunden pro Woche)"
        override fun readinessSummary(issueCount: Int) = if (issueCount == 0) ": keine Probleme" else ": $issueCount Probleme"
    }

    override val rooms = object : RoomTexts {
        override val roomsTitle = "Räume"
        override val roomColumn = "Raum"
        override val createRoom = "Raum anlegen"
        override val manageFeatures = "Ausstattung verwalten"
        override val deleteRoom = "Raum löschen"
        override val toRoomList = "Zur Raumliste"
        override val createFeature = "Ausstattung anlegen"
        override val toRooms = "Zu den Räumen"
        override fun roomTitle(identifier: String) = "Raum $identifier"
    }

    override val timeGrid = object : TimeGridTexts {
        override val addSlot = "Zeitfenster hinzufügen"
        override val teachingDays = "Unterrichtstage"
        override val saveTeachingDays = "Unterrichtstage speichern"
        override val slotColumn = "Zeitfenster"
        override val timeColumn = "Zeit"
        override val kindColumn = "Art"
        override val actionsColumn = "Aktionen"
        override val label = "Bezeichnung"
        override val start = "Beginn"
        override val end = "Ende"
        override val kind = "Art"
        override val daysWithoutSlot = "Tage ohne dieses Zeitfenster"
        override val notHeldOn = "Entfällt am"
        override val deleteSlot = "Zeitfenster löschen"
        override val toTimeGrid = "Zum Zeitraster"
        override val deleteSlotTitle = "Zeitfenster löschen"
        override val deletePermanently = "Endgültig löschen"
        override fun title(schoolName: String) = "Zeitraster – $schoolName"
        override fun teachingSlotsPerWeek(count: Int) = "Unterrichtsstunden pro Woche: $count"
        override fun slotKindName(kind: SlotKind) = when (kind) {
            SlotKind.TEACHING -> "Unterricht"
            SlotKind.BREAK -> "Pause"
        }
        override fun slotTitle(label: String) = "Zeitfenster $label"
        override fun deleteQuestion(label: String, start: LocalTime, end: LocalTime) = "Zeitfenster „$label“ ($start–$end) wirklich löschen?"
        override fun deleteRemovals(exclusionCount: Int, unavailabilityCount: Int) =
            "Dabei werden $exclusionCount Tagesausnahme(n) und $unavailabilityCount Sperrzeiten von Lehrkräften entfernt."
    }

    override val subjects = object : SubjectTexts {
        override val title = "Fächer"
        override val subjectColumn = "Fach"
        override val requiredFeaturesColumn = "Benötigte Ausstattung"
        override val createSubject = "Fach anlegen"
        override val deleteSubject = "Fach löschen"
        override val toSubjects = "Zur Fächerliste"
        override fun subjectTitle(name: String) = "Fach $name"
    }

    override val classLevels = object : ClassLevelTexts {
        override val title = "Klassenstufen"
        override val levelColumn = "Stufe"
        override val weeklyPeriodsColumn = "Wochenstunden"
        override val classesColumn = "Klassen"
        override val createLevel = "Klassenstufe anlegen"
        override val levelIdentifierLabel = "Bezeichnung (z. B. 6)"
        override val curriculum = "Lehrplan"
        override val subjectColumn = "Fach"
        override val periodsInputColumn = "Wochenstunden (leer = nicht unterrichtet)"
        override val createClass = "Klasse anlegen"
        override val deleteLevel = "Klassenstufe löschen"
        override val toLevels = "Zu den Klassenstufen"
        override val suffixLabel = "Klassenzusatz"
        override val weightLabel = "Gewichtung (1–100)"
        override val toLevel = "Zur Klassenstufe"
        override val editClass = "Klasse bearbeiten"
        override val deleteClass = "Klasse löschen"
        override val homeRoomLabel = "Klassenraum"
        override val noHomeRoom = "kein Klassenraum"
        override fun gridOffers(teachingSlotsPerWeek: Int) = "Das Zeitraster bietet $teachingSlotsPerWeek Unterrichtsstunden pro Woche."
        override fun levelTitle(identifier: String) = "Klassenstufe $identifier"
        override fun gridWarning(weeklyPeriods: Int, teachingSlotsPerWeek: Int, excessPeriods: Int) =
            "Der Lehrplan passt nicht in das Zeitraster: $weeklyPeriods Wochenstunden, aber nur $teachingSlotsPerWeek " +
                "Unterrichtsstunden pro Woche ($excessPeriods zu viel). Gespeichert ist er trotzdem."
        override fun levelSum(weeklyPeriods: Int, teachingSlotsPerWeek: Int) =
            "Summe: $weeklyPeriods Wochenstunden (Zeitraster: $teachingSlotsPerWeek Unterrichtsstunden pro Woche)"
        override fun newClassTitle(levelIdentifier: String) = "Neue Klasse in Stufe $levelIdentifier"
        override fun classTitle(displayName: String) = "Klasse $displayName"
        override fun curriculumFromLevel(levelIdentifier: String) = "Lehrplan (aus Stufe $levelIdentifier)"
        override fun classSum(weeklyPeriods: Int) = "Summe: $weeklyPeriods Wochenstunden"
    }

    override val teachers = object : TeacherTexts {
        override val title = "Lehrkräfte"
        override val shortCodeColumn = "Kürzel"
        override val loadColumn = "Deputat"
        override val createTeacher = "Lehrkraft anlegen"
        override val shortCodeLabel = "Kürzel (2–8 Großbuchstaben oder Ziffern)"
        override val general = "Allgemein"
        override val qualifications = "Qualifikationen"
        override val subjectsLegend = "Fächer"
        override val noSubjects = "Es sind noch keine Fächer angelegt."
        override val saveQualifications = "Qualifikationen speichern"
        override val unavailabilityTitle = "Sperrzeiten (angekreuzt = nicht verfügbar)"
        override val noTeachingSlots = "Das Zeitraster hat noch keine Unterrichtsstunden."
        override val slotColumn = "Zeitfenster"
        override val saveUnavailability = "Sperrzeiten speichern"
        override val deleteTeacher = "Lehrkraft löschen"
        override val toTeachers = "Zur Lehrkräfteliste"
        override fun loadLabel(teachingSlotsPerWeek: Int) = "Deputat (höchstens $teachingSlotsPerWeek Wochenstunden)"
        override fun teacherTitle(displayName: String) = "Lehrkraft $displayName"
    }

    override val accounts = object : AccountTexts {
        override val title = "Konten"
        override val roleColumn = "Rolle"
        override val statusColumn = "Status"
        override val role = "Rolle"
        override val createAccount = "Konto anlegen"
        override val initialPassword = "Anfangspasswort (mindestens 10 Zeichen)"
        override val deactivateHeading = "Deaktivieren"
        override val activateHeading = "Aktivieren"
        override val deactivateButton = "Konto deaktivieren"
        override val activateButton = "Konto aktivieren"
        override val resetPassword = "Passwort zurücksetzen"
        override val newPassword = "Neues Passwort (mindestens 10 Zeichen)"
        override val resetButton = "Zurücksetzen"
        override val toAccounts = "Zur Kontenliste"
        override val ownPasswordTitle = "Passwort ändern"
        override val passwordChanged = "Das Passwort wurde geändert."
        override val currentPassword = "Aktuelles Passwort"
        override val changeButton = "Ändern"
        override fun accountTitle(username: String) = "Konto $username"
        override fun stateName(active: Boolean) = if (active) "aktiv" else "deaktiviert"
        override fun status(active: Boolean) = "Status: ${stateName(active)}"
    }

    override val readiness = object : ReadinessTexts {
        override val title = "Bereitschaft für die Planung"
        override val noIssues = "Keine blockierenden Probleme gefunden."
        override val noRoomOffersAll = ": kein Raum bietet alle benötigte Ausstattung zusammen"
        override fun heading(issue: ReadinessIssue) = when (issue) {
            is ReadinessIssue.SubjectNoSuitableRoom -> "Fächer ohne passenden Raum"
            is ReadinessIssue.SubjectNoQualifiedTeacher -> "Fächer ohne qualifizierte Lehrkraft"
            is ReadinessIssue.SubjectTeacherCapacityShortfall -> "Fächer mit zu wenig Lehrkraft-Stunden"
            is ReadinessIssue.LevelExceedsGrid -> "Klassenstufen über dem Zeitraster"
            is ReadinessIssue.TeacherWithoutQualification -> "Lehrkräfte ohne Qualifikation"
            is ReadinessIssue.TeacherZeroLoad -> "Lehrkräfte ohne Deputat"
            is ReadinessIssue.TeacherLoadExceedsGrid -> "Lehrkräfte mit Deputat über dem Zeitraster"
            is ReadinessIssue.InvalidTeacherFixation -> "Ungültige Lehrer-Festlegungen"
        }
        override fun missingFeatures(featureNames: List<String>) = " – fehlende Ausstattung: ${featureNames.joinToString(", ")}"
        override fun missingPeriods(periods: Int) = ": $periods Wochenstunden fehlen"
        override fun excessPeriods(periods: Int) = ": $periods Wochenstunden zu viel"
        override fun levelName(identifier: String) = "Stufe $identifier"
    }

    override val constraints: ConstraintTexts = GermanConstraintTexts

    override val generations = object : GenerationTexts {
        override val title = "Stundenplanläufe"
        override val startHeading = "Neuen Lauf starten"
        override val timeLimitLabel = "Zeitlimit in Minuten (1–60)"
        override val startButton = "Lauf starten"
        override val warningsIntro = "Hinweise aus der Bereitschaftsprüfung:"
        override val warningsConfirm = "Ich habe die Hinweise gelesen und möchte trotzdem starten."
        override val blockedTitle = "Start nicht möglich"
        override val blockedIntro = "Diese Probleme müssen vor der Planung behoben werden:"
        override val toReadiness = "Zur Bereitschaftsübersicht"
        override val runningTitle = "Lauf läuft bereits"
        override val runningExists = "Für die Schule läuft bereits ein Lauf. Warten Sie, bis er endet, oder stoppen Sie ihn."
        override val toRunning = "Zum laufenden Lauf"
        override val startedColumn = "Gestartet"
        override val startedByColumn = "Von"
        override val statusColumn = "Status"
        override val alternativesColumn = "Alternativen"
        override val noGenerations = "Noch keine Läufe."
        override val stopButton = "Stoppen"
        override val alternativesHeading = "Gleichwertige Alternativen"
        override val noAlternatives = "Keine Alternativen gefunden."
        override val unplacedColumn = "Nicht verplant"
        override val softViolationsColumn = "Soll-Verstöße"
        override val openAlternative = "Ansehen"
        override val toGenerations = "Zu den Läufen"
        override val infeasibleHint = "Mit den Muss-Bedingungen ließ sich kein gültiger Stundenplan bilden."
        override val classFill = "Klassen gefüllt"
        override val teacherLoad = "Deputate genutzt"
        override val otherSoft = "Soll-Bedingungen erfüllt"
        override fun statusName(status: GenerationStatus) = when (status) {
            GenerationStatus.RUNNING -> "läuft"
            GenerationStatus.FINISHED -> "abgeschlossen"
            GenerationStatus.STOPPED -> "vorzeitig gestoppt"
            GenerationStatus.FAILED -> "fehlgeschlagen"
            GenerationStatus.FAILED_INFEASIBLE -> "fehlgeschlagen – Muss-Bedingungen nicht erfüllbar"
        }
        override fun generationTitle(startedAt: String) = "Lauf vom $startedAt"
        override fun progress(elapsed: String, alternativesFound: Int) =
            "Läuft seit $elapsed – bisher ${if (alternativesFound == 1) "1 Alternative" else "$alternativesFound Alternativen"} gefunden."
        override fun alternativeTitle(number: Int) = "Alternative $number"
        override fun percent(value: Double) = String.format(Locale.GERMANY, "%.1f %%", value * 100)
        override fun dateTime(at: Instant): String = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(at)
        override fun duration(seconds: Long) = if (seconds < 60) "$seconds s" else "${seconds / 60} min ${seconds % 60} s"
    }

    override val timetables = object : TimetableTexts {
        override val viewClass = "Klasse"
        override val viewTeacher = "Lehrkraft"
        override val viewRoom = "Raum"
        override val show = "Anzeigen"
        override val objectivesHeading = "Zielwerte"
        override val unplacedHeading = "Nicht verplante Stunden"
        override val deletedEntry = "gelöschter Eintrag"
        override val slotColumn = "Zeit"
        override fun unplacedEntry(className: String, subjectName: String, periods: Int) =
            "$className: $subjectName – ${if (periods == 1) "1 Stunde" else "$periods Stunden"}"
    }
}
