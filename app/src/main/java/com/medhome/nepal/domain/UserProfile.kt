package com.medhome.nepal.domain

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val role: Role,
)
