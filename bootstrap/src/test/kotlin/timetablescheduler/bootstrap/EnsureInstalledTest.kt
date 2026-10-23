package timetablescheduler.bootstrap

import timetablescheduler.adapters.persistence.DatabaseSettings
import timetablescheduler.application.account.InitialSetup
import timetablescheduler.application.fakes.MasterDataWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** FR-031: the first start installs from the configuration; later starts ignore it. */
class EnsureInstalledTest {
    private val world = MasterDataWorld()
    private val installation = world.installation()

    private fun configWith(setup: InitialSetup?) =
        AppConfig(DatabaseSettings("jdbc:postgresql://db/test", "app", "secret"), byteArrayOf(1), false, setup)

    @Test
    fun `the first start creates the school and the planner from the initial setup`() {
        ensureInstalled(installation, configWith(InitialSetup("Gymnasium Nord", "admin", "anfangs-passwort")))

        assertNotNull(world.schools.findByNameIgnoreCase("Gymnasium Nord"))
        assertNotNull(world.accounts.findByUsernameIgnoreCase("admin"))
    }

    @Test
    fun `an empty database without initial setup names the missing variables`() {
        val failure = assertFailsWith<MissingConfiguration> { ensureInstalled(installation, configWith(null)) }

        assertTrue("TTS_INITIAL_SCHOOL_NAME" in failure.message.orEmpty())
    }

    @Test
    fun `a later start ignores the initial setup`() {
        ensureInstalled(installation, configWith(InitialSetup("Gymnasium Nord", "admin", "anfangs-passwort")))

        ensureInstalled(installation, configWith(null))
        ensureInstalled(installation, configWith(InitialSetup("Realschule Süd", "zweiter", "anderes-passwort")))

        assertEquals(null, world.schools.findByNameIgnoreCase("Realschule Süd"))
    }

    @Test
    fun `a rejected initial setup stops the start without revealing the values`() {
        val tooShortPassword = configWith(InitialSetup("Gymnasium Nord", "admin", "kurz"))

        val failure = assertFailsWith<MissingConfiguration> { ensureInstalled(installation, tooShortPassword) }

        assertTrue("TTS_INITIAL_" in failure.message.orEmpty())
        assertFalse("kurz" in failure.message.orEmpty())
        assertFalse(installation.isInstalled())
    }
}
