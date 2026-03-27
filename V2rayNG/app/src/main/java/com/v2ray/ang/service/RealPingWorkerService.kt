package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayNativeManager
import com.v2ray.ang.handler.V2rayConfigManager
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Worker that runs a batch of real-ping tests with controlled concurrency.
 *
 * Concurrency: at most [CONCURRENCY] native ping calls run in parallel.
 *
 * Early-stop: once [FAST_NODE_TARGET] nodes with delay ≤ [FAST_DELAY_THRESHOLD_MS] are
 * found, any task that hasn't started its native call yet is skipped immediately.
 * Already-running native calls cannot be interrupted (JNI blocking), so they will
 * complete normally but their results won't count toward further early-stop checks.
 */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onFinish: (status: String) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO + CoroutineName("RealPingBatchWorker"))

    // Semaphore limits how many native ping calls run concurrently
    private val semaphore = Semaphore(CONCURRENCY)

    // Flag set to true when early-stop threshold is reached
    private val earlyStop = AtomicBoolean(false)

    private val doneCount = AtomicInteger(0)
    private val fastCount = AtomicInteger(0)
    private val total = guids.size

    fun start() {
        val jobs = guids.map { guid ->
            scope.launch {
                // Before acquiring semaphore, check if we should skip
                if (earlyStop.get()) {
                    notifyProgress()
                    return@launch
                }

                // Acquire a concurrency slot (blocks until a slot is free)
                semaphore.acquire()
                try {
                    // Re-check after acquiring — may have been set while waiting
                    if (earlyStop.get()) {
                        notifyProgress()
                        return@launch
                    }

                    // Run the blocking native call on IO thread
                    val result = withContext(Dispatchers.IO) { startRealPing(guid) }
                    MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_CONFIG_SUCCESS, Pair(guid, result))

                    // Count fast nodes and set early-stop flag if target reached
                    if (result in 1..FAST_DELAY_THRESHOLD_MS) {
                        val fast = fastCount.incrementAndGet()
                        if (fast >= FAST_NODE_TARGET) {
                            earlyStop.set(true)
                        }
                    }
                } finally {
                    semaphore.release()
                    notifyProgress()
                }
            }
        }

        scope.launch {
            joinAll(*jobs.toTypedArray())
            onFinish("0")
        }
    }

    private fun notifyProgress() {
        val done = doneCount.incrementAndGet()
        val fast = fastCount.get()
        // notify UI: "done/total/fast" e.g. "3/100/1"
        MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_CONFIG_NOTIFY, "$done/$total/$fast")
    }

    fun cancel() {
        earlyStop.set(true)
        job.cancel()
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
        /** Max concurrent native ping calls. */
        const val CONCURRENCY = 16

        /** Delay threshold (ms) below which a node is considered "fast". */
        const val FAST_DELAY_THRESHOLD_MS = 300L

        /** Number of fast nodes that triggers early stop. */
        const val FAST_NODE_TARGET = 20
    }
}

