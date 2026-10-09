package com.medhome.nepal.ui.doctors

import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.Specialty
import java.util.Locale

/** What the doctor list is narrowed to: a name search and at most one specialty (null: all). */
data class DoctorFilter(val query: String = "", val specialty: Specialty? = null) {
    val isActive: Boolean get() = normalized(query).isNotEmpty() || specialty != null
}

/** Longest search kept; longer input is cut (nobody's name is longer). */
const val MAX_QUERY_LENGTH = 60

/**
 * Doctors whose name contains every word of the query (any order, ignoring case and extra
 * spaces: "rai asha" finds "Asha Rai") and, when a specialty is picked, have that specialty.
 */
fun List<Doctor>.matching(filter: DoctorFilter): List<Doctor> {
    val words = normalized(filter.query).split(' ').filter { it.isNotEmpty() }
    return filter { doctor ->
        val name = normalized(doctor.name)
        (filter.specialty == null || doctor.specialty == filter.specialty) && words.all { it in name }
    }
}

/** The specialties at least one doctor has, in the app's fixed order (the filter chips). */
fun List<Doctor>.specialties(): List<Specialty> {
    val present = mapTo(mutableSetOf()) { it.specialty }
    return Specialty.entries.filter { it in present }
}

private fun normalized(text: String): String =
    text.trim().lowercase(Locale.ROOT).replace(SPACES, " ")

private val SPACES = Regex("\\s+")
