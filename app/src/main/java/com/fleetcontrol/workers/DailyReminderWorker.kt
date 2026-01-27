package com.fleetcontrol.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fleetcontrol.services.notification.NotificationService

class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val notificationService = NotificationService(applicationContext)
        notificationService.showTripReminder("Review today's fleet activity and profits!")
        return Result.success()
    }
}
