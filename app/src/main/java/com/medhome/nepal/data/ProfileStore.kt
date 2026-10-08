package com.medhome.nepal.data

import com.medhome.nepal.domain.UserProfile

/** Every method throws [com.medhome.nepal.domain.AuthException] on failure. */
interface ProfileStore {
    /**
     * Reads users/{uid}, falling back to the local cache when offline.
     * Returns null only when the server confirms the document does not exist.
     */
    suspend fun getProfile(uid: String): UserProfile?

    /** Returns the existing profile, or creates a patient profile if none exists. Needs the server. */
    suspend fun ensureProfile(uid: String, name: String, email: String): UserProfile

    suspend fun deleteProfile(uid: String)
}
