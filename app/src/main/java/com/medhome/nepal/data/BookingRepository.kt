package com.medhome.nepal.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.domain.Booking
import com.medhome.nepal.domain.BookingError
import com.medhome.nepal.domain.BookingException
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.CancelledBy
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.Slot
import com.medhome.nepal.domain.Slots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/** The signed-in patient's bookings, newest first, and whether they came from the cache. */
data class BookingsSnapshot(val bookings: List<Booking>, val fromCache: Boolean)

/**
 * Booking and cancelling, and the live reads behind them. Flows end with an [AuthException] on
 * a failure; [book] and [cancel] throw [BookingException].
 */
interface BookingRepository {
    /** IDs of [doctorId]'s locked (booked) slots starting within [window]. Live. */
    fun takenSlotIds(doctorId: String, window: LongRange): Flow<Set<String>>

    /** The signed-in patient's bookings, newest first. Live; works offline from the cache. */
    fun myBookings(): Flow<BookingsSnapshot>

    /** Whether the sign-in token says the email is verified (refreshing it once if not). */
    suspend fun hasVerifiedEmail(): Boolean

    /** Books [slot] and returns the new booking's ID. Needs the server. */
    suspend fun book(slot: Slot): String

    /** Cancels [booking], freeing its slot. Needs the server. */
    suspend fun cancel(booking: Booking)

    /** Cancels every upcoming booking of the signed-in patient (before deleting the account). */
    suspend fun cancelAllUpcoming()

    /**
     * Blanks the patient's name ([BookingMapper.DELETED_PATIENT_NAME]) on every booking of the
     * signed-in patient, past, upcoming or cancelled (deleting the account). Throws
     * [BookingException] unless every one is done. Needs the server.
     */
    suspend fun erasePatientName()
}

/**
 * Firestore implementation. A booking is three documents written in one transaction: the
 * booking, its slot lock and one of the patient's 3 quota places (see firestore.rules).
 * Transactions rather than batches: offline a batch would queue and never resolve, while a
 * transaction fails at once.
 *
 * [firestore] is asked for on every call (sign-out terminates the instance). [currentUid] and
 * [verifiedClaim] come from Firebase Auth; [clock] is the device clock, used only to skip work
 * the rules would refuse anyway (they use the server's time).
 */
