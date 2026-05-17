package com.swarapulse.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.swarapulse.MainActivity
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AppointmentReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val patientId = inputData.getLong("patientId", -1L)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("patientId", patientId)
            // in real app use deep link URI
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        showNotification("Upcoming Appointment", "You have an appointment in 30 minutes.", pendingIntent)

        return Result.success()
    }

    private fun showNotification(title: String, message: String, pendingIntent: PendingIntent) {
        val channelId = "appointment_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Appointment Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationManager.IMPORTANCE_HIGH)
            .addAction(android.R.drawable.ic_media_play, "Start Visit", pendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze", null) // Intent omitted for scaffold
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2, notification)
    }
}
