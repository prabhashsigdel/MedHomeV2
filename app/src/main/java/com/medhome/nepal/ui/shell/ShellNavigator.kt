package com.medhome.nepal.ui.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/** The patient shell's tabs, as screens see them. */
enum class ShellTab { HOME, BOOKINGS, MEDICINES, RECORDS }

/**
 * Lets any screen inside the patient shell, pushed screens included, switch tabs, which only
 * the shell's own NavHost can do. Part 2 (booking) uses it to finish a booking on the Bookings
 * tab. Switching is a standard multiple-back-stack switch: the current tab keeps its stack.
 */
@Stable
class ShellNavigator internal constructor(private val select: (ShellTab) -> Unit) {
    fun selectTab(tab: ShellTab) = select(tab)
}

/** Provided by the patient shell; null outside it (staff home, sign-in screens, previews). */
val LocalShellNavigator = staticCompositionLocalOf<ShellNavigator?> { null }
