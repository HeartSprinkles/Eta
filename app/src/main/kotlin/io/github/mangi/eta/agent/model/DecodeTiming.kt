package io.github.mangi.eta.agent.model

/**
 * 判定流式解码窗口是否可信。
 *
 * 部分 OpenAI 兼容端点（或代理）会把整段回答一次性下发，此时「首个增量 → 结束」只剩几毫秒，
 * 直接用 tokens / 窗口会算出上万 tok/s 的假速度。窗口不可信时退回整轮耗时（含 TTFT），
 * 若整轮耗时同样过短则不记录速度，界面只展示 TTFT 与总用时。
 */
internal object DecodeTiming {
    /** 低于该窗口的测量没有统计意义。 */
    const val MIN_WINDOW_MS = 200L

    /** 超过该速度视为缓冲式返回造成的假值。 */
    const val MAX_PLAUSIBLE_TPS = 1_000L

    fun tokensPerSecond(outputTokens: Int, windowMs: Long): Double =
        if (windowMs <= 0L || outputTokens <= 0) 0.0 else outputTokens * 1_000.0 / windowMs

    /**
     * 返回用于计算速度的时间窗口：
     * 优先采用可信的流式解码窗口，其次退回整轮耗时，两者都不可信时返回 null（不展示速度）。
     */
    fun effectiveWindowMs(measuredMs: Long?, stepLlmMs: Long, outputTokens: Int): Long? {
        if (outputTokens <= 0) return null
        val measured = measuredMs?.takeIf { it > 0L }
        if (measured != null &&
            measured >= MIN_WINDOW_MS &&
            tokensPerSecond(outputTokens, measured) <= MAX_PLAUSIBLE_TPS
        ) {
            return measured
        }
        return stepLlmMs.takeIf { it >= MIN_WINDOW_MS }
    }
}
