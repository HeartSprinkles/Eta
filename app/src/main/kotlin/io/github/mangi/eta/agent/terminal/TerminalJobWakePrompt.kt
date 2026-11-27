package io.github.mangi.eta.agent.terminal

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import org.json.JSONArray

/**
 * 把已完成的后台命令整理成一条可发送的续跑指令。
 *
 * 只读取任务记录与日志尾部，不改动任务状态；真正的续跑由 App 内会话按普通发送流程完成。
 */
internal object TerminalJobWakePrompt {
    private const val MAX_LOG_TAIL_CHARS = 1_200

    fun build(task: DetachedTask, logTail: String): String {
        val status = when (task.commandStatus) {
            "completed" -> "已完成"
            "timed_out" -> "已超时"
            "stopped" -> "已被停止"
            "interrupted" -> "被中断"
            else -> "失败"
        }
        val header = buildString {
            append("[后台任务完成] ")
            append(task.description.ifBlank { task.command })
            append("（job_id=").append(task.id).append("，").append(status)
            task.exitCode?.let { append("，exit=").append(it) }
            append("，日志=").append(task.logPath).append("）")
        }
        val tail = logTail.trim()
        val body = if (tail.isEmpty()) "日志为空。" else "日志末尾：\n```\n$tail\n```"
        return buildString {
            append(header).append('\n')
            append("请先读取上面的日志确认结果，然后继续完成原任务；如果失败，说明原因并给出下一步。\n")
            append(body)
        }
    }

    /** 只取日志尾部，避免把长日志整段塞进上下文。 */
    fun tail(logFile: File, maxChars: Int = MAX_LOG_TAIL_CHARS): String {
        if (!logFile.isFile) return ""
        return runCatching {
            val length = logFile.length()
            if (length <= 0L) return@runCatching ""
            val start = (length - maxChars.toLong() * 4L).coerceAtLeast(0L)
            RandomAccessFile(logFile, "r").use { file ->
                file.seek(start)
                val bytes = ByteArray((length - start).toInt())
                file.readFully(bytes)
                String(bytes, Charsets.UTF_8).takeLast(maxChars)
            }
        }.getOrElse { "" }
    }

    fun findTask(context: Context, jobId: String): DetachedTask? = runCatching {
        val records = DetachedTaskSupervisor.defaultRecordsFile(context)
        if (!records.isFile) return@runCatching null
        val array = JSONArray(records.readText())
        (0 until array.length())
            .map { index -> array.getJSONObject(index).toDetachedTask() }
            .firstOrNull { task -> task.commandTask && task.id == jobId }
    }.getOrNull()

    fun load(context: Context, jobId: String): String? {
        val task = findTask(context, jobId) ?: return null
        if (task.completedAt == null) return null
        return build(task, tail(File(task.logPath)))
    }
}
