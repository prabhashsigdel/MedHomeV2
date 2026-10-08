package com.medhome.nepal.domain

/** Stored as [key] (never translated text); shown translated by the UI. */
enum class Gender(val key: String) {
    MALE("male"),
    FEMALE("female"),
    OTHER("other"),
    ;

    companion object {
        fun fromKey(key: String?): Gender? = entries.firstOrNull { it.key == key }
    }
}

/**
 * [phone] is a 10-digit Nepali mobile number, [dateOfBirth] an ISO "YYYY-MM-DD" date. Both, and
 * [gender], are optional.
 */
data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val role: Role,
    val phone: String? = null,
    val dateOfBirth: String? = null,
    val gender: Gender? = null,
) {
    /** First word of the name for greetings ("Prabhash Sigdel" -> "Prabhash"); null if blank. */
    val firstName: String?
        get() = name.trim().split(WHITESPACE).firstOrNull()?.takeIf { it.isNotEmpty() }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/** What the user can edit about themselves. Null optional fields are removed from the profile. */
data class ProfileDetails(
    val name: String,
    val phone: String?,
    val dateOfBirth: String?,
    val gender: Gender?,
)
