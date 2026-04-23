package com.v2ray.ang.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.WatchdogHelper.cancel
import com.v2ray.ang.util.WatchdogHelper.schedule

/**
 * TV / Android 6 保活看门狗。
 *
 * 原理：使用 [AlarmManager] 每隔 [AppConfig.WATCHDOG_INTERVAL_MS] 毫秒发送一次
 * [AppConfig.ACTION_RESTART_SERVICE] 广播。[RestartServiceReceiver][com.v2ray.ang.receiver.RestartServiceReceiver]
 * 收到后检查代理服务是否在运行，若已停止则重新拉起。
 *
 * - API 23+：使用 [AlarmManager.setExactAndAllowWhileIdle]，即使 Doze 也能精确触发
 * - API 21-22（Android 5）：使用 [AlarmManager.setExact]
 *
 * 调用 [schedule] 设置/续期下一个心跳；[cancel] 取消全部心跳。
 * 每次 Receiver 被触发后需再次调用 [schedule]，形成滚动链。
 */
object WatchdogHelper {

    private const val REQUEST_CODE = 9527

    /**
     * 安排下一次看门狗心跳。
     * 幂等：重复调用只会更新触发时间，不会叠加多个闹钟。
     */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context) ?: return
        val triggerAt = System.currentTimeMillis() + AppConfig.WATCHDOG_INTERVAL_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // API 23+: 可穿透 Doze 模式
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // API 21-22
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.i(AppConfig.TAG, "WatchdogHelper: next heartbeat in ${AppConfig.WATCHDOG_INTERVAL_MS / 1000}s")
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "WatchdogHelper: failed to schedule alarm", e)
        }
    }

    /**
     * 取消看门狗心跳（用户主动停止代理时调用）。
     */
    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context) ?: return
        try {
            am.cancel(pi)
            Log.i(AppConfig.TAG, "WatchdogHelper: alarm cancelled")
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "WatchdogHelper: failed to cancel alarm", e)
        }
    }

    private fun buildPendingIntent(context: Context): PendingIntent? {
        return try {
            val intent = Intent(AppConfig.ACTION_RESTART_SERVICE).apply {
                `package` = context.packageName
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "WatchdogHelper: failed to build PendingIntent", e)
            null
        }
    }
}

