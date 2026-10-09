package com.medhome.nepal.fakes

import com.medhome.nepal.data.DoctorLookup
import com.medhome.nepal.data.DoctorRepository
import com.medhome.nepal.data.DoctorsSnapshot
import com.medhome.nepal.domain.AuthError
import com.medhome.nepal.domain.AuthException
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.TimeRange
import com.medhome.nepal.domain.Weekday
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

fun doctor(
    id: String = "doc-001",
    name: String = "Asha Rai",
    specialty: Specialty = Specialty.CARDIOLOGY,
    hospital: String = "Valley Care Hospital",
    feeNpr: Int = 800,
) = Doctor(
    id = id,
    name = name,
    specialty = specialty,
    hospital = hospital,
    feeNpr = feeNpr,
    experienceYears = 12,
    bio = "Heart health and blood pressure.",
    slotMinutes = 15,
    weeklySchedule = mapOf(
        Weekday.SUNDAY to listOf(TimeRange(TimeOfDay(10 * 60), TimeOfDay(13 * 60))),
    ),
)

/**
 * Doctors from memory. [snapshot] null means "no answer yet" (loading); [failure] makes every
 * new collection fail with that error. [listens] counts started collections (retry tests).
 */
class FakeDoctorRepository(
    doctors: List<Doctor> = emptyList(),
    fromCache: Boolean = false,
) : DoctorRepository {
    val snapshot = MutableStateFlow<DoctorsSnapshot?>(DoctorsSnapshot(doctors, fromCache))
    var failure: AuthError? = null
    var listens = 0
        private set

    override fun activeDoctors(): Flow<DoctorsSnapshot> = flow {
        listens++
        failure?.let { throw AuthException(it) }
        snapshot.filterNotNull().collect { emit(it) }
    }

    override fun doctor(id: String): Flow<DoctorLookup> = flow {
        listens++
        failure?.let { throw AuthException(it) }
        snapshot.filterNotNull()
            .map { current ->
                val found = current.doctors.firstOrNull { it.id == id }
                if (found != null) DoctorLookup.Found(found, current.fromCache) else DoctorLookup.Unavailable(current.fromCache)
            }
            .collect { emit(it) }
    }
}
