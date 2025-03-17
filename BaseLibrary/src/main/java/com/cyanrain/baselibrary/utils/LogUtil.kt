package com.cyanrain.baselibrary.utils

import android.content.Context
import android.util.Log
import com.cyanrain.baselibrary.common.JsonConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock

object LogUtil {
    private const val TAG = "LogUtil"

    private var mConfig: Config? = null
    private val mCoroutineScope = CoroutineScope(Dispatchers.IO)
    private val mWriteLock = ReentrantLock()
    private var mLogBeanConverter: LogBeanConverter? = null

    public fun init(
        context: Context,
        converter: LogBeanConverter,
        config: Config = Config(
            isDebug = true,
            isSaveFile = true,
            saveFileDir = getDefaultDir(context)
        )
    ) {
        mConfig = config
        mLogBeanConverter = converter
        if (mConfig?.isSaveFile == true) {
            mConfig?.saveFileDir?.let {
                if (!it.exists()) {
                    // 文件不存在，创建文件夹
                    it.mkdirs()
                }
            }
        }
        Log.d(TAG, "LogUtil init success")
    }

    public fun v(tag: String, message: String, throwable: Throwable? = null) {
        log(LEVEL.VERBOSE, tag, message, throwable)
    }

    public fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(LEVEL.DEBUG, tag, message, throwable)
    }

    public fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(LEVEL.INFO, tag, message, throwable)
    }

    public fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(LEVEL.WARN, tag, message, throwable)
    }

    public fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LEVEL.ERROR, tag, message, throwable)
    }

    public fun wtf(tag: String, message: String, throwable: Throwable? = null) {
        log(LEVEL.WFT, tag, message, throwable)
    }

    private fun saveFile(logBean: LogBean) {
        synchronized(LogUtil::class.java) {
            val content = log2Str(logBean)

            mConfig?.saveFileDir?.let { dir ->
                dir.listFiles()?.let { files ->
                    if (files.isEmpty()) {
                        val newFile = createNewFile(dir)
                        writeFile(newFile, content)
                    } else if (files.size <= mConfig?.maxFileCount!!) {
                        files.maxByOrNull { it.lastModified() }?.let {
                            if (it.length() > mConfig?.maxFileSize!!) {
                                writeFile(createNewFile(dir), content)
                            } else {
                                writeFile(it, content)
                            }
                        }
                    } else {
                        files.minByOrNull { it.lastModified() }?.delete()
                    }
                }
            }
        }
    }

    /**
     * 获取新文件
     * @param dir 文件夹
     * @return File 新文件
     */
    private fun createNewFile(dir: File): File {
        val newFile = File(dir, "log_${System.currentTimeMillis()}.txt")
        if (newFile.exists()) {
            newFile.delete()
        }
        newFile.createNewFile()
        return newFile
    }

    /**
     * 写入文件
     * @param file 文件
     * @param content 内容
     * @param isCreate 是否创建文件
     */
    private fun writeFile(file: File, content: String, isCreate: Boolean = false) {
        mCoroutineScope.launch {
            // 加锁
            mWriteLock.lock()
            try {
                if (isCreate) {
                    if (!file.exists()) {
                        file.createNewFile()
                    }
                }
                FileWriter(file, true).use { writer ->
                    writer.append(content)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                // 解锁
                mWriteLock.unlock()
            }
        }
    }

    /**
     * 获取默认日志保存路径
     * @param context 上下文
     * @return File 日志保存路径
     */
    public fun getDefaultDir(context: Context): File {
        return context.getExternalFilesDir("lgu-logs") ?: File(
            context.filesDir,
            "lgu-logs"
        )
    }

    /**
     * 清理所有日志文件
     */
    public fun clear() {
        mConfig?.saveFileDir?.listFiles()?.forEach {
            it.delete()
        }
    }

    /**
     * 停止
     */
    public fun destroy() {
        mCoroutineScope.cancel()
    }

    /**
     * 日志格式化
     * @param logBean 日志对象
     * @return String 日志格式化后的字符串
     */
    private fun log2Str(logBean: LogBean): String {
        val data = CommonUtils.getCurrentTime(logBean.time)
        val stackTrace = CommonUtils.getTrackStackTrace(logBean.throwable)
//        mJsonConverter?.toJson(logBean)
        return mLogBeanConverter?.toJson(logBean) ?: ""
    }

    /**
     * 日志格式化
     * @param logBean 日志对象
     * @return String 日志格式化后的字符串
     */
    private fun str2Log(content: String): LogBean? {

        return mLogBeanConverter?.fromJson(content)
    }


    private fun log(level: LEVEL, tag: String, message: String, throwable: Throwable?) {
        val logBean = LogBean(
            tag = tag,
            message = message,
            level = level,
            time = System.currentTimeMillis(),
            throwable = throwable,
            threadInfo = CommonUtils.getThreadInfo()
        )
        if (mConfig?.isDebug == true) {
            // debug模式才输出日志
            printLog(logBean)
        }
        if (mConfig?.isSaveFile == true) {
            // 保存文件
            saveFile(logBean)
        }
    }

    private fun printLog(logBean: LogBean) {
        when (logBean.level) {
            LEVEL.VERBOSE -> Log.v(logBean.tag, logBean.message, logBean.throwable)
            LEVEL.DEBUG -> Log.d(logBean.tag, logBean.message, logBean.throwable)
            LEVEL.INFO -> Log.i(logBean.tag, logBean.message, logBean.throwable)
            LEVEL.WARN -> Log.w(logBean.tag, logBean.message, logBean.throwable)
            LEVEL.ERROR -> Log.e(logBean.tag, logBean.message, logBean.throwable)
            LEVEL.WFT -> Log.wtf(logBean.tag, logBean.message, logBean.throwable)
        }
    }

    public data class LogBean(
        var tag: String,  // 标签
        var message: String,  // 内容
        var level: LEVEL,  // 日志
        var time: Long,  // 时间
        var throwable: Throwable?,  // 异常
        var threadInfo: String  // 线程信息
    )

    public data class Config(
        var isDebug: Boolean, // 是否是debug模式
        var isSaveFile: Boolean,  // 是否保存
        var saveFileDir: File,  // 保存路径
        var maxFileSize: Long = 1 * 1024,  // 文件大小
        var maxFileCount: Int = 5 // 文件数量
    )

    public enum class LEVEL {
        VERBOSE,  // 详细
        DEBUG,  // 调试
        INFO,  // 信息
        WARN,  // 警告
        ERROR,  // 错误
        WFT  // 严重错误
    }

    // 日志工具的json转换器
    public interface LogBeanConverter : JsonConverter<LogBean> {

        override fun toJson(any: LogBean): String

        override fun fromJson(json: String): LogBean
    }
}