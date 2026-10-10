package com.medhome.nepal.session

import com.medhome.nepal.data.ListenerRegistry
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.domain.Gender
import com.medhome.nepal.domain.ProfileDetails
import com.medhome.nepal.domain.Role
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.fakes.FakeAuthDataSource
import com.medhome.nepal.fakes.FakeCredentialClient
import com.medhome.nepal.fakes.FakeProfileStore
import com.medhome.nepal.fakes.googleUser
import com.medhome.nepal.fakes.passwordUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private val profiles = FakeProfileStore()
    private val google = FakeCredentialClient()

    private suspend fun TestScope.withSession(
        auth: FakeAuthDataSource,
        block: suspend (SessionManager) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            block(SessionManager(auth, profiles, google, scope))
        } finally {
            scope.cancel()
        }
    }

    private suspend fun expectError(expected: AuthError, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: AuthException) {
            assertEquals(expected, e.error)
        }
    }

    private fun doctor(uid: String) = UserProfile(uid, "Dr Rai", "$uid@example.com", Role.DOCTOR)

    // Cold start

    @Test
    fun `cold start without a user is signed out`() = runTest {
        withSession(FakeAuthDataSource()) { session ->
            session.start()
            assertEquals(SessionState.SignedOut(), session.state.value)
        }
    }

    @Test
    fun `cold start with existing profile uses a plain read and no transaction`() = runTest {
        val user = passwordUser()
        profiles.profiles[user.uid] = doctor(user.uid)
        withSession(FakeAuthDataSource(user)) { session ->
            session.start()
            assertEquals(SessionState.SignedIn(doctor(user.uid), usesPassword = true), session.state.value)
            assertEquals(listOf("getProfile"), profiles.calls)
        }
    }

    @Test
    fun `cold start network failure shows profile unavailable without signing out`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        profiles.getError = AuthError.NETWORK
        withSession(auth) { session ->
            session.start()
            assertEquals(SessionState.ProfileUnavailable, session.state.value)
            assertFalse("signOut" in auth.calls)
            assertEquals(0, google.clearCount)
        }
    }

    @Test
    fun `retry after network recovers to signed in`() = runTest {
        val user = passwordUser()
        profiles.profiles[user.uid] = doctor(user.uid)
        profiles.getError = AuthError.NETWORK
        withSession(FakeAuthDataSource(user)) { session ->
            session.start()
            profiles.getError = null
            session.restoreSession()
            assertEquals(SessionState.SignedIn(doctor(user.uid), usesPassword = true), session.state.value)
        }
    }

    @Test
    fun `cold start permission denied signs out with the error`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        profiles.getError = AuthError.PERMISSION_DENIED
        withSession(auth) { session ->
            session.start()
            assertEquals(SessionState.SignedOut(AuthError.PERMISSION_DENIED), session.state.value)
            assertNull(auth.currentUser)
            assertEquals(1, google.clearCount)
        }
    }

    @Test
    fun `cold start invalid role signs out with the error`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        profiles.getError = AuthError.PROFILE_INVALID
        withSession(auth) { session ->
            session.start()
            assertEquals(SessionState.SignedOut(AuthError.PROFILE_INVALID), session.state.value)
            assertNull(auth.currentUser)
        }
    }

    @Test
    fun `cold start creates the profile only when the server says it is missing`() = runTest {
        val user = passwordUser()
        withSession(FakeAuthDataSource(user)) { session ->
            session.start()
            assertEquals(listOf("getProfile", "ensureProfile"), profiles.calls)
            assertEquals(Role.PATIENT, (session.state.value as SessionState.SignedIn).profile.role)
        }
    }

    // Sign in

    @Test
    fun `sign in never overwrites an existing role`() = runTest {
        val user = passwordUser()
        profiles.profiles[user.uid] = doctor(user.uid)
        withSession(FakeAuthDataSource().apply { nextUser = user }) { session ->
            session.signInWithEmail("a@b.co", "password123")
            assertEquals(SessionState.SignedIn(doctor(user.uid), usesPassword = true), session.state.value)
            assertEquals(listOf("ensureProfile"), profiles.calls)
        }
    }

    @Test
    fun `sign in with failed profile load signs out and reports it`() = runTest {
        val auth = FakeAuthDataSource()
        profiles.ensureError = AuthError.NETWORK
        withSession(auth) { session ->
            expectError(AuthError.PROFILE_LOAD_FAILED) { session.signInWithEmail("a@b.co", "password123") }
            assertNull(auth.currentUser)
            assertTrue(session.state.value is SessionState.SignedOut)
            assertEquals(1, google.clearCount)
        }
    }

    @Test
    fun `sign in with invalid role reports invalid profile`() = runTest {
        profiles.ensureError = AuthError.PROFILE_INVALID
        withSession(FakeAuthDataSource()) { session ->
            expectError(AuthError.PROFILE_INVALID) { session.signInWithEmail("a@b.co", "password123") }
        }
    }

    @Test
    fun `wrong password is reported without touching profiles`() = runTest {
        val auth = FakeAuthDataSource().apply { failures["signInWithEmail"] = AuthError.INVALID_CREDENTIALS }
        withSession(auth) { session ->
            expectError(AuthError.INVALID_CREDENTIALS) { session.signInWithEmail("a@b.co", "wrong-pass") }
            assertTrue(profiles.calls.isEmpty())
        }
    }

    @Test
    fun `profile email is stored lowercased`() = runTest {
        val user = passwordUser().copy(email = "Asha.Rai@Example.COM")
        withSession(FakeAuthDataSource().apply { nextUser = user }) { session ->
            session.signInWithEmail("Asha.Rai@Example.COM", "password123")
            assertEquals("asha.rai@example.com", (session.state.value as SessionState.SignedIn).profile.email)
        }
    }

    @Test
    fun `unverified password user needs verification`() = runTest {
        val user = passwordUser(verified = false)
        withSession(FakeAuthDataSource().apply { nextUser = user }) { session ->
            session.signInWithEmail("a@b.co", "password123")
            assertTrue(session.state.value is SessionState.NeedsVerification)
        }
    }

    @Test
    fun `google user counts as verified`() = runTest {
        val user = googleUser()
        withSession(FakeAuthDataSource().apply { nextUser = user }) { session ->
            session.signInWithGoogle("token")
            val state = session.state.value as SessionState.SignedIn
            assertFalse(state.usesPassword)
            assertEquals("Google Person", state.profile.name)
        }
    }

    @Test
    fun `google account collision is surfaced`() = runTest {
        val auth = FakeAuthDataSource().apply {
            failures["signInWithGoogle"] = AuthError.ACCOUNT_EXISTS_WITH_PASSWORD
        }
        withSession(auth) { session ->
            expectError(AuthError.ACCOUNT_EXISTS_WITH_PASSWORD) { session.signInWithGoogle("token") }
        }
    }

    // Sign up and verification

    @Test
    fun `sign up creates a patient and sends a verification email`() = runTest {
        val user = passwordUser(verified = false)
        val auth = FakeAuthDataSource().apply { nextUser = user }
        withSession(auth) { session ->
            session.signUp("Asha", "a@b.co", "password123")
            val state = session.state.value as SessionState.NeedsVerification
            assertEquals(Role.PATIENT, state.profile.role)
            assertFalse(state.verificationEmailFailed)
            assertEquals(
                listOf("createUserWithEmail", "updateDisplayName", "sendEmailVerification"),
                auth.calls,
            )
        }
    }

    @Test
    fun `sign up reports a failed verification email instead of hiding it`() = runTest {
        val auth = FakeAuthDataSource().apply {
            nextUser = passwordUser(verified = false)
            failures["sendEmailVerification"] = AuthError.TOO_MANY_REQUESTS
        }
        withSession(auth) { session ->
            session.signUp("Asha", "a@b.co", "password123")
            assertTrue((session.state.value as SessionState.NeedsVerification).verificationEmailFailed)

            auth.failures.clear()
            session.resendVerificationEmail()
            assertFalse((session.state.value as SessionState.NeedsVerification).verificationEmailFailed)
        }
    }

    @Test
    fun `sign up with email in use is reported`() = runTest {
        val auth = FakeAuthDataSource().apply { failures["createUserWithEmail"] = AuthError.EMAIL_IN_USE }
        withSession(auth) { session ->
            expectError(AuthError.EMAIL_IN_USE) { session.signUp("Asha", "a@b.co", "password123") }
            assertTrue(profiles.calls.isEmpty())
        }
    }

    @Test
    fun `checking verification moves to signed in once verified`() = runTest {
        val user = passwordUser(verified = false)
        val auth = FakeAuthDataSource().apply { nextUser = user }
        withSession(auth) { session ->
            session.signInWithEmail("a@b.co", "password123")
            assertFalse(session.checkEmailVerified())
            assertTrue(session.state.value is SessionState.NeedsVerification)

            auth.userAfterReload = user.copy(isEmailVerified = true)
            assertTrue(session.checkEmailVerified())
            assertTrue(session.state.value is SessionState.SignedIn)
            // The rules read the token, so verifying gets a fresh one at once.
            assertTrue("refreshIdToken" in auth.calls)
        }
    }

    @Test
    fun `a signed-in user can be sent to verification and comes back once verified`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        withSession(auth) { session ->
            session.start()
            val signedIn = session.state.value as SessionState.SignedIn
            session.showEmailVerification()
            assertEquals(SessionState.NeedsVerification(signedIn.profile), session.state.value)

            assertTrue(session.checkEmailVerified())
            assertTrue(session.state.value is SessionState.SignedIn)
        }
    }

    @Test
    fun `verification is only shown from signed in`() = runTest {
        withSession(FakeAuthDataSource()) { session ->
            session.start()
            session.showEmailVerification()
            assertEquals(SessionState.SignedOut(), session.state.value)
        }
    }

    // Sign out and delete

    @Test
    fun `sign out closes screens and stops listeners before wiping the cache`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        val listeners = ListenerRegistry()
        var stopped = 0
        listeners.register { stopped++ }
        var stateAtClear: SessionState? = null
        var stoppedAtClear = -1
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val session = SessionManager(auth, profiles, google, scope, listeners)
            profiles.onClear = {
                stateAtClear = session.state.value
                stoppedAtClear = stopped
            }
            session.start()
            session.signOut()
            assertEquals(SessionState.SignedOut(), stateAtClear)
            assertEquals(1, stoppedAtClear)
            assertEquals(0, listeners.openCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `deleting an account cancels upcoming bookings first, and stops if that fails`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        var cancelFailure: AuthError? = AuthError.NETWORK
        val steps = mutableListOf<String>()
        try {
            val session = SessionManager(auth, profiles, google, scope, cancelUpcomingBookings = {
                steps += "cancelUpcoming"
                cancelFailure?.let { throw AuthException(it) }
            })
            session.start()
            expectError(AuthError.NETWORK) { session.deleteAccount(Reauth.Password("password123")) }
            assertFalse("deleteProfile" in profiles.calls)
            assertFalse("deleteUser" in auth.calls)
            assertTrue(session.state.value is SessionState.SignedIn)

            cancelFailure = null
            session.deleteAccount(Reauth.Password("password123"))
            assertEquals(listOf("cancelUpcoming", "cancelUpcoming"), steps)
            assertTrue("deleteProfile" in profiles.calls)
            assertEquals(SessionState.SignedOut(), session.state.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a listener started after sign-out is stopped at once, and the next sign-in accepts listeners again`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        val listeners = ListenerRegistry()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val session = SessionManager(auth, profiles, google, scope, listeners)
            session.start()
            session.signOut()
            var lateStopped = 0
            listeners.register { lateStopped++ }
            assertEquals(1, lateStopped)
            assertEquals(0, listeners.openCount)

            session.signInWithEmail("uid-1@example.com", "password123")
            assertTrue(session.state.value is SessionState.SignedIn)
            var stopped = 0
            listeners.register { stopped++ }
            assertEquals(0, stopped)
            assertEquals(1, listeners.openCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `an external sign-out also closes the listener registry`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        val listeners = ListenerRegistry()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val session = SessionManager(auth, profiles, google, scope, listeners)
            session.start()
            assertFalse(listeners.isClosed)
            auth.signOutExternally()
            assertEquals(SessionState.SignedOut(), session.state.value)
            assertTrue(listeners.isClosed)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a listener that ended on its own is not stopped again`() {
        val listeners = ListenerRegistry()
        var stopped = 0
        listeners.register { stopped++ }.release()
        listeners.stopAll()
        assertEquals(0, stopped)
    }

    @Test
    fun `sign out clears firebase and credential manager`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        withSession(auth) { session ->
            session.start()
            session.signOut()
            assertNull(auth.currentUser)
            assertEquals(1, google.clearCount)
            assertEquals(1, profiles.clearCount)
            assertEquals(SessionState.SignedOut(), session.state.value)
        }
    }

    /** Runs [block] with a session that records the state each reminder wipe saw. */
    private suspend fun TestScope.withReminderWipes(
        auth: FakeAuthDataSource,
        block: suspend (SessionManager, List<SessionState>) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val seen = mutableListOf<SessionState>()
        lateinit var session: SessionManager
        session = SessionManager(auth, profiles, google, scope, clearReminders = { seen += session.state.value })
        try {
            block(session, seen)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `sign out wipes reminders after signed out is published`() = runTest {
        withReminderWipes(FakeAuthDataSource(passwordUser())) { session, seen ->
            session.start()
            session.signOut()
            assertEquals(listOf(SessionState.SignedOut()), seen)
        }
    }

    @Test
    fun `account deletion wipes reminders`() = runTest {
        withReminderWipes(FakeAuthDataSource(passwordUser())) { session, seen ->
            session.start()
            session.deleteAccount(Reauth.Password("password123"))
            assertEquals(listOf(SessionState.SignedOut()), seen)
        }
    }

    @Test
    fun `an external sign out wipes reminders`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        withReminderWipes(auth) { session, seen ->
            session.start()
            auth.signOutExternally()
            assertEquals(listOf(SessionState.SignedOut()), seen)
        }
    }

    @Test
    fun `a failing reminder wipe doesn't stop sign out or the cache wipe`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val session = SessionManager(auth, profiles, google, scope, clearReminders = { error("Disk full") })
            session.start()
            session.signOut()
            assertEquals(SessionState.SignedOut(), session.state.value)
            assertEquals(1, profiles.clearCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `failed profile load clears local data when signing out`() = runTest {
        profiles.ensureError = AuthError.NETWORK
        withSession(FakeAuthDataSource()) { session ->
            expectError(AuthError.PROFILE_LOAD_FAILED) { session.signInWithEmail("a@b.co", "password123") }
            assertEquals(1, profiles.clearCount)
        }
    }

    @Test
    fun `offline cold start keeps local data`() = runTest {
        profiles.getError = AuthError.NETWORK
        withSession(FakeAuthDataSource(passwordUser())) { session ->
            session.start()
            assertEquals(SessionState.ProfileUnavailable, session.state.value)
            assertEquals(0, profiles.clearCount)
        }
    }

    @Test
    fun `external sign out returns to signed out`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        withSession(auth) { session ->
            session.start()
            assertTrue(session.state.value is SessionState.SignedIn)
            auth.signOutExternally()
            assertEquals(SessionState.SignedOut(), session.state.value)
            assertEquals(1, profiles.clearCount)
        }
    }

    @Test
    fun `delete account reauthenticates before deleting anything`() = runTest {
        val user = passwordUser()
        val auth = FakeAuthDataSource(user)
        withSession(auth) { session ->
            session.start()
            session.deleteAccount(Reauth.Password("password123"))
            assertEquals(listOf("reauthenticate", "deleteUser"), auth.calls)
            assertFalse(user.uid in profiles.profiles)
            assertEquals(SessionState.SignedOut(), session.state.value)
            assertEquals(1, google.clearCount)
            assertEquals(1, profiles.clearCount)
        }
    }

    @Test
    fun `failed reauthentication keeps the profile`() = runTest {
        val user = passwordUser()
        profiles.profiles[user.uid] = doctor(user.uid)
        val auth = FakeAuthDataSource(user).apply { failures["reauthenticate"] = AuthError.INVALID_CREDENTIALS }
        withSession(auth) { session ->
            session.start()
            expectError(AuthError.INVALID_CREDENTIALS) { session.deleteAccount(Reauth.Password("nope")) }
            assertEquals(doctor(user.uid), profiles.profiles[user.uid])
            assertTrue(session.state.value is SessionState.SignedIn)
        }
    }

    @Test
    fun `google account deletes with google reauthentication`() = runTest {
        val user = googleUser()
        val auth = FakeAuthDataSource(user)
        withSession(auth) { session ->
            session.start()
            session.deleteAccount(Reauth.Google("token"))
            assertEquals(listOf("reauthenticateWithGoogle", "deleteUser"), auth.calls)
        }
    }

    @Test
    fun `sign out during an in-flight profile load never publishes signed in`() = runTest {
        val user = passwordUser()
        val auth = FakeAuthDataSource(user)
        val slowProfiles = object : com.medhome.nepal.data.ProfileStore by profiles {
            override suspend fun getProfile(uid: String): UserProfile {
                auth.signOutExternally()
                return doctor(uid)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val session = SessionManager(auth, slowProfiles, google, scope)
            session.start()
            assertEquals(SessionState.SignedOut(), session.state.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `external sign out on the verify screen signs out and clears data`() = runTest {
        val user = passwordUser(verified = false)
        val auth = FakeAuthDataSource().apply { nextUser = user }
        withSession(auth) { session ->
            session.start()
            session.signInWithEmail("a@b.co", "password123")
            auth.signOutExternally()
            assertEquals(SessionState.SignedOut(), session.state.value)
            assertEquals(1, profiles.clearCount)
        }
    }

    @Test
    fun `updating the profile saves it and keeps the role`() = runTest {
        val user = passwordUser()
        profiles.profiles[user.uid] = doctor(user.uid)
        withSession(FakeAuthDataSource(user)) { session ->
            session.start()
            session.updateProfile(ProfileDetails("Dr Sita Rai", "9841234567", "1990-05-01", Gender.FEMALE))
            val profile = (session.state.value as SessionState.SignedIn).profile
            assertEquals("Dr Sita Rai", profile.name)
            assertEquals("9841234567", profile.phone)
            assertEquals(Role.DOCTOR, profile.role)
            assertEquals(profile, profiles.profiles[user.uid])
        }
    }

    @Test
    fun `a failed profile update leaves the profile unchanged`() = runTest {
        val user = passwordUser()
        profiles.profiles[user.uid] = doctor(user.uid)
        profiles.updateDetailsError = AuthError.NETWORK
        withSession(FakeAuthDataSource(user)) { session ->
            session.start()
            expectError(AuthError.NETWORK) { session.updateProfile(ProfileDetails("Someone Else", null, null, null)) }
            assertEquals("Dr Rai", (session.state.value as SessionState.SignedIn).profile.name)
        }
    }

    @Test
    fun `updating the profile requires being signed in`() = runTest {
        withSession(FakeAuthDataSource()) { session ->
            session.start()
            expectError(AuthError.NOT_SIGNED_IN) { session.updateProfile(ProfileDetails("Asha", null, null, null)) }
            assertFalse("updateDetails" in profiles.calls)
        }
    }

    @Test
    fun `changing the password reauthenticates before updating`() = runTest {
        val auth = FakeAuthDataSource(passwordUser())
        withSession(auth) { session ->
            session.start()
            session.changePassword("old-password", "new-password1")
            assertEquals(listOf("reauthenticate", "updatePassword"), auth.calls)
        }
    }

    @Test
    fun `a wrong current password never updates the password`() = runTest {
        val auth = FakeAuthDataSource(passwordUser()).apply { failures["reauthenticate"] = AuthError.INVALID_CREDENTIALS }
        withSession(auth) { session ->
            session.start()
            expectError(AuthError.INVALID_CREDENTIALS) { session.changePassword("wrong", "new-password1") }
            assertFalse("updatePassword" in auth.calls)
        }
    }

    @Test
    fun `clearing the signed out error keeps the user signed out`() = runTest {
        profiles.getError = AuthError.PERMISSION_DENIED
        withSession(FakeAuthDataSource(passwordUser())) { session ->
            session.start()
            session.clearSignedOutError()
            assertEquals(SessionState.SignedOut(), session.state.value)
        }
    }
}
