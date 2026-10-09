package com.medhome.nepal.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.Doctor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/** Active doctors, and whether they came from the on-device cache (offline, or not synced yet). */
data class DoctorsSnapshot(val doctors: List<Doctor>, val fromCache: Boolean)

sealed interface DoctorLookup {
    data class Found(val doctor: Doctor, val fromCache: Boolean) : DoctorLookup

    /** Missing, inactive or unusable. [fromCache]: offline, so it may just not be cached yet. */
    data class Unavailable(val fromCache: Boolean) : DoctorLookup
}

/**
 * The doctors catalogue (read-only for the app: security rules deny every client write). Both
 * flows are live and end with an [com.medhome.nepal.domain.AuthException] on a failure (a
 * denied single doctor is reported as unavailable instead: the rules hide inactive doctors).
 * Offline, Firestore's on-device cache answers and keeps listening.
 */
interface DoctorRepository {
    /** Every active, well-formed doctor, sorted by name. Asks for active ones only, as the rules require. */
    fun activeDoctors(): Flow<DoctorsSnapshot>

    /** One doctor; unavailable when inactive, missing or [id] isn't a valid doctor ID. */
    fun doctor(id: String): Flow<DoctorLookup>
}

/**
 * [firestore] is asked for on every listen: signing out terminates the current instance and
 * FirebaseFirestore.getInstance() then returns a new one. Listeners only run while a screen
 * collects, and sign-out stops any still open through [listeners] before it shuts Firestore down.
 */
class FirestoreDoctorRepository(
    private val firestore: () -> FirebaseFirestore,
    private val listeners: ListenerRegistry,
) : DoctorRepository {

    override fun activeDoctors(): Flow<DoctorsSnapshot> = callbackFlow {
        val registration = firestore().collection(COLLECTION_DOCTORS)
            .whereEqualTo(DoctorMapper.FIELD_ACTIVE, true)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(AuthErrorMapper.toException(error))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val doctors = snapshot.documents
                    .mapNotNull { DoctorMapper.parse(it.id, it.data) }
                    .sortedWith(byName)
                trySend(DoctorsSnapshot(doctors, fromCache = snapshot.metadata.isFromCache))
            }
        val stop = listeners.register {
            registration.remove()
            channel.close()
        }
        awaitClose {
            stop.release()
            registration.remove()
        }
    }

    override fun doctor(id: String): Flow<DoctorLookup> {
        // Only our own document IDs are valid; anything else (a path, say) never reaches Firestore.
        if (!Doctor.isValidId(id)) return flowOf(DoctorLookup.Unavailable(fromCache = false))
        return listenToDoctor(id)
    }

    private fun listenToDoctor(id: String): Flow<DoctorLookup> = callbackFlow {
        val registration = firestore().collection(COLLECTION_DOCTORS).document(id)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    val mapped = AuthErrorMapper.toException(error)
                    // Rules deny reading an inactive or missing doctor: to the user, it's unavailable.
                    if (mapped.error == AuthError.PERMISSION_DENIED) {
                        trySend(DoctorLookup.Unavailable(fromCache = false))
                        close()
                    } else {
                        close(mapped)
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val fromCache = snapshot.metadata.isFromCache
                val doctor = if (snapshot.exists()) DoctorMapper.parse(snapshot.id, snapshot.data) else null
                trySend(if (doctor != null) DoctorLookup.Found(doctor, fromCache) else DoctorLookup.Unavailable(fromCache))
            }
        val stop = listeners.register {
            registration.remove()
            channel.close()
        }
        awaitClose {
            stop.release()
            registration.remove()
        }
    }

    private companion object {
        const val COLLECTION_DOCTORS = "doctors"
        val byName: Comparator<Doctor> = compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
}
