package com.medhome.nepal.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.domain.Role
import com.medhome.nepal.domain.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [firestore] is asked for on every call: [clearLocalData] terminates the current instance and
 * FirebaseFirestore.getInstance() then returns a new one.
 */
class FirestoreProfileStore(
    private val firestore: () -> FirebaseFirestore,
) : ProfileStore {

    override suspend fun getProfile(uid: String): UserProfile? {
        val snapshot = try {
            mapErrors { document(uid).get().await() }
        } catch (e: AuthException) {
            if (e.error != AuthError.NETWORK) throw e
            readFromCache(uid) ?: throw e
        }
        if (!snapshot.exists()) {
            // A cached "missing" is not proof that the server has no profile.
            if (snapshot.metadata.isFromCache) throw AuthException(AuthError.NETWORK)
            return null
        }
        return parse(uid, snapshot.data)
    }

    override suspend fun ensureProfile(uid: String, name: String, email: String): UserProfile {
        val ref = document(uid)
        // Transactions read from the server, so "missing" is a real answer, never a guess.
        val existing = mapErrors {
            firestore().runTransaction<Map<String, Any>?> { transaction ->
                val snapshot = transaction.get(ref)
                if (snapshot.exists()) {
                    snapshot.data
                } else {
                    transaction.set(
                        ref,
                        mapOf(
                            FIELD_NAME to name,
                            FIELD_EMAIL to email,
                            FIELD_ROLE to Role.PATIENT.id,
                            FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                        ),
                    )
                    null
                }
            }.await()
        }
        return if (existing != null) {
            parse(uid, existing)
        } else {
            UserProfile(uid = uid, name = name, email = email, role = Role.PATIENT)
        }
    }

    override suspend fun deleteProfile(uid: String) = mapErrors {
        document(uid).delete().await()
        Unit
    }

    override suspend fun updateName(uid: String, name: String) = mapErrors {
        val ref = document(uid)
        // A transaction rather than update(): offline, update() would queue and never resolve,
        // leaving the save spinner running; a transaction fails fast with UNAVAILABLE.
        firestore().runTransaction { transaction ->
            transaction.update(ref, FIELD_NAME, name)
            null
        }.await()
        Unit
    }

    override suspend fun clearLocalData() = mapErrors {
        val instance = firestore()
        instance.terminate().await()
        instance.clearPersistence().await()
        Unit
    }

    /** Returns null when the document is not in the local cache. */
    private suspend fun readFromCache(uid: String): DocumentSnapshot? = try {
        document(uid).get(Source.CACHE).await()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun document(uid: String) = firestore().collection(COLLECTION_USERS).document(uid)

    private fun parse(uid: String, data: Map<String, Any?>?): UserProfile {
        val role = Role.fromId(data?.get(FIELD_ROLE) as? String)
            ?: throw AuthException(AuthError.PROFILE_INVALID)
        return UserProfile(
            uid = uid,
            name = data?.get(FIELD_NAME) as? String ?: "",
            email = data?.get(FIELD_EMAIL) as? String ?: "",
            role = role,
        )
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val FIELD_NAME = "name"
        const val FIELD_EMAIL = "email"
        const val FIELD_ROLE = "role"
        const val FIELD_CREATED_AT = "createdAt"
    }
}
