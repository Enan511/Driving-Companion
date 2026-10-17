package io.github.enan511.drivingcompanion.drive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.enan511.drivingcompanion.DrivingApplication

class SessionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(SessionNotifications.ACTION_RESUME, SessionNotifications.ACTION_NEW)) return
        val period = intent.getStringExtra(SessionNotifications.EXTRA_PERIOD) ?: return
        val coordinator = (context.applicationContext as DrivingApplication).coordinator
        val completion = goAsync()
        coordinator.notificationChoice(
            period, intent.getStringExtra(SessionNotifications.EXTRA_HISTORY),
            intent.action == SessionNotifications.ACTION_RESUME,
        ) { completion.finish() }
    }
}
