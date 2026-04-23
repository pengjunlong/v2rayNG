package com.v2ray.ang.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG_CANCEL
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG_SUCCESS
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.PluginServiceManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.handler.V2rayConfigManager
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import go.Seq
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class V2RayTestService : Service() {

    private var batchJob = SupervisorJob()
    private var batchScope = CoroutineScope(batchJob + Dispatchers.IO + CoroutineName("RealPingBatch"))
    private val earlyStop = AtomicBoolean(false)

    companion object {
        /** Max concurrent native ping calls. */
        const val CONCURRENCY = 8
    }

    override fun onCreate() {
        super.onCreate()
        Seq.setContext(this)
        Libv2ray.initCoreEnv(Utils.userAssetPath(this), Utils.getDeviceIdForXUDPBaseKey())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getIntExtra("key", 0)) {
            MSG_MEASURE_CONFIG -> {
                val guid = intent.serializable<String>("content") ?: ""
                // 单条测试（非批量，如当前节点延迟测试）
                batchScope.launch {
                    val result = startRealPing(guid)
                    MessageUtil.sendMsg2UI(this@V2RayTestService, MSG_MEASURE_CONFIG_SUCCESS, Pair(guid, result))
                }
            }

            AppConfig.MSG_MEASURE_CONFIG_BATCH -> {
                // 批量测试：取消旧批次，重新开始
                cancelBatch()
                @Suppress("UNCHECKED_CAST")
                val guids = intent.serializable<ArrayList<String>>("content") ?: return super.onStartCommand(intent, flags, startId)
                startBatch(guids)
            }

            MSG_MEASURE_CONFIG_CANCEL -> {
                cancelBatch()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startBatch(guids: List<String>) {
        earlyStop.set(false)
        val semaphore = Semaphore(CONCURRENCY)
        val doneCount = AtomicInteger(0)
        val fastCount = AtomicInteger(0)
        val total = guids.size
        // 从 MMKV 读取用户配置，未设置时使用默认值
        val fastDelayThresholdMs = MmkvManager.decodeSettingsString(AppConfig.PREF_FAST_DELAY_THRESHOLD)
            ?.toIntOrNull()?.toLong() ?: AppConfig.DEFAULT_FAST_DELAY_THRESHOLD.toLong()
        val fastNodeTarget = MmkvManager.decodeSettingsString(AppConfig.PREF_FAST_NODE_TARGET)
            ?.toIntOrNull() ?: AppConfig.DEFAULT_FAST_NODE_TARGET

        val jobs = guids.map { guid ->
            batchScope.launch {
                // 在获取 semaphore 前先检查 early-stop
                if (earlyStop.get()) return@launch

                semaphore.acquire()
                try {
                    // 获取 semaphore 后再次检查（等待期间可能已触发）
                    if (earlyStop.get()) return@launch

                    val result = startRealPing(guid)
                    MessageUtil.sendMsg2UI(this@V2RayTestService, MSG_MEASURE_CONFIG_SUCCESS, Pair(guid, result))

                    // 统计快节点，达标时设置 earlyStop
                    if (result in 1..fastDelayThresholdMs) {
                        if (fastCount.incrementAndGet() >= fastNodeTarget) {
                            earlyStop.set(true)
                        }
                    }

                    // 进度通知：done/total/fast
                    val done = doneCount.incrementAndGet()
                    val fast = fastCount.get()
                    MessageUtil.sendMsg2UI(
                        this@V2RayTestService,
                        AppConfig.MSG_MEASURE_CONFIG_NOTIFY,
                        "$done/$total/$fast"
                    )
                } finally {
                    semaphore.release()
                }
            }
        }

        // 等所有 coroutine 结束后通知完成
        batchScope.launch {
            joinAll(*jobs.toTypedArray())
            MessageUtil.sendMsg2UI(this@V2RayTestService, AppConfig.MSG_MEASURE_CONFIG_FINISH, "0")
        }
    }

    private fun cancelBatch() {
        earlyStop.set(true)
        batchJob.cancel()
        // 重建 scope 供下次使用
        batchJob = SupervisorJob()
        batchScope = CoroutineScope(batchJob + Dispatchers.IO + CoroutineName("RealPingBatch"))
    }

    private fun startRealPing(guid: String): Long {
        val retFailure = -1L
        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (config.configType == EConfigType.HYSTERIA2) {
            return PluginServiceManager.realPingHy2(this, config)
        } else {
            val configResult = V2rayConfigManager.getV2rayConfig4Speedtest(this, guid)
            if (!configResult.status) return retFailure
            return SpeedtestManager.realPing(configResult.content)
        }
    }
}
