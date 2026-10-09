package com.medhome.nepal.ui.shell

import com.medhome.nepal.domain.Role
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.ui.language.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppShellLogicTest {

    private fun profile(name: String) = UserProfile("uid", name, "a@b.co", Role.PATIENT)

    @Test
    fun `patients get the tabs, admins the admin panel and doctors the placeholder`() {
        assertEquals(SignedInHome.PATIENT_TABS, signedInHomeFor(Role.PATIENT))
        assertEquals(SignedInHome.ADMIN_PANEL, signedInHomeFor(Role.ADMIN))
        assertEquals(SignedInHome.STAFF_PLACEHOLDER, signedInHomeFor(Role.DOCTOR))
    }

    @Test
    fun `first name is the first word of the name`() {
        assertEquals("Prabhash", profile("Prabhash Sigdel").firstName)
        assertEquals("Prabhash", profile("  Prabhash   Kumar  Sigdel ").firstName)
        assertEquals("Asha", profile("Asha").firstName)
    }

    @Test
    fun `first name is null for a blank name`() {
        assertNull(profile("").firstName)
        assertNull(profile("   ").firstName)
    }

    @Test
    fun `saved app language wins over the phone language`() {
        assertEquals(AppLanguage.NEPALI, AppLanguage.resolve(appLanguageTag = "ne", systemLanguageTag = "en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(appLanguageTag = "en", systemLanguageTag = "ne"))
    }

    @Test
    fun `without a saved choice the phone language is used when supported`() {
        assertEquals(AppLanguage.NEPALI, AppLanguage.resolve(appLanguageTag = null, systemLanguageTag = "ne-NP"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(appLanguageTag = null, systemLanguageTag = "en-US"))
    }

    @Test
    fun `unsupported languages fall back to English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(appLanguageTag = null, systemLanguageTag = "hi"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(appLanguageTag = null, systemLanguageTag = null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(appLanguageTag = "fr", systemLanguageTag = "de"))
    }
}
