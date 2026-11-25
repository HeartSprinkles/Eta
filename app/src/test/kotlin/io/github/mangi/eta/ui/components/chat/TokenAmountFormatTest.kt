package io.github.mangi.eta.ui.components.chat

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenAmountFormatTest {
    @Test
    fun smallValuesStayExact() {
        assertEquals("0", formatTokenAmount(0, Locale.US))
        assertEquals("847", formatTokenAmount(847, Locale.US))
        assertEquals("999", formatTokenAmount(999, Locale.US))
    }

    @Test
    fun thousandsUseAtMostOneDecimal() {
        assertEquals("1K", formatTokenAmount(1_000))
        assertEquals("1K", formatTokenAmount(1_049))
        assertEquals("1.1K", formatTokenAmount(1_050))
        assertEquals("12.3K", formatTokenAmount(12_345))
        assertEquals("999.9K", formatTokenAmount(999_949))
    }

    @Test
    fun roundedThousandsCarryToMillions() {
        assertEquals("1M", formatTokenAmount(999_950))
        assertEquals("1M", formatTokenAmount(1_000_000))
        assertEquals("1.2M", formatTokenAmount(1_234_567))
    }

    @Test
    fun roundedMillionsCarryToBillionsWithoutOverflow() {
        assertEquals("999.9M", formatTokenAmount(999_949_999))
        assertEquals("1B", formatTokenAmount(999_950_000))
        assertEquals("1B", formatTokenAmount(1_000_000_000))
        assertEquals("2.1B", formatTokenAmount(Int.MAX_VALUE))
    }

    @Test
    fun negativesAreClamped() {
        assertEquals("0", formatTokenAmount(-1))
        assertEquals("0", formatTokenAmount(Int.MIN_VALUE))
    }

    @Test
    fun smallValuesUseTheirLocaleAndDetailsStayExact() {
        assertEquals("999", formatTokenAmount(999, Locale.US))
        assertEquals("999", formatTokenAmount(999, Locale.SIMPLIFIED_CHINESE))
        assertEquals("12,345", formatExactTokenCount(12_345, Locale.US))
        assertEquals("12.3K", formatTokenAmount(12_345, Locale.US))
    }
}
