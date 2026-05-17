package com.swarapulse.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swarapulse.data.repository.VisitRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class FollowupReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val visitRepository: VisitRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // In a real app we'd filter for follow-up dates within 48h using a specific DAO method
        // For scaffold, we pretend we got the visits

        showNotification("Follow-up Reminders", "You have upcoming patient follow-ups.")

        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "followup_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Follow-up Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .build()

        notificationManager.notify(1, notification)
    }
}
