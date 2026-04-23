package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.WatchdogHelper

/**
 * TV / Android 6 保活守护 Receiver。
 *
 * 触发时机：
 *  1. V2RayVpnService / V2RayProxyOnlyService 在 onTaskRemoved() 时发出
 *     [AppConfig.ACTION_RESTART_SERVICE] 广播，系统在进程被杀后重启本 Receiver 所在进程，
 *     进而重新拉起代理服务。
 *  2. 由 AlarmManager / JobScheduler 定期健康检查回调发出相同广播，
 *     确保服务异常退出后也能在下次心跳时自愈。
 */
class RestartServiceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        if (intent?.action != AppConfig.ACTION_RESTART_SERVICE) return

        Log.i(AppConfig.TAG, "RestartServiceReceiver: received restart signal")

        // 没有选中节点就不重启
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            Log.i(AppConfig.TAG, "RestartServiceReceiver: no server selected, skip")
            return
        }

        // 服务已在运行则无需重启，但仍续期下一次心跳
        if (V2RayServiceManager.isRunning()) {
            Log.i(AppConfig.TAG, "RestartServiceReceiver: service already running, reschedule watchdog")
            WatchdogHelper.schedule(context)
            return
        }

        Log.i(AppConfig.TAG, "RestartServiceReceiver: restarting V2Ray service")
        V2RayServiceManager.startVService(context)
        // 服务重启后续期看门狗，形成滚动链
        WatchdogHelper.schedule(context)
    }
}

