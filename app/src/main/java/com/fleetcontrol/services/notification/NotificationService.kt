package com.fleetcontrol.services.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint

/**
 * Notification Service for FleetControl
 * Handles daily summaries and trip reminders
 */
class NotificationService(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID_DAILY = "fleetcontrol_daily"
        const val CHANNEL_ID_REMINDERS = "fleetcontrol_reminders"
        const val NOTIFICATION_ID_DAILY_SUMMARY = 1001
        const val NOTIFICATION_ID_TRIP_REMINDER = 1002
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            
            // Daily Summary Channel
            val dailyChannel = NotificationChannel(
                CHANNEL_ID_DAILY,
                "Daily Summary",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily profit and trip summaries"
            }
            
            // Reminders Channel
            val reminderChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Trip entry and fuel reminders"
            }
            
            notificationManager.createNotificationChannel(dailyChannel)
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }
    
    /**
     * Show daily summary notification
     */
    @SuppressLint("MissingPermission")
    fun showDailySummary(
        tripCount: Int,
        profit: Double,
        driverCount: Int
    ) {
        if (!hasNotificationPermission()) return
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_DAILY)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Today's Summary")
            .setContentText("$tripCount trips • ₹${String.format("%.0f", profit)} profit • $driverCount drivers")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DAILY_SUMMARY, notification)
    }
    
    /**
     * Show trip reminder notification
     */
    @SuppressLint("MissingPermission")
    fun showTripReminder(message: String = "Don't forget to log your trips for today!") {
        if (!hasNotificationPermission()) return
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("Trip Reminder")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TRIP_REMINDER, notification)
    }
    
    /**
     * Show low advance warning
     */
    @SuppressLint("MissingPermission")
    fun showAdvanceWarning(driverName: String, amount: Double) {
        if (!hasNotificationPermission()) return
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("High Advance Balance")
            .setContentText("$driverName has ₹${String.format("%.0f", amount)} outstanding")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_TRIP_REMINDER + driverName.hashCode(),
            notification
        )
    }
    
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
