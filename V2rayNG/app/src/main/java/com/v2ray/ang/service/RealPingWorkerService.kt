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
 *
 * Early-stop: once [FAST_NODE_TARGET] nodes with delay < [FAST_DELAY_THRESHOLD_MS] are
 * found, remaining tests are cancelled immediately.
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
    private val fastCount = AtomicInteger(0)
    private val total = guids.size

    fun start() {
        val jobs = guids.map { guid ->
            scope.launch {
                // Skip if already early-stopped
                if (job.isCancelled) return@launch
                try {
                    val result = startRealPing(guid)
                    MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_CONFIG_SUCCESS, Pair(guid, result))

                    // Count fast nodes and trigger early stop if target reached
                    if (result in 1..FAST_DELAY_THRESHOLD_MS) {
                        val fast = fastCount.incrementAndGet()
                        if (fast >= FAST_NODE_TARGET) {
                            job.cancel()
                        }
                    }
                } finally {
                    val done = doneCount.incrementAndGet()
                    val fast = fastCount.get()
                    // notify UI: "done/total/fast" e.g. "3/100/1"
                    MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_CONFIG_NOTIFY, "$done/$total/$fast")
                }
            }
        }

        scope.launch {
            try {
                joinAll(*jobs.toTypedArray())
                onFinish("0")
            } catch (_: CancellationException) {
                // Early stop triggered — still treat as success so sort/reload runs
                onFinish("0")
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

    companion object {
        /** Delay threshold (ms) below which a node is considered "fast". */
        const val FAST_DELAY_THRESHOLD_MS = 300L

        /** Number of fast nodes that triggers early stop. */
        const val FAST_NODE_TARGET = 10
    }
}

