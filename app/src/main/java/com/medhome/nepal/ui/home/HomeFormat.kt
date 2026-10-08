package com.medhome.nepal.ui.home

import android.annotation.SuppressLint
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import java.util.Date
import java.util.Locale

/**
 * Up to two initials for the avatar: the first letter of the first and the last word, so
 * "Prabhash Kumar Sigdel" gives "PS" and "प्रभाष सिग्देल" gives "पस". Null for a name with no
 * letters (the avatar then shows a person icon).
 */
fun initialsOf(name: String): String? {
    val letters = name.trim()
        .split(WHITESPACE)
        .mapNotNull(::firstLetter)
    if (letters.isEmpty()) return null
    val initials = if (letters.size == 1) letters.first() else letters.first() + letters.last()
    return initials.uppercase()
}

/** The first letter or digit of [word] (a whole code point, so emoji-safe), or null. */
private fun firstLetter(word: String): String? {
    var index = 0
    while (index < word.length) {
        val codePoint = word.codePointAt(index)
        if (Character.isLetterOrDigit(codePoint)) return String(Character.toChars(codePoint))
        index += Character.charCount(codePoint)
    }
    return null
}

private val WHITESPACE = Regex("\\s+")

/**
 * Today's line above the greeting, day first in every language ("Thursday, 8 October"). Names
 * and digits follow [locale], so Nepali gets Devanagari digits on the Gregorian (AD) calendar.
 * ICU, because it formats Nepali correctly on every supported Android version.
 */
@SuppressLint("SimpleDateFormat") // Fixed day-first order by decision; names and digits stay localized.
fun homeDateText(date: Date, locale: Locale): String =
    SimpleDateFormat(HOME_DATE_PATTERN, ULocale.forLocale(locale)).format(date)

private const val HOME_DATE_PATTERN = "EEEE, d MMMM"
