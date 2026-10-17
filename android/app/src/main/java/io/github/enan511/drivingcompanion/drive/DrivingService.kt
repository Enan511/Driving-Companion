package io.github.enan511.drivingcompanion.drive

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import io.github.enan511.drivingcompanion.DrivingApplication
import io.github.enan511.drivingcompanion.core.drive.SessionSnapshot

class DrivingService : Service() {
    private val coordinator get() = (application as DrivingApplication).coordinator
    private lateinit var notifications: SessionNotifications
    private lateinit var clock: SessionClock
    private var foregroundReady = false
    private var graceWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notifications = SessionNotifications(this)
        clock = SessionClock(this)
        enterForeground()
    }

    private fun enterForeground() {
        try {
            // Meet the foreground deadline before any database/profile query.
            startForeground(SessionNotifications.ID, notifications.build(null, false, clock.now()), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            foregroundReady = true
        } catch (_: SecurityException) {
            coordinator.serviceFailed("Bluetooth access is required to run the vehicle session.")
            stopSelf()
        } catch (_: RuntimeException) {
            coordinator.serviceFailed("Android did not allow the driving service to start. Open the app to review setup.")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A reconnect can deliver a start while an earlier stop is being processed.
        if (!foregroundReady) enterForeground()
        if (foregroundReady) coordinator.serviceReady(this)
        // Presence/connection callbacks or a user reopening the app reconcile process death.
        // Do not blindly restart an active session from a stale sticky intent.
        return START_NOT_STICKY
    }

    fun update(snapshot: SessionSnapshot, focused: Boolean) {
        if (!foregroundReady) return
        val grace = snapshot.grace
        if (grace != null) {
            if (graceWakeLock?.isHeld != true) {
                graceWakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:ReconnectGrace").apply {
                        setReferenceCounted(false)
                        // CPU remains awake only for this short deadline plus the bounded
                        // profile confirmation. There is no wake lock during normal driving.
                        acquire((grace.remainingMs(clock.now()) + 6_000).coerceIn(1_000, 26_000))
                    }
            }
        } else releaseGraceWakeLock()
        getSystemService(android.app.NotificationManager::class.java)
            .notify(SessionNotifications.ID, notifications.build(snapshot, focused, clock.now()))
    }

    fun finishSession() {
        foregroundReady = false
        releaseGraceWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        foregroundReady = false
        releaseGraceWakeLock()
        coordinator.serviceDestroyed(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun releaseGraceWakeLock() {
        graceWakeLock?.takeIf { it.isHeld }?.release()
        graceWakeLock = null
    }
}