class FirestoreBookingRepository(
    private val firestore: () -> FirebaseFirestore,
    private val currentUid: () -> String?,
    private val verifiedClaim: suspend (forceRefresh: Boolean) -> Boolean,
    private val listeners: ListenerRegistry,
    private val clock: () -> Long = System::currentTimeMillis,
) : BookingRepository {

    override fun takenSlotIds(doctorId: String, window: LongRange): Flow<Set<String>> {
        require(Doctor.isValidId(doctorId)) { "Not a doctor ID" }
        return listen(
            query = {
                firestore().collection(COLLECTION_LOCKS)
                    .whereEqualTo(FIELD_LOCK_DOCTOR_ID, doctorId)
                    .whereGreaterThanOrEqualTo(FIELD_LOCK_START_AT, timestampOf(window.first))
                    .whereLessThanOrEqualTo(FIELD_LOCK_START_AT, timestampOf(window.last))
            },
        ) { snapshot -> snapshot.documents.mapTo(mutableSetOf()) { it.id } }
    }

    override fun myBookings(): Flow<BookingsSnapshot> {
        val uid = currentUid() ?: return callbackFlow { close(AuthException(AuthError.NOT_SIGNED_IN)) }
        // The rules only allow listing with patientUid == uid. At most 3 are upcoming, so a
        // newest-first page always holds every upcoming booking.
        return listen(
            query = {
                firestore().collection(COLLECTION_BOOKINGS)
                    .whereEqualTo(BookingMapper.FIELD_PATIENT_UID, uid)
                    .orderBy(BookingMapper.FIELD_START_AT, Query.Direction.DESCENDING)
                    .limit(MAX_BOOKINGS_READ)
            },
        ) { snapshot ->
            BookingsSnapshot(
                bookings = snapshot.documents.mapNotNull { BookingMapper.parse(it.id, it.data, uid) },
                fromCache = snapshot.metadata.isFromCache,
            )
        }
    }

    override suspend fun hasVerifiedEmail(): Boolean = mapErrors {
        // A token issued before the user verified still says false for up to an hour.
        verifiedClaim(false) || verifiedClaim(true)
    }

    override suspend fun book(slot: Slot): String {
        val uid = setUp { currentUid() } ?: throw BookingException(BookingError.UNKNOWN)
        val verified = try {
            hasVerifiedEmail()
        } catch (e: AuthException) {
            throw BookingException(if (e.error == AuthError.NETWORK) BookingError.NETWORK else BookingError.UNKNOWN, e)
        }
        if (!verified) throw BookingException(BookingError.EMAIL_NOT_VERIFIED)
        val db = setUp { firestore() }
        val bookingRef = setUp { db.collection(COLLECTION_BOOKINGS).document() }
        val lockRef = setUp { db.collection(COLLECTION_LOCKS).document(slot.id) }
        return try {
            db.runTransaction { transaction ->
                val doctorSnapshot = transaction.get(db.collection(COLLECTION_DOCTORS).document(slot.doctorId))
                val doctorData = doctorSnapshot.data
                val doctor = DoctorMapper.parse(slot.doctorId, doctorData)
                    ?: throw BookingException(BookingError.DOCTOR_UNAVAILABLE)
                if (!Slots.isOffered(doctor, slot, clock())) throw BookingException(BookingError.SLOT_UNAVAILABLE)
                if (transaction.get(lockRef).exists()) throw BookingException(BookingError.SLOT_TAKEN)
                val place = Booking.QuotaPlaces.firstOrNull { place ->
                    val held = transaction.get(quotaRef(db, uid, place))
                    // A place is free when empty, or once the booking it holds has started.
                    !held.exists() || (held.getTimestamp(FIELD_QUOTA_START_AT)?.toDate()?.time ?: Long.MAX_VALUE) <= clock()
                } ?: throw BookingException(BookingError.LIMIT_REACHED)
                // Copied as stored: the rules require the profile's current name. It is what the
                // admin's list shows, so they never need to read the profile itself.
                val patientName = transaction.get(db.collection(COLLECTION_USERS).document(uid))
                    .getString(FIELD_USER_NAME)
                    ?: throw BookingException(BookingError.UNKNOWN)

                val startAt = timestampOf(slot.startAtMillis)
                transaction.set(
                    bookingRef,
                    mapOf(
                        BookingMapper.FIELD_PATIENT_UID to uid,
                        BookingMapper.FIELD_PATIENT_NAME to patientName,
                        BookingMapper.FIELD_DOCTOR_ID to slot.doctorId,
                        // Copied as stored, not as cleaned for display: the rules compare them.
                        BookingMapper.FIELD_DOCTOR to mapOf(
                            DoctorMapper.FIELD_NAME to doctorData?.get(DoctorMapper.FIELD_NAME),
                            DoctorMapper.FIELD_SPECIALTY to doctorData?.get(DoctorMapper.FIELD_SPECIALTY),
                            DoctorMapper.FIELD_HOSPITAL to doctorData?.get(DoctorMapper.FIELD_HOSPITAL),
                            DoctorMapper.FIELD_FEE to doctorData?.get(DoctorMapper.FIELD_FEE),
                        ),
                        BookingMapper.FIELD_START_AT to startAt,
                        BookingMapper.FIELD_SLOT_ID to slot.id,
                        BookingMapper.FIELD_QUOTA_PLACE to place.toLong(),
                        BookingMapper.FIELD_STATUS to BookingStatus.BOOKED.key,
                        BookingMapper.FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    ),
                )
                transaction.set(
                    lockRef,
                    mapOf(
                        FIELD_LOCK_DOCTOR_ID to slot.doctorId,
                        FIELD_LOCK_START_AT to startAt,
                        FIELD_LOCK_BOOKING_ID to bookingRef.id,
                    ),
                )
                transaction.set(
                    quotaRef(db, uid, place),
                    mapOf(FIELD_QUOTA_BOOKING_ID to bookingRef.id, FIELD_QUOTA_START_AT to startAt),
                )
                null
            }.await()
            bookingRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw bookingFailure(e, lockRef)
        }
    }

    override suspend fun cancel(booking: Booking) {
        // Everything inside the try: after sign-out the Firestore instance is terminated, and
        // even building a reference throws (IllegalStateException), which must not escape.
        try {
            val uid = currentUid() ?: throw BookingException(BookingError.UNKNOWN)
            if (!BookingMapper.isValidId(booking.id)) throw BookingException(BookingError.NOT_FOUND)
            val db = firestore()
            val bookingRef = db.collection(COLLECTION_BOOKINGS).document(booking.id)
            db.runTransaction { transaction ->
                // Re-read: the copy on screen may be stale (cancelled on another phone).
                val current = BookingMapper.parse(booking.id, transaction.get(bookingRef).data, uid)
                    ?: throw BookingException(BookingError.NOT_FOUND)
                if (current.status == BookingStatus.CANCELLED) return@runTransaction null
                if (current.startAtMillis <= clock()) throw BookingException(BookingError.ALREADY_STARTED)
                val lockRef = db.collection(COLLECTION_LOCKS).document(current.slotId)
                val placeRef = quotaRef(db, uid, current.quotaPlace)
                // Deleting a missing document is refused by the rules (they read its fields), so
                // only what is there, and only the quota place that holds this booking.
                val lockExists = transaction.get(lockRef).exists()
                val placeHeld = transaction.get(placeRef).getString(FIELD_QUOTA_BOOKING_ID) == current.id
                transaction.update(
                    bookingRef,
                    mapOf(
                        BookingMapper.FIELD_STATUS to BookingStatus.CANCELLED.key,
                        BookingMapper.FIELD_CANCELLED_AT to FieldValue.serverTimestamp(),
                        BookingMapper.FIELD_CANCELLED_BY to CancelledBy.PATIENT.key,
                    ),
                )
                if (lockExists) transaction.delete(lockRef)
                if (placeHeld) transaction.delete(placeRef)
                null
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.bookingException() ?: BookingException(firestoreError(e), e)
        }
    }

    override suspend fun cancelAllUpcoming() {
        val uid = setUp { currentUid() } ?: throw BookingException(BookingError.UNKNOWN)
        val upcoming = try {
            // From the server: a stale cache could miss a booking made on another phone.
            firestore().collection(COLLECTION_BOOKINGS)
                .whereEqualTo(BookingMapper.FIELD_PATIENT_UID, uid)
                .whereEqualTo(BookingMapper.FIELD_STATUS, BookingStatus.BOOKED.key)
                .get(Source.SERVER)
                .await()
                .documents
                .mapNotNull { BookingMapper.parse(it.id, it.data, uid) }
                .filter { it.isUpcoming(clock()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BookingException(firestoreError(e), e)
        }
        upcoming.forEach { booking ->
            try {
                cancel(booking)
            } catch (e: BookingException) {
                // Started in the meantime: nothing left to free.
                if (e.error != BookingError.ALREADY_STARTED) throw e
            }
        }
    }

    override suspend fun erasePatientName() {
        try {
            val uid = currentUid() ?: throw BookingException(BookingError.UNKNOWN)
            val db = firestore()
            // From the server, every booking (not only those the mapper accepts: a malformed one
            // still carries the name), page by page in document ID order (no extra index).
            var after: DocumentSnapshot? = null
            repeat(MAX_ERASE_PAGES) {
                val first = db.collection(COLLECTION_BOOKINGS)
                    .whereEqualTo(BookingMapper.FIELD_PATIENT_UID, uid)
                    .orderBy(FieldPath.documentId())
                    .limit(ERASE_PAGE_SIZE)
                val page = after?.let { first.startAfter(it) } ?: first
                val documents = page.get(Source.SERVER).await().documents
                // Bookings from before the name was stored have none to erase.
                val named = documents.filter { it.contains(BookingMapper.FIELD_PATIENT_NAME) && !isErased(it) }
                if (named.isNotEmpty()) {
                    // A transaction rather than a batch: offline it fails at once instead of queueing.
                    db.runTransaction { transaction ->
                        named.forEach { transaction.update(it.reference, BookingMapper.FIELD_PATIENT_NAME, BookingMapper.DELETED_PATIENT_NAME) }
                        null
                    }.await()
                }
                if (documents.size < ERASE_PAGE_SIZE) return
                after = documents.last()
            }
            // Still more after the last page: never report done when names may be left.
            throw BookingException(BookingError.UNKNOWN)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.bookingException() ?: BookingException(firestoreError(e), e)
        }
    }

    private fun isErased(booking: DocumentSnapshot): Boolean =
        booking.get(BookingMapper.FIELD_PATIENT_NAME) == BookingMapper.DELETED_PATIENT_NAME

    /**
     * Why a booking transaction failed. A refused write usually means someone took the slot
     * between our read and the commit (their lock makes ours an update, which is denied), so
     * check the lock first. Otherwise a refusal means the slot is no longer bookable: the doctor
     * was deactivated (their read is refused) or their schedule changed.
     */
    private suspend fun bookingFailure(e: Exception, lockRef: DocumentReference): BookingException {
        e.bookingException()?.let { return it }
        val error = AuthErrorMapper.map(e)
        if (error == AuthError.NETWORK) return BookingException(BookingError.NETWORK, e)
        val taken = try {
            lockRef.get(Source.SERVER).await().exists()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        val reason = when {
            taken -> BookingError.SLOT_TAKEN
            error == AuthError.PERMISSION_DENIED -> BookingError.SLOT_UNAVAILABLE
            else -> BookingError.UNKNOWN
        }
        return BookingException(reason, e)
    }

    private fun firestoreError(e: Exception): BookingError = when (AuthErrorMapper.map(e)) {
        AuthError.NETWORK -> BookingError.NETWORK
        else -> BookingError.UNKNOWN
    }

    /** Our own errors thrown inside a transaction may come back wrapped. */
    private fun Throwable.bookingException(): BookingException? =
        generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).filterIsInstance<BookingException>().firstOrNull()

    /**
     * Steps before a booking's transaction (the signed-in user, the Firestore instance, document
     * references). After sign-out the instance is terminated and these throw
     * IllegalStateException: report it as [BookingError.UNKNOWN] instead of letting it escape.
     */
    private inline fun <T> setUp(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.bookingException() ?: BookingException(BookingError.UNKNOWN, e)
    }

    private fun <T> listen(query: () -> Query, map: (QuerySnapshot) -> T): Flow<T> = queryFlow(listeners, query, map)

    private fun quotaRef(db: FirebaseFirestore, uid: String, place: Int) =
        db.collection(COLLECTION_USERS).document(uid).collection(COLLECTION_QUOTA).document(place.toString())

    private fun timestampOf(millis: Long) = Timestamp(Date(millis))

    private companion object {
        const val COLLECTION_BOOKINGS = "bookings"
        const val COLLECTION_LOCKS = "slotLocks"
        const val COLLECTION_DOCTORS = "doctors"
        const val COLLECTION_USERS = "users"
        const val COLLECTION_QUOTA = "bookingQuota"
        const val FIELD_LOCK_DOCTOR_ID = "doctorId"
        const val FIELD_LOCK_START_AT = "startAt"
        const val FIELD_LOCK_BOOKING_ID = "bookingId"
        const val FIELD_QUOTA_BOOKING_ID = "bookingId"
        const val FIELD_QUOTA_START_AT = "startAt"
        const val FIELD_USER_NAME = "name"
        const val MAX_BOOKINGS_READ = 100L
        const val MAX_CAUSE_DEPTH = 5

        /** Bookings renamed per transaction (Firestore allows 500 writes in one). */
        const val ERASE_PAGE_SIZE = 200L

        /** 10,000 bookings: far more than one patient has; a guard, never a real limit. */
        const val MAX_ERASE_PAGES = 50
    }
}
