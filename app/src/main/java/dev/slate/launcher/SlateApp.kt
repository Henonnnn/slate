package dev.slate.launcher

import android.app.Application
import android.content.Context
import android.os.StrictMode
import java.util.TimeZone

class SlateApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        container = AppContainer(this)
        // The first clock read loads the tz database from disk (~170 ms on a Pixel 9).
        // Warm it here, off the main thread, before the header composes.
        Thread({ TimeZone.getDefault() }, "tz-warmup").start()
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build(),
        )
    }
}

val Context.container: AppContainer
    get() = (applicationContext as SlateApp).container
