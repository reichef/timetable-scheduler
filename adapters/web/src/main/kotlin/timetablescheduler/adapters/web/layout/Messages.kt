package timetablescheduler.adapters.web.layout

import timetablescheduler.application.error.DependantType
import timetablescheduler.domain.validation.ValidationError

/** German user-facing texts (R14). Business messages only — technical details never reach the user. */
object Messages {
    const val APP_TITLE = "Stundenplaner"
    const val LOGIN_TITLE = "Anmelden"
    const val LOGIN_FAILED = "Benutzername oder Passwort ist falsch."
    const val TEACHER_HOME = "Für Lehrkräfte ist noch keine Ansicht verfügbar."
    const val UNEXPECTED_ERROR = "Ein unerwarteter Fehler ist aufgetreten. Bitte versuchen Sie es erneut."
    const val CORRELATION_ID = "Fehlerkennung"
    const val NOT_FOUND = "Der Eintrag wurde nicht gefunden."
    const val ACCESS_DENIED = "Sie haben keine Berechtigung für diese Seite."
    const val VERSION_CONFLICT = "Der Eintrag wurde inzwischen von jemand anderem geändert."
    const val RELOAD = "Neu laden"
    const val DELETION_BLOCKED = "Der Eintrag kann nicht gelöscht werden, weil er noch verwendet wird von:"
    const val INVALID_INPUT = "Bitte korrigieren Sie die Eingaben:"

    private val fieldLabels = mapOf(
        "name" to "Name",
        "username" to "Benutzername",
        "password" to "Passwort",
        "identifier" to "Bezeichnung",
        "label" to "Bezeichnung",
        "suffix" to "Klassenzusatz",
        "priorityWeight" to "Gewichtung",
        "periodsPerWeek" to "Wochenstunden",
        "shortCode" to "Kürzel",
        "weeklyLoad" to "Deputat",
        "minutes" to "Pendelzeit",
        "startTime" to "Beginn",
        "endTime" to "Ende",
        "features" to "Ausstattung",
        "subjects" to "Fächer",
        "schools" to "Schulen",
        "dayOfWeek" to "Wochentag",
        "slot" to "Zeitfenster",
    )

    fun label(field: String): String = fieldLabels[field] ?: field

    fun describe(error: ValidationError): String = when (error) {
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
        is ValidationError.NoActivePlannerLeft -> "Eine Schule hätte sonst keinen aktiven Planer mehr."
    }

    fun describe(type: DependantType): String = when (type) {
        DependantType.ROOM -> "Raum"
        DependantType.ROOM_FEATURE -> "Ausstattung"
        DependantType.SUBJECT -> "Fach"
        DependantType.CLASS_LEVEL -> "Klassenstufe"
        DependantType.SCHOOL_CLASS -> "Klasse"
        DependantType.TIME_SLOT -> "Zeitfenster"
        DependantType.TEACHER -> "Lehrkraft"
        DependantType.USER_ACCOUNT -> "Benutzerkonto"
    }

    fun hiddenDependants(count: Int): String = "und $count Einträge anderer Schulen"
}
