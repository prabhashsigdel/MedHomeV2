package com.medhome.nepal.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.data.PasswordSaveOffers
import com.medhome.nepal.ui.common.LocalCredentialClient
import com.medhome.nepal.ui.motion.MotionTokens
import kotlinx.coroutines.delay

/**
 * Shows the system "save password" sheet for an offer queued by Login or Sign up. Lives at the
 * activity level because those screens are gone by the time the sign-in succeeds.
 * [signedIn] is true once the session is past the auth screens (verified or not).
 */
@Composable
fun PasswordSavePrompter(offers: PasswordSaveOffers, signedIn: Boolean) {
    val pending by offers.pending.collectAsStateWithLifecycle()
    val client = LocalCredentialClient.current
    val context = LocalContext.current
    LaunchedEffect(pending != null, signedIn) {
        if (pending == null) return@LaunchedEffect
        if (!signedIn) {
            // Signed out before we could ask: drop the password from memory.
            offers.discard()
            return@LaunchedEffect
        }
        // Let the screen transition finish so the sheet doesn't cover a half-drawn screen.
        delay(MotionTokens.SCREEN_MS.toLong())
        val offer = offers.take() ?: return@LaunchedEffect
        val outcome = client.savePassword(context, offer.email, offer.password)
        offers.record(offer.email, outcome)
    }
}
