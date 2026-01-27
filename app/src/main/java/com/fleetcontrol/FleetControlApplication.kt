package com.fleetcontrol

import android.app.Application
import com.fleetcontrol.data.AppContainer
import com.fleetcontrol.data.DefaultAppContainer
import com.fleetcontrol.BuildConfig
import android.util.Log
import kotlin.system.exitProcess

class FleetControlApplication : Application() {
    // Global Trap for Pre-Main Crashes
    companion object {
        var startupError: Throwable? = null
    }

    val container: AppContainer by lazy { DefaultAppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        
        // StrictMode: Detect Main Thread I/O in Debug Builds
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
        
        try {
            // Setup Crash Logging (But DON'T KILL process immediately, let UI try to show it)
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                startupError = throwable
                if (BuildConfig.DEBUG) {
                    try {
                        val file = java.io.File(getExternalFilesDir(null), "crash_log.txt")
                        java.io.FileWriter(file, true).use {
                            it.append("Crash at ${java.util.Date()}\n")
                            it.append(throwable.stackTraceToString())
                            it.append("\n\n")
                        }
                    } catch (e: Exception) {
                    }
                }
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable)
                } else {
                    exitProcess(2)
                }
            }

            try {
                scheduleDailyReminder()
                
                // Initialize WorkManager for background sync
                container.syncWorkManager.schedulePeriodicSync()
            } catch (e: Exception) {
                // Log WorkManager failure but don't crash app
                Log.e("FleetControlApp", "Error scheduling work", e)
            }
        } catch (e: Throwable) {
            // Catch APPLICATION level initialization errors (e.g. Dagger/Hilt/Container Issues)
            startupError = e
        }
    }

    private fun scheduleDailyReminder() {
        val workManager = androidx.work.WorkManager.getInstance(this)
        
        // Calculate initial delay for 8 PM
        val calendar = java.util.Calendar.getInstance()
        val now = java.util.Calendar.getInstance()
        
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 20) // 8 PM
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        
        if (calendar.before(now)) {
            calendar.add(java.util.Calendar.DATE, 1)
        }
        
        val initialDelay = calendar.timeInMillis - now.timeInMillis
        
        val dailyReminderRequest = androidx.work.PeriodicWorkRequestBuilder<com.fleetcontrol.workers.DailyReminderWorker>(
            1, java.util.concurrent.TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
            
        workManager.enqueueUniquePeriodicWork(
            "daily_reminder",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            dailyReminderRequest
        )
    }
}
