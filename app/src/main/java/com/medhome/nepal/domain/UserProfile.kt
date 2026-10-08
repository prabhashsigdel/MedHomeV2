package com.medhome.nepal.domain

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val role: Role,
) {
    /** First word of the name for greetings ("Prabhash Sigdel" -> "Prabhash"); null if blank. */
    val firstName: String?
        get() = name.trim().split(WHITESPACE).firstOrNull()?.takeIf { it.isNotEmpty() }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
