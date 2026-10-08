package com.medhome.nepal.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.medhome.nepal.data.CredentialClient

/** Provided by MainActivity from the app container; tests provide a fake. */
val LocalCredentialClient = staticCompositionLocalOf<CredentialClient> {
    error("LocalCredentialClient not provided")
}
