package io.github.mangi.eta.ui.model

/**
 * 生成速度读数。
 *
 * 与 vLLM / llama.cpp / Ollama 的口径一致：解码速度 = (n − 1) / (总耗时 − TTFT)，
 * 首个 token 属于 TTFT，不进入解码区间。缓冲式返回（整段回答一次下发）没有可测的解码区间，
 * 此时只给端到端吞吐 n / 总耗时，界面用 `≈` 前缀区分，两种读数绝不混用。
 */
internal sealed interface TokenSpeed {
    val tokensPerSecond: Double

    /** 真流式：解码区间可测。 */
    data class Streamed(override val tokensPerSecond: Double) : TokenSpeed

    /** 缓冲式：只有端到端吞吐可测。 */
    data class Buffered(override val tokensPerSecond: Double) : TokenSpeed

    /** 连端到端吞吐都无法计算。 */
    data object Unknown : TokenSpeed {
        override val tokensPerSecond: Double get() = 0.0
    }

    companion object {
        /** 解码区间短于该值即视为“不是流式”。 */
        const val MIN_DECODE_WINDOW_MS = 100L

        /** 解码区间不足整轮耗时的 1%，说明回答是最后一刻整段下发的。 */
        const val MIN_DECODE_WINDOW_RATIO = 0.01

        fun of(outputTokens: Int?, ttftMs: Long?, totalMs: Long?): TokenSpeed {
            val tokens = outputTokens?.takeIf { it > 0 } ?: return Unknown
            val total = totalMs?.takeIf { it > 0L } ?: return Unknown
            val ttft = (ttftMs ?: 0L).coerceIn(0L, total)
            val decodeMs = total - ttft
            val buffered = decodeMs < MIN_DECODE_WINDOW_MS ||
                decodeMs.toDouble() < total.toDouble() * MIN_DECODE_WINDOW_RATIO
            if (buffered) return Buffered(tokens * 1_000.0 / total)
            if (tokens <= 1) return Unknown
            return Streamed((tokens - 1) * 1_000.0 / decodeMs)
        }
    }
}
