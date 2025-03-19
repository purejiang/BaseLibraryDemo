package com.cyanrain.baselibrary.utils

import android.content.Context
import android.util.Log
import com.cyanrain.baselibrary.common.JsonConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 日志工具类
 *
 * PS：不用实现顺序写入，避免死锁和线程等待耗时，读取的时候再进行排序即可
 * @author CyanRain
 */
object LogUtil {
    private const val TAG = "LogUtil"

    private const val LOG_DIR_NAME = "lgu-logs"
    private const val LOG_PREFIX = "log_"
    private const val LOG_SUFFIX = ".txt"
    public const val MAX_FILE_SIZE = 2 * 1024 * 1024L
    public const val MAX_FILE_COUNT = 5

    private lateinit var mConfig: Config
    private lateinit var mLogBeanConverter: LogBeanConverter

    private val mCoroutineScope = CoroutineScope(Dispatchers.IO)

    //    private val mWriteLock = ReentrantLock()   // 线程里面用这个锁，协程不用
    private val mMutex = Mutex()
    private var mCurrentActiveFile: File? = null

    /**
     * 初始化
     * @param context 上下文
     * @param converter json转换器
     * @param config 配置
     *
     */
    public fun init(
        context: Context,
        converter: LogBeanConverter,
        config: Config = Config(
            isDebug = false,
            isSaveFile = true,
            saveFileDir = getDefaultDir(context),
            maxFileSize = MAX_FILE_SIZE,
            maxFileCount = MAX_FILE_COUNT
        )
    ) {
        mConfig = config
        mLogBeanConverter = converter
        if (mConfig.isSaveFile) {
            mConfig.saveFileDir.let {
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

    public fun getLogFromFile(): List<LogBean>? {

        return null
    }

    /**
     * 清理所有日志文件
     */
    public fun clear() {
        mConfig.saveFileDir.listFiles()?.forEach {
            it.delete()
        }
    }

    /**
     * 停止所有协程
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
        return "${mLogBeanConverter.toJson(logBean)}\n"
    }

    /**
     * 日志格式化
     * @param content 日志格式化后的字符串
     * @return LogBean 日志对象
     */
    private fun str2Log(content: String): LogBean {
        return mLogBeanConverter.fromJson(content)
    }

    /**
     * 获取默认日志保存路径
     * @param context 上下文
     * @return File 日志保存路径
     */
    private fun getDefaultDir(context: Context): File {
        return context.getExternalFilesDir(LOG_DIR_NAME) ?: File(
            context.filesDir,
            LOG_DIR_NAME
        )
    }

    /**
     * 日志打印/写入文件
     * @param level 日志级别
     * @param tag 标签
     * @param message 内容
     * @param throwable 异常
     */
    private fun log(level: LEVEL, tag: String, message: String, throwable: Throwable?) {
        val logBean = LogBean(
            tag = tag,
            message = message,
            level = level,
            time = System.currentTimeMillis(),
            throwable = throwable,
            threadInfo = CommonUtils.getThreadInfo()
        )
        if (mConfig.isDebug) {
            // debug模式才输出日志
            printLog(logBean)
        }
        if (mConfig.isSaveFile) {
            // 保存文件
            mCoroutineScope.launch {
                saveFile(logBean)
            }
        }
    }

    private suspend fun saveFile(logBean: LogBean) {
        val content = log2Str(logBean)
        // 加锁
//        mWriteLock.lock()
        mMutex.lock()
        try {
            mConfig.let { cf ->
                val targetFile = getCurrentActiveFile().also {
                    if (it.length() + content.length > cf.maxFileSize) {
                        mCurrentActiveFile = createNewFile(cf.saveFileDir)
                    }
                }
                // 执行实际写入
                writeFile(targetFile, content)
                //Log.d(TAG, "save file success: ${targetFile.absolutePath}")
                // 维护文件数量
                maintainFileCount()
            }
        } finally {
            // 解锁
//            mWriteLock.unlock()
            mMutex.unlock()
        }
    }

    /**
     * 写入文件
     * @param file 文件
     * @param content 内容
     */
    private fun writeFile(file: File, content: String) {
        try {
            // 高频情况下，用FileWriter会导致频繁访问磁盘进行写入文件
//            FileWriter(file, true).use { writer ->
//                writer.append(content)
//            }
            // 优化点：用bufferWrite，先缓存在内存，满了之后再写入，减少频繁的IO操作，但是在缓存期间应用被关闭/崩了，就不会写入了
            // 默认是8*1024
            FileOutputStream(file, true).bufferedWriter().use {
                it.append(content)
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * 获取新文件
     * @param dir 文件夹
     * @return File 新文件
     */
    private fun createNewFile(dir: File): File {
        val newFile = File(dir, "$LOG_PREFIX${System.currentTimeMillis()}$LOG_SUFFIX")
        if (newFile.exists()) {
            newFile.delete()
        }
        newFile.createNewFile()
        return newFile
    }

    /**
     * 设置当前日志文件
     * @return File 当前日志文件
     */
    private fun getCurrentActiveFile(): File {
        return mCurrentActiveFile ?: run {
            mConfig.saveFileDir.listFiles()
                ?.maxByOrNull { it.lastModified() }
                ?.takeIf { it.length() < mConfig.maxFileSize }
                ?: createNewFile(mConfig.saveFileDir).also {
                    mCurrentActiveFile = it
                }
        }
    }

    /**
     * 维护日志文件数量
     */
    private fun maintainFileCount() {
        mConfig.saveFileDir.listFiles()?.let { list ->
            if (list.size > mConfig.maxFileCount) {
                list.sortedBy { it.lastModified() }
                    .take(list.size - mConfig.maxFileCount)
                    .forEach { it.delete() }
            }
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
        var isSaveFile: Boolean,  // 是否保存日志文件
        var saveFileDir: File,  // 保存路径
        var maxFileSize: Long,  // 文件大小
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