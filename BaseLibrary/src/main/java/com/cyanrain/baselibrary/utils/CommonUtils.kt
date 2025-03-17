package com.cyanrain.baselibrary.utils

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommonUtils {
    companion object {
        fun getThreadInfo(): String {
            // 获取当前线程
            // 获取当前线程的id和名称
            val thread: Thread = Thread.currentThread()
            return thread.name + "(" + thread.id + ")"
        }

        fun getCurrentTime(time: Long = System.currentTimeMillis()): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return dateFormat.format(Date(time))
        }

        fun getTrackStackTrace(e: Throwable?): String {
            val writer = StringWriter()
            e?.printStackTrace(PrintWriter(writer))
            return writer.toString()
        }

    }
}