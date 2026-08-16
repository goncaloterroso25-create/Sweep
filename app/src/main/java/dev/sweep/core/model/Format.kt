package dev.sweep.core.model

import java.util.Locale

/**
 * A byte figure split into its number and its unit, so the UI can typeset them at
 * different sizes — "42.8" large, "GB" small.
 */
data class FormattedBytes(val value: String, val unit: String) {
    override fun toString(): String = "$value $unit"
}

/**
 * Decimal (base 1000) byte formatting, matching how Android Settings reports storage.
 * Using base 1024 here would make Sweep's numbers disagree with the system's.
 */
object ByteFormat {

    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    fun format(bytes: Long, locale: Locale = Locale.getDefault()): FormattedBytes {
        if (bytes <= 0L) return FormattedBytes("0", "B")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1000.0 && unit < UNITS.lastIndex) {
            value /= 1000.0
            unit++
        }
        val decimals = when {
            unit == 0 -> 0
            value >= 100.0 -> 0
            else -> 1
        }
        val text = String.format(locale, "%.${decimals}f", value)
        return FormattedBytes(text, UNITS[unit])
    }

    fun short(bytes: Long, locale: Locale = Locale.getDefault()): String =
        format(bytes, locale).toString()
}

/** Calendar dates, for the file details sheet where "4 months ago" is too vague to act on. */
object DateFormat {

    fun day(millis: Long, locale: Locale = Locale.getDefault()): String {
        if (millis <= 0L) return "Unknown"
        val format = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale)
        return format.format(java.util.Date(millis))
    }
}

/** Human wording for "how long ago", used for the reason chips. */
object AgeFormat {

    fun describe(days: Int): String = when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 30 -> "$days days ago"
        days < 60 -> "last month"
        days < 365 -> "${days / 30} months ago"
        days < 730 -> "over a year ago"
        else -> "${days / 365} years ago"
    }

    /** Compact form for dense rows: "142d", "7mo", "2y". */
    fun compact(days: Int): String = when {
        days <= 0 -> "today"
        days < 60 -> "${days}d"
        days < 365 -> "${days / 30}mo"
        else -> "${days / 365}y"
    }
}
