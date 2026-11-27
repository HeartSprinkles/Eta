package io.github.mangi.eta.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenSpeedTest {
    @Test
    fun `真流式按 vLLM 口径用 n 减 1 计算解码速度`() {
        val speed = TokenSpeed.of(outputTokens = 1_000, ttftMs = 10_000L, totalMs = 20_000L)

        assertTrue(speed is TokenSpeed.Streamed)
        assertEquals(99.9, speed.tokensPerSecond, 0.001)
    }

    @Test
    fun `真机缓冲式响应退回端到端吞吐`() {
        // 真机记录：1348 tokens、TTFT 266903ms、整轮 267039ms，旧实现算出 9912 tok/s。
        val speed = TokenSpeed.of(outputTokens = 1_348, ttftMs = 266_903L, totalMs = 267_039L)

        assertTrue(speed is TokenSpeed.Buffered)
        assertEquals(5.047, speed.tokensPerSecond, 0.001)
    }

    @Test
    fun `解码区间短于下限时视为缓冲式`() {
        val speed = TokenSpeed.of(outputTokens = 200, ttftMs = 0L, totalMs = 50L)

        assertTrue(speed is TokenSpeed.Buffered)
        assertEquals(4_000.0, speed.tokensPerSecond, 0.001)
    }

    @Test
    fun `解码区间恰好占整轮百分之一时仍按流式计算`() {
        val speed = TokenSpeed.of(outputTokens = 100, ttftMs = 9_900L, totalMs = 10_000L)

        assertTrue(speed is TokenSpeed.Streamed)
        assertEquals(990.0, speed.tokensPerSecond, 0.001)
    }

    @Test
    fun `解码区间略低于百分之一时按缓冲式处理`() {
        val speed = TokenSpeed.of(outputTokens = 100, ttftMs = 9_901L, totalMs = 10_000L)

        assertTrue(speed is TokenSpeed.Buffered)
        assertEquals(10.0, speed.tokensPerSecond, 0.001)
    }

    @Test
    fun `单 token 回答没有解码速度`() {
        assertEquals(TokenSpeed.Unknown, TokenSpeed.of(outputTokens = 1, ttftMs = 0L, totalMs = 5_000L))
    }

    @Test
    fun `缺少 TTFT 时按零处理`() {
        val speed = TokenSpeed.of(outputTokens = 500, ttftMs = null, totalMs = 10_000L)

        assertTrue(speed is TokenSpeed.Streamed)
        assertEquals(49.9, speed.tokensPerSecond, 0.001)
    }

    @Test
    fun `TTFT 超过整轮耗时时不会出现负窗口`() {
        val speed = TokenSpeed.of(outputTokens = 100, ttftMs = 12_000L, totalMs = 10_000L)

        assertTrue(speed is TokenSpeed.Buffered)
        assertEquals(10.0, speed.tokensPerSecond, 0.001)
    }

    @Test
    fun `缺少 token 数或总耗时时不展示速度`() {
        assertEquals(TokenSpeed.Unknown, TokenSpeed.of(outputTokens = null, ttftMs = 1L, totalMs = 2L))
        assertEquals(TokenSpeed.Unknown, TokenSpeed.of(outputTokens = 0, ttftMs = 1L, totalMs = 2L))
        assertEquals(TokenSpeed.Unknown, TokenSpeed.of(outputTokens = 10, ttftMs = 1L, totalMs = null))
        assertEquals(TokenSpeed.Unknown, TokenSpeed.of(outputTokens = 10, ttftMs = 1L, totalMs = 0L))
        assertEquals(0.0, TokenSpeed.Unknown.tokensPerSecond, 0.0)
    }
}
