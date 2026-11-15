package com.silora.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build

class SiloraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val receiver=object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Core.executor.execute { try { Core.get(context).reconcile("系统铃声变化") } catch(e: Exception) { android.util.Log.e("Silora","状态同步失败",e) } }
            }
        }
        val filter=IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,filter)
    }
}
