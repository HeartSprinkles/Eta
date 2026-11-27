package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecodeTimingTest {
    @Test
    fun `正常的流式解码窗口被保留`() {
        assertEquals(
            10_000L,
            DecodeTiming.effectiveWindowMs(measuredMs = 10_000L, stepLlmMs = 12_000L, outputTokens = 1_000),
        )
    }

    @Test
    fun `一次性返回的响应退回整轮耗时`() {
        // 真机记录：1348 tokens、解码窗口 136ms、整轮 267039ms、TTFT 266903ms。
        assertEquals(
            267_039L,
            DecodeTiming.effectiveWindowMs(measuredMs = 136L, stepLlmMs = 267_039L, outputTokens = 1_348),
        )
    }

    @Test
    fun `速度超过可信上限时退回整轮耗时`() {
        assertEquals(
            4_000L,
            DecodeTiming.effectiveWindowMs(measuredMs = 200L, stepLlmMs = 4_000L, outputTokens = 201),
        )
    }

    @Test
    fun `窗口恰好达到可信边界时保留测量值`() {
        assertEquals(
            200L,
            DecodeTiming.effectiveWindowMs(measuredMs = 200L, stepLlmMs = 5_000L, outputTokens = 200),
        )
    }

    @Test
    fun `解码窗口缺失时退回整轮耗时`() {
        assertEquals(
            5_000L,
            DecodeTiming.effectiveWindowMs(measuredMs = null, stepLlmMs = 5_000L, outputTokens = 120),
        )
    }

    @Test
    fun `两种窗口都不可信时不记录速度`() {
        assertNull(DecodeTiming.effectiveWindowMs(measuredMs = 5L, stepLlmMs = 50L, outputTokens = 500))
        assertNull(DecodeTiming.effectiveWindowMs(measuredMs = 0L, stepLlmMs = 0L, outputTokens = 10))
    }

    @Test
    fun `没有输出 token 时不记录速度`() {
        assertNull(DecodeTiming.effectiveWindowMs(measuredMs = 9_000L, stepLlmMs = 9_000L, outputTokens = 0))
    }

    @Test
    fun `速度换算对非法输入返回零`() {
        assertEquals(0.0, DecodeTiming.tokensPerSecond(0, 1_000L), 0.0)
        assertEquals(0.0, DecodeTiming.tokensPerSecond(100, 0L), 0.0)
        assertEquals(50.0, DecodeTiming.tokensPerSecond(500, 10_000L), 0.0)
    }
}
