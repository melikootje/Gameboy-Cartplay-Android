package com.gbaoperator.plugin

import android.app.Application
import timber.log.Timber

// @HiltAndroidApp
class GBAOperatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Always plant debug tree for now since BuildConfig is disabled
            Timber.plant(Timber.DebugTree())
            Timber.i("GBA Operator Plugin started successfully")
        } catch (e: Exception) {
            // Fallback logging if Timber fails
            android.util.Log.e("GBAOperatorApp", "Failed to initialize Timber", e)
        }
    }
}
