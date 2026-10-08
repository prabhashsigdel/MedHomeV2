package com.medhome.nepal.domain

enum class Role(val id: String) {
    PATIENT("patient"),
    DOCTOR("doctor"),
    ADMIN("admin");

    companion object {
        fun fromId(id: String?): Role? = entries.firstOrNull { it.id == id }
    }
}
