package com.medhome.nepal.data

import android.content.Context

interface GoogleCredentialClient {
    /** Shows the Google account picker. Returns null if the user cancelled. */
    suspend fun requestIdToken(activityContext: Context): String?

    /** Forgets the selected account so the picker shows again next time. */
    suspend fun clearCredentialState()
}
