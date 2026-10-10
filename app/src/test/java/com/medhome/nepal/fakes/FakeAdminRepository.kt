package com.medhome.nepal.fakes

import com.medhome.nepal.data.AdminBookingsPage
import com.medhome.nepal.data.AdminRepository
import com.medhome.nepal.data.ManagedDoctorLookup
import com.medhome.nepal.data.ManagedDoctorsSnapshot
import com.medhome.nepal.data.UpcomingPage
import com.medhome.nepal.domain.AdminBooking
import com.medhome.nepal.domain.AdminBookingFilter
import com.medhome.nepal.domain.AdminCancelledBy
import com.medhome.nepal.domain.AdminError
import com.medhome.nepal.domain.AdminException
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.ClinicCancel
import com.medhome.nepal.domain.ClinicCancelReason
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.DoctorAppointment
import com.medhome.nepal.domain.ManagedDoctor
import com.medhome.nepal.ui.admin.AdminViewModels
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The catalogue in memory, as an admin sees it. [doctors] null means "no answer yet". Writes
 * change [doctors] (and cancels [appointments], which stay listed as cancelled by you) as
 * Firestore's listeners would show;
 * [writeFailure] makes them fail, and [gate], when set, holds them until completed (to see the
 * saving state). [cancelFailures] fails cancelling those bookings only, [idsFailure] fails
 * listing the bookings to cancel, and [idsPageSize] pages that list.
 */
class FakeAdminRepository(initial: List<ManagedDoctor> = emptyList()) : AdminRepository {
    val doctors = MutableStateFlow<List<ManagedDoctor>?>(initial)
    val appointments = MutableStateFlow<Map<String, List<DoctorAppointment>>>(emptyMap())
    var fromCache = false
    var listFailure: AuthError? = null
    var countFailure: AdminError? = null
    var writeFailure: AdminError? = null
    var gate: CompletableDeferred<Unit>? = null
    val created = mutableListOf<Doctor>()
    val updated = mutableListOf<Doctor>()
    val activeChanges = mutableListOf<Pair<String, Boolean>>()
    val cancelFailures = mutableMapOf<String, AdminError>()
    var idsFailure: AdminError? = null
    var idsPageSize = Int.MAX_VALUE
    val cancelledBookings = mutableListOf<String>()

    /** The reason each cancel was given (also those that turned out to be already cancelled). */
    val cancelReasons = mutableMapOf<String, ClinicCancelReason>()

    /** Bookings the patient cancelled after the list was read: cancelling them does nothing. */
    val alreadyCancelled = mutableSetOf<String>()

    /** Every doctor's bookings, for the Bookings tab ([allBookings]); [bookingsFailure] fails reading them. */
    val allBookings = MutableStateFlow<List<AdminBooking>>(emptyList())
    var bookingsFailure: AuthError? = null

    /** The limits the Bookings tab asked for, in order. */
    val bookingLimits = mutableListOf<Int>()

    /** "Now", for the Bookings tab's filters. */
    var now: () -> Long = System::currentTimeMillis

    override fun allDoctors(): Flow<ManagedDoctorsSnapshot> = flow {
        listFailure?.let { throw AuthException(it) }
        doctors.filterNotNull().map { ManagedDoctorsSnapshot(it, fromCache) }.collect { emit(it) }
    }

    override fun doctor(id: String): Flow<ManagedDoctorLookup> = flow {
        listFailure?.let { throw AuthException(it) }
        doctors.filterNotNull()
            .map { list ->
                val found = list.firstOrNull { it.doctor.id == id }
                if (found != null) ManagedDoctorLookup.Found(found, fromCache) else ManagedDoctorLookup.Missing(fromCache)
            }
            .collect { emit(it) }
    }

    override fun upcomingAppointments(doctorId: String): Flow<List<DoctorAppointment>> = flow {
        listFailure?.let { throw AuthException(it) }
        appointments.map { it[doctorId].orEmpty() }.collect { emit(it) }
    }

