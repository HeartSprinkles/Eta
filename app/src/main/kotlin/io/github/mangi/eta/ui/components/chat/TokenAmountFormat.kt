package io.github.mangi.eta.ui.components.chat

import java.text.NumberFormat
import java.util.Locale

/** Compact total-token headline; detail rows continue to use [formatExactTokenCount]. */
internal fun formatTokenAmount(tokens: Int, locale: Locale = Locale.getDefault()): String {
    val value = tokens.coerceAtLeast(0)
    if (value < 1_000) return NumberFormat.getIntegerInstance(locale).format(value)
    val units = listOf(1_000L to "K", 1_000_000L to "M", 1_000_000_000L to "B")
    var index = units.indexOfLast { value >= it.first }
    var tenths = (value.toLong() * 10L + units[index].first / 2L) / units[index].first
    while (tenths >= 10_000L && index < units.lastIndex) {
        index++
        tenths = (value.toLong() * 10L + units[index].first / 2L) / units[index].first
    }
    val whole = tenths / 10L
    val decimal = tenths % 10L
    return if (decimal == 0L) "$whole${units[index].second}" else "$whole.$decimal${units[index].second}"
}
