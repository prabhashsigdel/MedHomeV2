package com.medhome.nepal.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.medhome.nepal.domain.AdminError
import com.medhome.nepal.domain.AdminException
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.DoctorAppointment
import com.medhome.nepal.domain.ManagedDoctor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.Date

/** Every well-formed doctor, active or not, and whether the list came from the cache. */
data class ManagedDoctorsSnapshot(val doctors: List<ManagedDoctor>, val fromCache: Boolean)

sealed interface ManagedDoctorLookup {
    data class Found(val doctor: ManagedDoctor, val fromCache: Boolean) : ManagedDoctorLookup

    /** Missing or malformed. [fromCache]: offline, so it may just not be cached yet. */
    data class Missing(val fromCache: Boolean) : ManagedDoctorLookup
}

/**
 * What admins do with the doctors catalogue: list and read every doctor, add and edit them,
 * show or hide them from patients, and see their upcoming bookings (read-only). The rules
 * enforce all of it (`isAdmin()`); the app only offers it to admins. Flows end with an
 * [com.medhome.nepal.domain.AuthException] on a failure; writes throw [AdminException].
 */
interface AdminRepository {
    /** Every doctor, active or not, sorted by name. Live. */
    fun allDoctors(): Flow<ManagedDoctorsSnapshot>

    /** One doctor, active or not. Live. */
    fun doctor(id: String): Flow<ManagedDoctorLookup>

    /** [doctorId]'s booked appointments from now on, soonest first. Live. */
    fun upcomingAppointments(doctorId: String): Flow<List<DoctorAppointment>>

    /** How many booked appointments [doctorId] has from now on. Needs the server. */
    suspend fun upcomingCount(doctorId: String): Int

    /** Adds [doctor] (active) under its own ID. Fails with ALREADY_EXISTS if the ID is taken. */
    suspend fun createDoctor(doctor: Doctor)

    /** Replaces [doctor]'s details; `active` stays as it is. Fails with NOT_FOUND if missing. */
    suspend fun updateDoctor(doctor: Doctor)

    /** Shows ([active]) or hides the doctor from patients. Bookings are left as they are. */
    suspend fun setActive(doctorId: String, active: Boolean)
}

/**
 * Firestore implementation. Writes are transactions, which fail at once offline instead of
 * queueing (a queued admin edit would look saved but could be refused later). [firestore] is
 * asked for on every call (sign-out terminates the instance); every listener registers with
 * [listeners].
 */