    override fun bookings(filter: AdminBookingFilter, limit: Int): Flow<AdminBookingsPage> = flow {
        bookingLimits += limit
        bookingsFailure?.let { throw AuthException(it) }
        allBookings.map { all ->
            val at = now()
            val matching = when (filter) {
                AdminBookingFilter.UPCOMING -> all.filter { it.isUpcoming(at) }.sortedBy { it.startAtMillis }
                AdminBookingFilter.PAST -> all.filter { it.status == BookingStatus.BOOKED && it.startAtMillis <= at }
                    .sortedByDescending { it.startAtMillis }
                AdminBookingFilter.CANCELLED -> all.filter { it.status == BookingStatus.CANCELLED }.sortedByDescending { it.startAtMillis }
            }
            AdminBookingsPage(matching.take(limit), hasMore = matching.size >= limit, fromCache = fromCache)
        }.collect { emit(it) }
    }

    override fun booking(id: String): Flow<AdminBooking?> = flow {
        bookingsFailure?.let { throw AuthException(it) }
        allBookings.map { all -> all.firstOrNull { it.bookingId == id } }.collect { emit(it) }
    }

    override suspend fun upcomingCount(doctorId: String): Int {
        countFailure?.let { throw AdminException(it) }
        return booked(doctorId).size
    }

    override suspend fun createDoctor(doctor: Doctor) {
        write()
        if (current().any { it.doctor.id == doctor.id }) throw AdminException(AdminError.ALREADY_EXISTS)
        created += doctor
        doctors.value = current() + ManagedDoctor(doctor, active = true)
    }

    override suspend fun updateDoctor(doctor: Doctor) {
        write()
        if (current().none { it.doctor.id == doctor.id }) throw AdminException(AdminError.NOT_FOUND)
        updated += doctor
        doctors.value = current().map { if (it.doctor.id == doctor.id) it.copy(doctor = doctor) else it }
    }

    override suspend fun setActive(doctorId: String, active: Boolean) {
        write()
        if (current().none { it.doctor.id == doctorId }) throw AdminException(AdminError.NOT_FOUND)
        activeChanges += doctorId to active
        doctors.value = current().map { if (it.doctor.id == doctorId) it.copy(active = active) else it }
    }

    override suspend fun upcomingBookingPage(doctorId: String, afterMillis: Long?): UpcomingPage {
        idsFailure?.let { throw AdminException(it) }
        val page = booked(doctorId)
            .filter { afterMillis == null || it.startAtMillis > afterMillis }
            .sortedBy { it.startAtMillis }
            .take(idsPageSize)
        // Like a full Firestore page: there may be more after it.
        val full = page.size == idsPageSize
        return UpcomingPage(page.map { it.bookingId }, if (full) page.last().startAtMillis else null)
    }

    override suspend fun cancelBooking(bookingId: String, reason: ClinicCancelReason): Boolean {
        write()
        cancelReasons[bookingId] = reason
        cancelFailures[bookingId]?.let { throw AdminException(it) }
        if (alreadyCancelled.remove(bookingId)) return false
        cancelledBookings += bookingId
        // Like the live list: still there, cancelled by this admin.
        appointments.value = appointments.value.mapValues { (_, list) ->
            list.map { if (it.bookingId == bookingId) it.copy(clinicCancel = ClinicCancel.BY_YOU) else it }
        }
        allBookings.value = allBookings.value.map {
            if (it.bookingId == bookingId) {
                it.copy(
                    status = BookingStatus.CANCELLED,
                    cancelledBy = AdminCancelledBy.YOU,
                    cancelledAtMillis = now(),
                    clinicReason = reason,
                )
            } else {
                it
            }
        }
        return true
    }

    /** [doctorId]'s appointments still booked. */
    fun booked(doctorId: String): List<DoctorAppointment> = appointments.value[doctorId].orEmpty().filter { it.isBooked }

    private suspend fun write() {
        gate?.await()
        writeFailure?.let { throw AdminException(it) }
    }

    private fun current(): List<ManagedDoctor> = doctors.value.orEmpty()
}

/** Admin ViewModels over a fake, for shell tests (no Firebase app container in JVM tests). */
fun fakeAdminViewModels(
    repository: FakeAdminRepository = FakeAdminRepository(),
    clock: () -> Long = System::currentTimeMillis,
    newId: () -> String = { "doc-new" },
) = AdminViewModels.factory { AdminViewModels.Dependencies(repository, clock, newId) }
