package com.cyanrain.baselibrarydemo

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cyanrain.baselibrary.common.JsonConverter
import com.cyanrain.baselibrary.utils.CommonUtils
import com.cyanrain.baselibrary.utils.FileUtils
import com.cyanrain.baselibrary.utils.LogUtil
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate")
        LogUtil.init(this, object : LogUtil.LogBeanConverter{
            override fun toJson(any: LogUtil.LogBean): String {
                return Gson().toJson(any)
            }

            override fun fromJson(json: String): LogUtil.LogBean {
                return Gson().fromJson(json, LogUtil.LogBean::class.java)
            }
        }){
            isDebug = true
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets

        }
        findViewById<TextView>(R.id.tv_write_logs).let {
            it.setOnClickListener {
//                try {
//                    LogUtil.e("MainActivity", "aaa", RuntimeException("RuntimeException:"))
//                    Toast.makeText(this, Gson().toJson(RuntimeException("RuntimeException:").stackTrace), Toast.LENGTH_SHORT).show()
//                    throw
//                }catch (e: Exception){
//                    e.printStackTrace()
//                   =
//                }
//                writeLogs()
                FileUtils.writeFile<String>{
                    target = File(filesDir, "logs.txt")
                    data = "Hello World"
                    mode = FileUtils.WriteMode.OVERWRITE
                }
            }
        }
    }

    private fun writeLogs() {
        val executor = ThreadPoolExecutor(5, 30, 60, TimeUnit.SECONDS, LinkedBlockingQueue())

        for (i in 0..10000) {
            executor.submit {
                try {
                    throw RuntimeException("RuntimeException:$i")
                }catch (e: Exception){
                    e.printStackTrace()
                    LogUtil.e("MainActivity", "id：$i",e)
                }


            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.destroy()
    }
}