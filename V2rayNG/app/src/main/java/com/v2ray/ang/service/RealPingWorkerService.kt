package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayNativeManager
import com.v2ray.ang.handler.V2rayConfigManager
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Worker that runs a batch of real-ping tests independently.
 * Each batch owns its own CoroutineScope/dispatcher and can be cancelled separately.
 */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onFinish: (status: String) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val cpu = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private val dispatcher = Executors.newFixedThreadPool(cpu * 4).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))

    private val doneCount = AtomicInteger(0)
    private val total = guids.size

    fun start() {
        val jobs = guids.map { guid ->
            scope.launch {
                try {
                    val result = startRealPing(guid)
                    MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_CONFIG_SUCCESS, Pair(guid, result))
                } finally {
                    val done = doneCount.incrementAndGet()
                    // notify UI: "done/total" e.g. "3/100"
                    MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_CONFIG_NOTIFY, "$done/$total")
                }
            }
        }

        scope.launch {
            try {
                joinAll(*jobs.toTypedArray())
                onFinish("0")
            } catch (_: CancellationException) {
                onFinish("-1")
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        job.cancel()
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun startRealPing(guid: String): Long {
        val retFailure = -1L
        val configResult = V2rayConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return retFailure
        }
        return V2RayNativeManager.measureOutboundDelay(configResult.content, SettingsManager.getDelayTestUrl())
    }
}

