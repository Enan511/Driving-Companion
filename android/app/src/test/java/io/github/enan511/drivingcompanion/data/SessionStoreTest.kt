package io.github.enan511.drivingcompanion.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.enan511.drivingcompanion.SessionFixtures
import io.github.enan511.drivingcompanion.core.drive.SessionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SessionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val databases = mutableListOf<SessionDatabase>()
    // Native SQLite on Windows still encounters path-length limits in long
    // Robolectric test directories; keep the method/database names short.
    private val reopenDatabaseName = "r-${UUID.randomUUID().toString().take(8)}.db"

    private fun memoryDatabase(): SessionDatabase = Room.inMemoryDatabaseBuilder(
        context, SessionDatabase::class.java,
    ).build().also(databases::add)

    private fun fileDatabase(): SessionDatabase = Room.databaseBuilder(
        context, SessionDatabase::class.java, reopenDatabaseName,
    ).build().also(databases::add)

    @After fun cleanup() {
        databases.forEach { if (it.isOpen) it.close() }
        context.deleteDatabase(reopenDatabaseName)
    }

    @Test fun `whole checkpoint roundtrip preserves history choice and original grace deadline`() = runBlocking {
        val store = SessionStore(memoryDatabase())
        assertEquals(SessionSnapshot(), store.read())
        val waiting = SessionFixtures.returningDriveInGrace()
        store.write(waiting)

        val restored = store.read()
        assertEquals(waiting, restored)
        assertEquals(2, restored.records.size)
        assertEquals("Car · \"commuter\"", restored.selectedCar!!.name)
        assertEquals("period-2", restored.pendingChoice!!.periodId)
        assertEquals(10_000L, restored.grace!!.remainingMs(SessionFixtures.time(42_000)))

        // A resumed history and its consumed choice must replace the same checkpoint atomically.
        val reconnected = SessionFixtures.engine.connectionChanged(
            restored, SessionFixtures.car.id, true, SessionFixtures.time(43_000),
        )
        val resumed = SessionFixtures.engine.chooseResume(
            reconnected, reconnected.activePeriodId!!, "session-1", SessionFixtures.time(44_000),
        )
        store.write(resumed)
        val replaced = store.read()
        assertEquals(resumed, replaced)
        assertEquals(1, replaced.records.size)
        assertEquals(listOf("period-1", "period-2"), replaced.records.single().periods.map { it.id })
        assertNull(replaced.pendingChoice)
        assertNull(replaced.grace)
    }

    @Test fun `concurrent reads never observe partially replaced history and active period`(): Unit = runBlocking {
        val store = SessionStore(memoryDatabase())
        val active = SessionFixtures.returningDrive()
        val waiting = SessionFixtures.returningDriveInGrace()
        store.write(active)
        coroutineScope {
            launch(Dispatchers.IO) {
                repeat(24) { store.write(if (it % 2 == 0) waiting else active) }
            }
            launch(Dispatchers.IO) {
                repeat(24) {
                    val checkpoint = store.read()
                    assertTrue("Readers must see one complete committed checkpoint", checkpoint == active || checkpoint == waiting)
                    assertEquals(checkpoint.activePeriodId, checkpoint.pendingChoice!!.periodId)
                    assertNotNull(checkpoint.activeRecord)
                }
            }
        }
    }

    @Test fun `checkpoint survives disk reopen`() = runBlocking {
        context.deleteDatabase(reopenDatabaseName)
        val firstDatabase = fileDatabase()
        val waiting = SessionFixtures.returningDriveInGrace()
        SessionStore(firstDatabase).write(waiting)
        firstDatabase.close()

        val reopened = SessionStore(fileDatabase()).read()
        assertEquals(waiting, reopened)
        val expired = SessionFixtures.engine.reconcile(
            reopened,
            io.github.enan511.drivingcompanion.core.drive.ConnectionEvidence.UNKNOWN,
            SessionFixtures.time(60_000),
        )
        assertFalse(expired.monitoringAllowed)
        assertNull(expired.activePeriodId)
        assertNull(expired.grace)
        assertEquals(2, expired.records.size)
    }

}
