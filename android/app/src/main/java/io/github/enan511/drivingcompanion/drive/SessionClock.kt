package io.github.enan511.drivingcompanion.drive

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import io.github.enan511.drivingcompanion.core.drive.TimePoint
import java.util.UUID

class SessionClock(context: Context) {
    // A failed boot-count read uses a process-specific identity: conservative recovery
    // is safer than comparing unrelated elapsed timestamps across a reboot.
    private val bootId = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT).toString()
    }.getOrElse { FALLBACK_BOOT_ID }

    fun now() = TimePoint(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootId)

    private companion object {
        val FALLBACK_BOOT_ID = "unverified-${UUID.randomUUID()}"
    }
}
