package io.github.enan511.drivingcompanion

import android.app.Application
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import io.github.enan511.drivingcompanion.drive.SessionActionReceiver
import io.github.enan511.drivingcompanion.vehicle.CarPresenceService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlatformManifestTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Suppress("DEPRECATION")
    @Test fun `companion entry point is exported only behind Android binding permission`() {
        val info = context.packageManager.getServiceInfo(ComponentName(context, CarPresenceService::class.java), 0)
        assertTrue(info.exported)
        assertEquals("android.permission.BIND_COMPANION_DEVICE_SERVICE", info.permission)
    }

    @Suppress("DEPRECATION")
    @Test fun `history choice receiver cannot be invoked directly by another application`() {
        val info = context.packageManager.getReceiverInfo(ComponentName(context, SessionActionReceiver::class.java), 0)
        assertFalse(info.exported)
    }
}
