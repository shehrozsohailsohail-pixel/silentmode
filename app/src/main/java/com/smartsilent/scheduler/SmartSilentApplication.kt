package com.smartsilent.scheduler

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.smartsilent.scheduler.lifecycle.UnlockReceiver
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartSilentApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Framework Section 15 - ACTION_USER_PRESENT is one of the implicit
        // broadcasts Android disallows as a manifest-declared receiver since
        // API 26 (see UnlockReceiver's class doc for why), so it's
        // registered dynamically here instead, for as long as this process
        // stays alive. The exported/not-exported flag became a required
        // argument on API 33+; below that, the two-argument overload is the
        // only one that exists.
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(UnlockReceiver(), filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(UnlockReceiver(), filter)
        }
    }
}
