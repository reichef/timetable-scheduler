package timetablescheduler.adapters.web.i18n

import timetablescheduler.application.error.DependantType
import timetablescheduler.domain.account.InterfaceLanguage
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** FR-045: every interface text exists in both languages and is really translated. */
class UiTextsTest {

    private val validationErrors = listOf(
        ValidationError.BlankValue("name"),
        ValidationError.TooLong("name", 80),
        ValidationError.OutOfRange("weeklyLoad", 0, 38),
        ValidationError.OutOfRange("password", 10, null),
        ValidationError.InvalidFormat("startTime"),
        ValidationError.DuplicateValue("identifier"),
        ValidationError.InvalidReference("features"),
        ValidationError.Overlap,
        ValidationError.EndNotAfterStart,
        ValidationError.LastSchoolRemoved,
        ValidationError.NoActivePlannerLeft(SchoolId.new()),
        ValidationError.IncorrectCurrentPassword,
    )

    private val readinessIssues = listOf(
        ReadinessIssue.SubjectNoSuitableRoom(SubjectId.new(), emptySet()),
        ReadinessIssue.SubjectNoQualifiedTeacher(SubjectId.new()),
        ReadinessIssue.SubjectTeacherCapacityShortfall(SubjectId.new(), 3),
        ReadinessIssue.LevelExceedsGrid(ClassLevelId.new(), 2),
        ReadinessIssue.TeacherWithoutQualification(TeacherId.new()),
        ReadinessIssue.TeacherZeroLoad(TeacherId.new()),
        ReadinessIssue.TeacherLoadExceedsGrid(TeacherId.new(), 1),
    )

    /** Renders [text] in both catalogs and checks both are filled and differ. */
    private fun assertTranslated(description: String, text: (UiTexts) -> String) {
        val german = text(GermanTexts)
        val english = text(EnglishTexts)
        assertTrue(german.isNotBlank() && english.isNotBlank(), "$description is empty")
        assertNotEquals(german, english, "$description is not translated")
    }

    @Test
    fun `catalogs belong to their language`() {
        assertEquals(InterfaceLanguage.GERMAN, GermanTexts.language)
        assertEquals(InterfaceLanguage.ENGLISH, EnglishTexts.language)
        assertEquals(GermanTexts, textsFor(InterfaceLanguage.GERMAN))
        assertEquals(EnglishTexts, textsFor(InterfaceLanguage.ENGLISH))
    }

    @Test
    fun `every validation error is described in both languages`() {
        validationErrors.forEach { error -> assertTranslated("$error") { it.errors.describe(error) } }
    }

    @Test
    fun `every dependant type, role, slot kind and weekday is named in both languages`() {
        DependantType.entries.forEach { type -> assertTranslated("$type") { it.errors.describe(type) } }
        Role.entries.forEach { role -> assertTranslated("$role") { it.common.roleName(role) } }
        SlotKind.entries.forEach { kind -> assertTranslated("$kind") { it.timeGrid.slotKindName(kind) } }
        DayOfWeek.entries.forEach { day -> assertTranslated("$day") { it.common.dayName(day) } }
    }

    @Test
    fun `every readiness issue kind has a heading in both languages`() {
        readinessIssues.forEach { issue -> assertTranslated(issue::class.simpleName!!) { it.readiness.heading(issue) } }
        assertEquals(readinessIssues.size, readinessIssues.map { GermanTexts.readiness.heading(it) }.toSet().size)
    }
}
