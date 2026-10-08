package com.medhome.nepal.domain

import androidx.annotation.StringRes
import com.medhome.nepal.R

enum class AuthError(@param:StringRes val messageRes: Int) {
    INVALID_CREDENTIALS(R.string.error_invalid_credentials),
    USER_NOT_FOUND(R.string.error_user_not_found),
    USER_DISABLED(R.string.error_user_disabled),
    INVALID_EMAIL(R.string.error_invalid_email),
    EMAIL_IN_USE(R.string.error_email_in_use),
    WEAK_PASSWORD(R.string.error_weak_password),
    ACCOUNT_EXISTS_WITH_PASSWORD(R.string.error_account_exists_with_password),
    WRONG_ACCOUNT(R.string.error_wrong_account),
    REQUIRES_RECENT_LOGIN(R.string.error_requires_recent_login),
    TOO_MANY_REQUESTS(R.string.error_too_many_requests),
    NETWORK(R.string.error_network),
    GOOGLE_NO_ACCOUNT(R.string.error_google_no_account),
    GOOGLE_FAILED(R.string.error_google_failed),
    PERMISSION_DENIED(R.string.error_permission_denied),
    PROFILE_INVALID(R.string.error_profile_invalid),
    PROFILE_LOAD_FAILED(R.string.error_profile_load_failed),
    NOT_SIGNED_IN(R.string.error_not_signed_in),
    UNKNOWN(R.string.error_unknown),
}

class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)
