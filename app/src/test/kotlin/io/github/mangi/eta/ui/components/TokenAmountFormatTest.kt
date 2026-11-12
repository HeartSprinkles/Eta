package io.github.mangi.eta.ui.components

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenAmountFormatTest {
    @Test
    fun `四位数以下保留精确值`() {
        assertEquals("0", formatTokenAmount(0))
        assertEquals("847", formatTokenAmount(847))
        assertEquals("999", formatTokenAmount(999))
    }

    @Test
    fun `千级压缩为一位小数的 K`() {
        assertEquals("1K", formatTokenAmount(1_000))
        assertEquals("12.3K", formatTokenAmount(12_345))
        assertEquals("999.9K", formatTokenAmount(999_949))
    }

    @Test
    fun `进位到下一量级时提升单位而不是显示 1000K`() {
        assertEquals("1M", formatTokenAmount(999_950))
        assertEquals("1M", formatTokenAmount(1_000_000))
        assertEquals("1.2M", formatTokenAmount(1_234_567))
    }

    @Test
    fun `十亿级使用 B`() {
        assertEquals("2.1B", formatTokenAmount(Int.MAX_VALUE))
    }

    @Test
    fun `负数按零处理且不抛异常`() {
        assertEquals("0", formatTokenAmount(-1))
    }

    @Test
    fun `小数字遵循传入的区域设置`() {
        assertEquals("999", formatTokenAmount(999, Locale.US))
        assertEquals("999", formatTokenAmount(999, Locale.SIMPLIFIED_CHINESE))
    }
}
