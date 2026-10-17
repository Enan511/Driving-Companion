package io.github.enan511.drivingcompanion.drive

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.test.core.app.ApplicationProvider
import io.github.enan511.drivingcompanion.MainActivity
import io.github.enan511.drivingcompanion.SessionFixtures
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SessionNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = SessionNotifications(context)

    @Test fun `background grace uses countdown while focused app keeps warning in app only`() {
        val waiting = SessionFixtures.returningDriveInGrace()
        val now = SessionFixtures.time(42_000)
        val before = System.currentTimeMillis()
        val background = notifications.build(waiting, focused = false, now)
        val after = System.currentTimeMillis()

        assertEquals("Car disconnected", background.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertTrue(background.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(background.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue(background.`when` in (before + 10_000L)..(after + 10_000L))
        assertTrue(background.actions.isNullOrEmpty())
        assertNull(background.fullScreenIntent)

        val foreground = notifications.build(waiting, focused = true, now)
        assertFalse(foreground.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertFalse(foreground.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertTrue(foreground.actions.isNullOrEmpty())

        // Moving between surfaces must render the original deadline, not restart twenty seconds.
        val later = notifications.build(waiting, focused = false, SessionFixtures.time(47_000))
        assertTrue(later.`when` <= System.currentTimeMillis() + 5_000L)
        assertTrue(later.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
    }

    @Test fun `history actions exist only for background active connection and route to protected receiver`() {
        val active = SessionFixtures.returningDrive()
        val background = notifications.build(active, focused = false, SessionFixtures.time(31_000))
        val actions = requireNotNull(background.actions)
        assertEquals(2, actions.size)
        val resume = actions[0].actionIntent
        val new = actions[1].actionIntent
        assertTrue(resume.isBroadcast)
        assertTrue(resume.isImmutable)
        assertTrue(new.isImmutable)

        val resumeIntent = shadowOf(resume).savedIntent
        assertEquals(SessionActionReceiver::class.java.name, resumeIntent.component!!.className)
        assertEquals(SessionNotifications.ACTION_RESUME, resumeIntent.action)
        assertEquals(active.activePeriodId, resumeIntent.getStringExtra(SessionNotifications.EXTRA_PERIOD))
        assertEquals("session-1", resumeIntent.getStringExtra(SessionNotifications.EXTRA_HISTORY))
        val newIntent = shadowOf(new).savedIntent
        assertEquals(SessionNotifications.ACTION_NEW, newIntent.action)
        assertEquals(active.activePeriodId, newIntent.getStringExtra(SessionNotifications.EXTRA_PERIOD))
        assertNull(newIntent.getStringExtra(SessionNotifications.EXTRA_HISTORY))
        assertNotEquals(resume, new)

        val focused = notifications.build(active, focused = true, SessionFixtures.time(31_000))
        assertTrue(focused.actions.isNullOrEmpty())
        val grace = notifications.build(SessionFixtures.returningDriveInGrace(), false, SessionFixtures.time(33_000))
        assertTrue(grace.actions.isNullOrEmpty())

        val openApp = requireNotNull(background.contentIntent)
        assertTrue(openApp.isActivity)
        assertTrue(openApp.isImmutable)
        assertEquals(MainActivity::class.java.name, shadowOf(openApp).savedIntent.component!!.className)
    }

    @Test fun `new period actions cannot overwrite an old pending intent period token`() {
        val first = SessionFixtures.returningDrive()
        val oldAction = requireNotNull(notifications.build(first, false, SessionFixtures.time(31_000)).actions)[0].actionIntent
        val ended = SessionFixtures.engine.tick(SessionFixtures.returningDriveInGrace(), SessionFixtures.time(52_000))
        val next = SessionFixtures.engine.connectionChanged(
            ended, SessionFixtures.car.id, true, SessionFixtures.time(60_000),
        )
        val newAction = requireNotNull(notifications.build(next, false, SessionFixtures.time(61_000)).actions)[0].actionIntent

        assertNotEquals(oldAction, newAction)
        assertEquals(first.activePeriodId, shadowOf(oldAction).savedIntent.getStringExtra(SessionNotifications.EXTRA_PERIOD))
        assertEquals(next.activePeriodId, shadowOf(newAction).savedIntent.getStringExtra(SessionNotifications.EXTRA_PERIOD))
        assertNotEquals(shadowOf(oldAction).savedIntent.data, shadowOf(newAction).savedIntent.data)
        assertTrue(shadowOf(oldAction).flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test fun `service channel stays quiet and does not request a heads up interruption`() {
        val channel = requireNotNull(context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(SessionNotifications.CHANNEL))
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertFalse(channel.shouldVibrate())
        assertFalse(channel.canShowBadge())
        val notice = notifications.build(SessionFixtures.returningDrive(), false, SessionFixtures.time(31_000))
        assertEquals(Notification.VISIBILITY_PRIVATE, notice.visibility)
        assertTrue(notice.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertNull(notice.fullScreenIntent)
    }
}
