package com.medhome.nepal.data

import com.medhome.nepal.domain.ProfileDetails
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

    /**
     * Saves the user-editable fields; null optional fields are removed. Needs the server: fails
     * straight away when offline rather than queueing.
     */
    suspend fun updateDetails(uid: String, details: ProfileDetails)

    /**
     * Wipes Firestore's on-device cache so a signed-out user's data doesn't stay on the phone.
     * Cache reads bypass security rules, so this matters on shared devices.
     */
    suspend fun clearLocalData()
}
