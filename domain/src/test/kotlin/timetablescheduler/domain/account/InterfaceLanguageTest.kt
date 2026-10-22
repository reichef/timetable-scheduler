package timetablescheduler.domain.account

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterfaceLanguageTest {
    @Test
    fun `languages have their two-letter codes`() {
        assertEquals("de", InterfaceLanguage.GERMAN.code)
        assertEquals("en", InterfaceLanguage.ENGLISH.code)
    }

    @Test
    fun `code lookup ignores case and rejects unknown or blank codes`() {
        assertEquals(InterfaceLanguage.ENGLISH, InterfaceLanguage.fromCode("EN"))
        assertEquals(InterfaceLanguage.GERMAN, InterfaceLanguage.fromCode(" de "))
        assertNull(InterfaceLanguage.fromCode("fr"))
        assertNull(InterfaceLanguage.fromCode(""))
        assertNull(InterfaceLanguage.fromCode(null))
    }

    @Test
    fun `new account has no preferred language`() {
        val account = (UserAccount.create("mueller", Role.PLANNER, setOf(SchoolId.new())) as ValidationResult.Valid).value

        assertNull(account.preferredLanguage)
    }

    @Test
    fun `choosing a language keeps everything else`() {
        val account = (UserAccount.create("mueller", Role.TEACHER, setOf(SchoolId.new())) as ValidationResult.Valid).value

        val english = account.withPreferredLanguage(InterfaceLanguage.ENGLISH)

        assertEquals(InterfaceLanguage.ENGLISH, english.preferredLanguage)
        assertEquals(account.copy(preferredLanguage = InterfaceLanguage.ENGLISH), english)
    }
}
