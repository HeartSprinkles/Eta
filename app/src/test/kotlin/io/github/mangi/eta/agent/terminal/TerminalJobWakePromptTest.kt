package io.github.mangi.eta.agent.terminal

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalJobWakePromptTest {
    private fun task(
        id: String = "job_abcd1234",
        description: String = "后台构建",
        exitCode: Int? = 0,
        completedAt: Long? = 1_000L,
        endReason: String? = null,
        logPath: String = "/data/local/tmp/eta/daemon/$id.log",
        wakeConversationKey: String? = "conv-1",
    ): DetachedTask = DetachedTask(
        id = id,
        pid = 42L,
        token = "token",
        command = "sleep 1",
        cwd = "/workspace",
        identity = "root",
        environment = TerminalEnvironment.ANDROID,
        logPath = logPath,
        startedAt = 0L,
        commandTask = true,
        description = description,
        completedAt = completedAt,
        exitCode = exitCode,
        endReason = endReason,
        wakeConversationKey = wakeConversationKey,
    )

    @Test
    fun `续跑指令包含任务标识、退出码与日志路径`() {
        val prompt = TerminalJobWakePrompt.build(task(), "构建成功")

        assertTrue(prompt.contains("job_abcd1234"))
        assertTrue(prompt.contains("后台构建"))
        assertTrue(prompt.contains("exit=0"))
        assertTrue(prompt.contains("/data/local/tmp/eta/daemon/job_abcd1234.log"))
        assertTrue(prompt.contains("构建成功"))
        assertTrue(prompt.startsWith("[后台任务完成]"))
    }

    @Test
    fun `空日志只说明日志为空`() {
        val prompt = TerminalJobWakePrompt.build(task(), "   \n ")

        assertTrue(prompt.contains("日志为空。"))
        assertFalse(prompt.contains("```"))
    }

    @Test
    fun `失败任务在指令里标记为失败`() {
        val prompt = TerminalJobWakePrompt.build(task(exitCode = 2), "error")

        assertTrue(prompt.contains("失败"))
        assertTrue(prompt.contains("exit=2"))
    }

    @Test
    fun `超时与停止使用各自的措辞`() {
        assertTrue(TerminalJobWakePrompt.build(task(exitCode = null, endReason = "timed_out"), "").contains("已超时"))
        assertTrue(TerminalJobWakePrompt.build(task(exitCode = null, endReason = "stopped"), "").contains("已被停止"))
        assertTrue(TerminalJobWakePrompt.build(task(exitCode = null, endReason = "interrupted"), "").contains("被中断"))
    }

    @Test
    fun `日志尾部只保留最后若干字符`() {
        val file = File.createTempFile("eta-job-log", ".txt")
        try {
            file.writeText("a".repeat(5_000) + "TAIL")
            val tail = TerminalJobWakePrompt.tail(file, maxChars = 16)

            assertEquals(16, tail.length)
            assertTrue(tail.endsWith("TAIL"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `日志文件缺失时返回空字符串`() {
        val missing = File("/data/local/tmp/eta/definitely-missing-${System.nanoTime()}.log")

        assertEquals("", TerminalJobWakePrompt.tail(missing))
    }
}
