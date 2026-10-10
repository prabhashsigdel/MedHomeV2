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
import com.google.firebase.firestore.Source
import com.medhome.nepal.domain.AdminError
import com.medhome.nepal.domain.AdminException
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.CancelledBy
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.DoctorAppointment
import com.medhome.nepal.domain.ManagedDoctor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * IDs of upcoming bookings, and where the next page starts (null: this was the last page). A
 * doctor's booked appointments never share a start time (the slot lock makes them unique), so a
 * start time is a safe cursor, and a booking left booked (a failed cancel) doesn't hide the rest.
 */
data class UpcomingPage(val bookingIds: List<String>, val nextAfterMillis: Long?)

/** Every well-formed doctor, active or not, and whether the list came from the cache. */
data class ManagedDoctorsSnapshot(val doctors: List<ManagedDoctor>, val fromCache: Boolean)

sealed interface ManagedDoctorLookup {
    data class Found(val doctor: ManagedDoctor, val fromCache: Boolean) : ManagedDoctorLookup

    /** Missing or malformed. [fromCache]: offline, so it may just not be cached yet. */
    data class Missing(val fromCache: Boolean) : ManagedDoctorLookup
}

/**
 * What admins do with the doctors catalogue: list and read every doctor, add and edit them,
 * show or hide them from patients, and see and cancel their upcoming bookings. The rules
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

    /**
     * One page of [doctorId]'s booked appointments starting after [afterMillis] (or now, if
     * later), soonest first. Pass the page's [UpcomingPage.nextAfterMillis] for the next page.
     * Needs the server.
     */
    suspend fun upcomingBookingPage(doctorId: String, afterMillis: Long?): UpcomingPage

    /**
     * Cancels one booking for the clinic (`cancelledBy` "clinic"), freeing its slot lock and the
     * patient's quota place in the same write. Returns false when it was already cancelled
     * (nothing to do). Started: BOOKING_STARTED. Needs the server.
     */
    suspend fun cancelBooking(bookingId: String): Boolean
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
        listen({ doctors(firestore()) }) { snapshot ->
            ManagedDoctorsSnapshot(
                doctors = snapshot.documents.mapNotNull { DoctorMapper.parseManaged(it.id, it.data) }.sortedWith(byName),
                fromCache = snapshot.metadata.isFromCache,
            )
        }

    override fun doctor(id: String): Flow<ManagedDoctorLookup> {
        // Only our own document IDs are valid; anything else (a path, say) never reaches Firestore.
        if (!Doctor.isValidId(id)) return flowOf(ManagedDoctorLookup.Missing(fromCache = false))
        return snapshotFlow<DocumentSnapshot, ManagedDoctorLookup>(
            listeners = listeners,
            attach = { listener -> doctors(firestore()).document(id).addSnapshotListener(MetadataChanges.INCLUDE, listener) },
        ) { snapshot, error ->
            if (error != null) {
                close(AuthErrorMapper.toException(error))
                return@snapshotFlow
            }
            if (snapshot == null) return@snapshotFlow
            val fromCache = snapshot.metadata.isFromCache
            val doctor = if (snapshot.exists()) DoctorMapper.parseManaged(snapshot.id, snapshot.data) else null
            trySend(if (doctor != null) ManagedDoctorLookup.Found(doctor, fromCache) else ManagedDoctorLookup.Missing(fromCache))
        }
    }

    override fun upcomingAppointments(doctorId: String): Flow<List<DoctorAppointment>> {
        // Never a path: an invalid ID fails like a missing doctor's list would.
        if (!Doctor.isValidId(doctorId)) return flow { throw AuthException(AuthError.UNKNOWN) }
        // The first name comes from the booking's own patientName: no patient profile is read
        // (or cached on this phone).
        return listen({ upcomingQuery(doctorId).limit(MAX_APPOINTMENTS_READ) }) { snapshot ->
            snapshot.documents.mapNotNull { BookingMapper.parseForDoctor(it.id, it.data, doctorId) }
        }
    }

    override suspend fun upcomingBookingPage(doctorId: String, afterMillis: Long?): UpcomingPage {
        if (!Doctor.isValidId(doctorId)) throw AdminException(AdminError.NOT_FOUND)
        return write {
            val documents = upcomingQuery(doctorId, afterMillis).limit(MAX_PAGE_READ).get(Source.SERVER).await().documents
            val last = documents.lastOrNull()?.getTimestamp(BookingMapper.FIELD_START_AT)
            UpcomingPage(
                bookingIds = documents.map { it.id }.filter(BookingMapper::isValidId),
                nextAfterMillis = if (documents.size < MAX_PAGE_READ) null else last?.toDate()?.time,
            )
        }
    }

    override suspend fun cancelBooking(bookingId: String): Boolean = write {
        signedInUid()
        if (!BookingMapper.isValidId(bookingId)) throw AdminException(AdminError.NOT_FOUND)
        val db = firestore()
        val bookingRef = db.collection(COLLECTION_BOOKINGS).document(bookingId)
        db.runTransaction { transaction ->
            // Re-read: the list may be stale (cancelled by the patient meanwhile).
            val (patientUid, booking) = BookingMapper.parseAnyPatient(bookingId, transaction.get(bookingRef).data)
                ?: throw AdminException(AdminError.NOT_FOUND)
            if (booking.status == BookingStatus.CANCELLED) return@runTransaction false
            if (booking.startAtMillis <= clock()) throw AdminException(AdminError.BOOKING_STARTED)
            val lockRef = db.collection(COLLECTION_LOCKS).document(booking.slotId)
            val placeRef = db.collection(COLLECTION_USERS).document(patientUid)
                .collection(COLLECTION_QUOTA).document(booking.quotaPlace.toString())
            // As a patient cancel: delete only what exists, and only the place holding this booking.
            val lockExists = transaction.get(lockRef).exists()
            val placeHeld = transaction.get(placeRef).getString(FIELD_QUOTA_BOOKING_ID) == bookingId
            transaction.update(
                bookingRef,
                mapOf(
                    BookingMapper.FIELD_STATUS to BookingStatus.CANCELLED.key,
                    BookingMapper.FIELD_CANCELLED_AT to FieldValue.serverTimestamp(),
                    BookingMapper.FIELD_CANCELLED_BY to CancelledBy.CLINIC.key,
                ),
            )
            if (lockExists) transaction.delete(lockRef)
            if (placeHeld) transaction.delete(placeRef)
            true
        }.await()
    }

    override suspend fun upcomingCount(doctorId: String): Int {
        if (!Doctor.isValidId(doctorId)) throw AdminException(AdminError.NOT_FOUND)
        return write {
            upcomingQuery(doctorId).count().get(AggregateSource.SERVER).await().count.toInt()
        }
    }

    override suspend fun createDoctor(doctor: Doctor) {
        write {
            val uid = signedInUid()
            val id = checkedId(doctor.id)
            val db = firestore()
            val ref = doctors(db).document(id)
            db.runTransaction { transaction ->
                // Never overwrite: an existing doctor is edited with updateDoctor.
                if (transaction.get(ref).exists()) throw AdminException(AdminError.ALREADY_EXISTS)
                transaction.set(ref, DoctorMapper.toFields(doctor) + (DoctorMapper.FIELD_ACTIVE to true) + stamp(uid))
                null
            }.await()
        }
    }

    override suspend fun updateDoctor(doctor: Doctor) {
        write {
            val uid = signedInUid()
            val id = checkedId(doctor.id)
            val db = firestore()
            val ref = doctors(db).document(id)
            db.runTransaction { transaction ->
                requireExists(transaction.get(ref))
                // update() replaces each field whole (the schedule map too), and leaves `active` alone.
                transaction.update(ref, DoctorMapper.toFields(doctor) + stamp(uid))
                null
            }.await()
        }
    }

    override suspend fun setActive(doctorId: String, active: Boolean) {
        write {
            val uid = signedInUid()
            val id = checkedId(doctorId)
            val db = firestore()
            val ref = doctors(db).document(id)
            db.runTransaction { transaction ->
                requireExists(transaction.get(ref))
                transaction.update(ref, mapOf(DoctorMapper.FIELD_ACTIVE to active) + stamp(uid))
                null
            }.await()
        }
    }

    /**
     * Booked appointments of [doctorId] starting after now (or after [afterMillis], if later),
     * soonest first (needs the composite index).
     */
    private fun upcomingQuery(doctorId: String, afterMillis: Long? = null): Query =
        firestore().collection(COLLECTION_BOOKINGS)
            .whereEqualTo(BookingMapper.FIELD_DOCTOR_ID, doctorId)
            .whereEqualTo(BookingMapper.FIELD_STATUS, BookingStatus.BOOKED.key)
            .whereGreaterThan(BookingMapper.FIELD_START_AT, Timestamp(Date(maxOf(clock(), afterMillis ?: Long.MIN_VALUE))))
            .orderBy(BookingMapper.FIELD_START_AT, Query.Direction.ASCENDING)

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

    /**
     * Runs a write or server read, turning every failure into an [AdminException]. Getting the
     * Firestore instance and building references happen inside it too: after sign-out the
     * instance is terminated and they throw IllegalStateException, which must not escape.
     */
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

    private fun <T> listen(query: () -> Query, map: (QuerySnapshot) -> T): Flow<T> = queryFlow(listeners, query, map)

    private fun doctors(db: FirebaseFirestore): CollectionReference = db.collection(COLLECTION_DOCTORS)

    private companion object {
        const val COLLECTION_DOCTORS = "doctors"
        const val COLLECTION_BOOKINGS = "bookings"
        const val COLLECTION_USERS = "users"
        const val COLLECTION_LOCKS = "slotLocks"
        const val COLLECTION_QUOTA = "bookingQuota"
        const val FIELD_QUOTA_BOOKING_ID = "bookingId"
        const val MAX_APPOINTMENTS_READ = 100L
        /** One page of bookings to cancel. */
        const val MAX_PAGE_READ = 200L
        const val MAX_CAUSE_DEPTH = 5
        val byName: Comparator<ManagedDoctor> = compareBy(String.CASE_INSENSITIVE_ORDER) { it.doctor.name }
    }
}
