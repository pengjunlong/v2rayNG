package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.WatchdogHelper

class BootReceiver : BroadcastReceiver() {
    /**
     * 处理开机广播（BOOT_COMPLETED / LOCKED_BOOT_COMPLETED）。
     * TV / Android 6 盒子需要在开机后自动启动代理服务并注册看门狗。
     */
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val action = intent?.action ?: return
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
                action == "android.intent.action.LOCKED_BOOT_COMPLETED"
        if (!isBoot) return

        Log.i(AppConfig.TAG, "BootReceiver: action=$action")

        if (!MmkvManager.decodeStartOnBoot() || MmkvManager.getSelectServer().isNullOrEmpty()) {
            return
        }

        V2RayServiceManager.startVService(context)

        // 注册看门狗，开机后定期检测服务存活
        WatchdogHelper.schedule(context)
    }
}
