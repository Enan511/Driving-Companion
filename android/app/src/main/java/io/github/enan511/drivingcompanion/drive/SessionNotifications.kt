package io.github.enan511.drivingcompanion.drive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.enan511.drivingcompanion.MainActivity
import io.github.enan511.drivingcompanion.R
import io.github.enan511.drivingcompanion.core.drive.SessionSnapshot
import io.github.enan511.drivingcompanion.core.drive.TimePoint

class SessionNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = "Vehicle connection, reconnect countdown and session history choices"
            setShowBadge(false)
            enableVibration(false)
        })
    }

    fun build(snapshot: SessionSnapshot?, focused: Boolean, now: TimePoint): Notification {
        val grace = snapshot?.grace
        val contentIntent = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle(snapshot?.selectedCar?.name ?: "Driving Companion")
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setShowWhen(false)

        when {
            grace != null && grace.remainingMs(now) == 0L -> builder
                .setContentText("Verifying the car connection")
            grace != null && !focused -> builder
                .setContentTitle("Car disconnected")
                .setContentText("Waiting for the car to reconnect")
                .setWhen(System.currentTimeMillis() + grace.remainingMs(now))
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
            grace != null -> builder.setContentText("Connection interrupted — reconnecting")
            snapshot?.monitoringAllowed == true -> builder.setContentText("Driving session active")
            else -> builder.setContentText("Verifying vehicle connection")
        }

        val choice = snapshot?.pendingChoice
        if (choice != null && snapshot.monitoringAllowed && !focused) {
            val previousId = choice.previousRecordIds.lastOrNull()
            if (previousId != null) builder.addAction(Notification.Action.Builder(
                null, "Resume previous", choiceIntent(ACTION_RESUME, choice.periodId, previousId),
            ).build())
            builder.addAction(Notification.Action.Builder(
                null, "New session", choiceIntent(ACTION_NEW, choice.periodId, null),
            ).build())
        }
        return builder.build()
    }

    private fun choiceIntent(action: String, periodId: String, historyId: String?): PendingIntent {
        val intent = Intent(context, SessionActionReceiver::class.java).apply {
            this.action = action
            data = Uri.Builder().scheme("driving-companion").authority("session-choice")
                .appendPath(periodId).appendPath(action).appendPath(historyId ?: "new").build()
            putExtra(EXTRA_PERIOD, periodId)
            putExtra(EXTRA_HISTORY, historyId)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL = "driving-session"
        const val ID = 100
        const val ACTION_RESUME = "io.github.enan511.drivingcompanion.RESUME_HISTORY"
        const val ACTION_NEW = "io.github.enan511.drivingcompanion.NEW_HISTORY"
        const val EXTRA_PERIOD = "period"
        const val EXTRA_HISTORY = "history"
    }
}