class FirestoreAdminRepository(
    private val firestore: () -> FirebaseFirestore,
    private val currentUid: () -> String?,
    private val listeners: ListenerRegistry,
    private val clock: () -> Long = System::currentTimeMillis,
) : AdminRepository {

    override fun allDoctors(): Flow<ManagedDoctorsSnapshot> =
        listen(doctors(firestore())) { snapshot ->
            ManagedDoctorsSnapshot(
                doctors = snapshot.documents.mapNotNull { DoctorMapper.parseManaged(it.id, it.data) }.sortedWith(byName),
                fromCache = snapshot.metadata.isFromCache,
            )
        }

    override fun doctor(id: String): Flow<ManagedDoctorLookup> {
        // Only our own document IDs are valid; anything else (a path, say) never reaches Firestore.
        if (!Doctor.isValidId(id)) return flowOf(ManagedDoctorLookup.Missing(fromCache = false))
        return callbackFlow {
            val registration = doctors(firestore()).document(id)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        close(AuthErrorMapper.toException(error))
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    val fromCache = snapshot.metadata.isFromCache
                    val doctor = if (snapshot.exists()) DoctorMapper.parseManaged(snapshot.id, snapshot.data) else null
                    trySend(if (doctor != null) ManagedDoctorLookup.Found(doctor, fromCache) else ManagedDoctorLookup.Missing(fromCache))
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
    }

    override fun upcomingAppointments(doctorId: String): Flow<List<DoctorAppointment>> {
        // Never a path: an invalid ID fails like a missing doctor's list would.
        if (!Doctor.isValidId(doctorId)) return flow { throw AuthException(AuthError.UNKNOWN) }
        val query = upcomingQuery(doctorId).limit(MAX_APPOINTMENTS_READ)
        return channelFlow {
            // Rows show at once; each patient's first name is read in the background (once per
            // patient while the list is open; a failed or slow read is retried on the next update)
            // and the list is sent again when it arrives. Everything here runs on the collector's
            // dispatcher, one coroutine at a time between suspensions.
            val names = HashMap<String, String>()
            val reading = HashSet<String>()
            var latest = emptyList<DoctorBookingRow>()
            fun current() = latest.map { DoctorAppointment(it.id, it.startAtMillis, it.patientUid?.let(names::get)) }
            listen(query) { snapshot -> snapshot.documents.mapNotNull { BookingMapper.parseForDoctor(it.id, it.data, doctorId) } }
                .collect { rows ->
                    latest = rows
                    send(current())
                    val unread = rows.mapNotNull { it.patientUid }.distinct().filter { it !in names && reading.add(it) }
                    unread.forEach { uid ->
                        launch {
                            val name = withTimeoutOrNull(NAME_READ_TIMEOUT_MS) { firstNameOf(uid) }
                            reading.remove(uid)
                            if (name != null) {
                                names[uid] = name
                                send(current())
                            }
                        }
                    }
                }
        }
    }

    override suspend fun upcomingCount(doctorId: String): Int {
        if (!Doctor.isValidId(doctorId)) throw AdminException(AdminError.NOT_FOUND)
        return write {
            upcomingQuery(doctorId).count().get(AggregateSource.SERVER).await().count.toInt()
        }
    }

    override suspend fun createDoctor(doctor: Doctor) {
        val uid = signedInUid()
        val db = firestore()
        val ref = doctors(db).document(checkedId(doctor.id))
        write {
            db.runTransaction { transaction ->
                // Never overwrite: an existing doctor is edited with updateDoctor.
                if (transaction.get(ref).exists()) throw AdminException(AdminError.ALREADY_EXISTS)
                transaction.set(ref, DoctorMapper.toFields(doctor) + (DoctorMapper.FIELD_ACTIVE to true) + stamp(uid))
                null
            }.await()
        }
    }

    override suspend fun updateDoctor(doctor: Doctor) {
        val uid = signedInUid()
        val db = firestore()
        val ref = doctors(db).document(checkedId(doctor.id))
        write {
            db.runTransaction { transaction ->
                requireExists(transaction.get(ref))
                // update() replaces each field whole (the schedule map too), and leaves `active` alone.
                transaction.update(ref, DoctorMapper.toFields(doctor) + stamp(uid))
                null
            }.await()
        }
    }

    override suspend fun setActive(doctorId: String, active: Boolean) {
        val uid = signedInUid()
        val db = firestore()
        val ref = doctors(db).document(checkedId(doctorId))
        write {
            db.runTransaction { transaction ->
                requireExists(transaction.get(ref))
                transaction.update(ref, mapOf(DoctorMapper.FIELD_ACTIVE to active) + stamp(uid))
                null
            }.await()
        }
    }

    /** Booked appointments of [doctorId] starting after now, soonest first (needs the composite index). */
    private fun upcomingQuery(doctorId: String): Query =
        firestore().collection(COLLECTION_BOOKINGS)
            .whereEqualTo(BookingMapper.FIELD_DOCTOR_ID, doctorId)
            .whereEqualTo(BookingMapper.FIELD_STATUS, BookingStatus.BOOKED.key)
            .whereGreaterThan(BookingMapper.FIELD_START_AT, Timestamp(Date(clock())))
            .orderBy(BookingMapper.FIELD_START_AT, Query.Direction.ASCENDING)

    /**
     * A patient's first name, or null when it can't be read. The whole profile is fetched (the
     * rules let admins read profiles; there is no narrower document), but only the first word of
     * the name leaves this function, and nothing is logged.
     */
    private suspend fun firstNameOf(uid: String): String? {
        val name = try {
            firestore().collection(COLLECTION_USERS).document(uid).get().await().getString(FIELD_USER_NAME)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return firstWord(name)
    }

    private fun firstWord(name: String?): String? =
        DoctorMapper.cleanLine(name, DoctorMapper.MAX_NAME_LENGTH)?.substringBefore(' ')?.takeIf { it.isNotEmpty() }

    private fun signedInUid(): String = currentUid() ?: throw AdminException(AdminError.PERMISSION_DENIED)

    private fun checkedId(id: String): String = id.takeIf(Doctor::isValidId) ?: throw AdminException(AdminError.NOT_FOUND)

    private fun requireExists(snapshot: DocumentSnapshot) {
        if (!snapshot.exists()) throw AdminException(AdminError.NOT_FOUND)
    }

    /** Who changed the doctor and when (server time); the rules require both on every write. */
    private fun stamp(uid: String): Map<String, Any> = mapOf(
        DoctorMapper.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        DoctorMapper.FIELD_UPDATED_BY to uid,
    )

    /** Runs a write or server read, turning every failure into an [AdminException]. */
    private suspend fun <T> write(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.adminException() ?: AdminException(adminError(e), e)
    }

    private fun adminError(e: Exception): AdminError = when (AuthErrorMapper.map(e)) {
        AuthError.NETWORK -> AdminError.NETWORK
        AuthError.PERMISSION_DENIED -> AdminError.PERMISSION_DENIED
        else -> AdminError.UNKNOWN
    }

    /** Our own errors thrown inside a transaction may come back wrapped. */
    private fun Throwable.adminException(): AdminException? =
        generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).filterIsInstance<AdminException>().firstOrNull()

    private fun <T> listen(query: Query, map: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                close(AuthErrorMapper.toException(error))
                return@addSnapshotListener
            }
            if (snapshot != null) trySend(map(snapshot))
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

    private fun doctors(db: FirebaseFirestore): CollectionReference = db.collection(COLLECTION_DOCTORS)

    private companion object {
        const val COLLECTION_DOCTORS = "doctors"
        const val COLLECTION_BOOKINGS = "bookings"
        const val COLLECTION_USERS = "users"
        const val FIELD_USER_NAME = "name"
        const val MAX_APPOINTMENTS_READ = 100L
        /** A name that takes longer (offline, say) shows as "Patient" until the next update. */
        const val NAME_READ_TIMEOUT_MS = 5_000L
        const val MAX_CAUSE_DEPTH = 5
        val byName: Comparator<ManagedDoctor> = compareBy(String.CASE_INSENSITIVE_ORDER) { it.doctor.name }
    }
}
