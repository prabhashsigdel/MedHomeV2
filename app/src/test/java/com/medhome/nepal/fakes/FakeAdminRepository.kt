package com.medhome.nepal.fakes

import com.medhome.nepal.data.AdminRepository
import com.medhome.nepal.data.ManagedDoctorLookup
import com.medhome.nepal.data.ManagedDoctorsSnapshot
import com.medhome.nepal.domain.AdminError
import com.medhome.nepal.domain.AdminException
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
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
 * change [doctors] as Firestore's listeners would show; [writeFailure] makes them fail, and
 * [gate], when set, holds them until completed (to see the saving state).
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

    override suspend fun upcomingCount(doctorId: String): Int {
        countFailure?.let { throw AdminException(it) }
        return appointments.value[doctorId].orEmpty().size
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
