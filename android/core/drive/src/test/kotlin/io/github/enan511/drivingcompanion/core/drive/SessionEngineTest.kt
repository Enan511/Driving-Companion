package io.github.enan511.drivingcompanion.core.drive

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SessionEngineTest {
    private val engine = SessionEngine()
    private val car = SelectedCar("selected-car", "Car", 7)
    private fun time(ms: Long, boot: String = "boot-a", wall: Long = 1_000_000 + ms) = TimePoint(wall, ms, boot)
    private fun configured() = engine.selectCar(SessionSnapshot(), car, time(0))
    private fun connected() = engine.connectionChanged(configured(), car.id, true, time(1_000))
    private fun disconnected() = engine.connectionChanged(connected(), car.id, false, time(5_000))
    private fun ended() = engine.tick(disconnected(), time(25_000))

    @Test fun `other cars never activate or disconnect selected car`() {
        val initial = configured()
        assertEquals(initial, engine.connectionChanged(initial, "headphones", true, time(1_000)))
        val active = connected()
        assertEquals(active, engine.connectionChanged(active, "headphones", false, time(2_000)))
    }

    @Test fun `duplicate callbacks cannot create periods or extend grace`() {
        val active = connected()
        val duplicate = engine.connectionChanged(active, car.id, true, time(2_000))
        assertEquals(active.activePeriod, duplicate.activePeriod)
        assertEquals(1, duplicate.records.size)
        val disconnected = engine.connectionChanged(duplicate, car.id, false, time(5_000))
        val repeated = engine.connectionChanged(disconnected, car.id, false, time(10_000))
        assertEquals(25_000L, repeated.grace!!.deadline.elapsedRealtimeMs)
        assertEquals(1, repeated.activePeriod!!.intervals.size)
    }

    @Test fun `grace reconnect preserves period and excludes disconnected gap`() {
        val disconnected = disconnected()
        val reconnected = engine.connectionChanged(disconnected, car.id, true, time(10_000))
        assertEquals(disconnected.activePeriodId, reconnected.activePeriodId)
        assertTrue(reconnected.monitoringAllowed)
        assertNull(reconnected.grace)
        assertEquals(9_000L, reconnected.activePeriod!!.connectedDurationMs(time(15_000)))
        assertEquals(2, reconnected.activePeriod!!.intervals.size)
    }

    @Test fun `reconnect just before deadline wins but exact deadline starts new period`() {
        assertEquals(disconnected().activePeriodId,
            engine.connectionChanged(disconnected(), car.id, true, time(24_999)).activePeriodId)
        val atDeadline = engine.connectionChanged(disconnected(), car.id, true, time(25_000))
        assertNotEquals(disconnected().activePeriodId, atDeadline.activePeriodId)
        assertEquals(2, atDeadline.records.size)
        assertEquals(4_000L, atDeadline.records.first().connectedDurationMs(time(25_000)))
    }

    @Test fun `expiry and reconnect race has same outcome regardless dispatch order`() {
        val base = disconnected()
        val timerFirst = engine.connectionChanged(engine.tick(base, time(25_000)), car.id, true, time(25_000))
        val connectionFirst = engine.tick(engine.connectionChanged(base, car.id, true, time(25_000)), time(25_000))
        assertEquals(timerFirst, connectionFirst)
        assertEquals(connectionFirst, engine.tick(connectionFirst, time(26_000)))
    }

    @Test fun `countdown remains one monotonic deadline despite wall clock and UI changes`() {
        val base = disconnected()
        // No visibility input can reset the engine. Wall time can jump without changing expiry.
        val inspected = engine.tick(base, time(15_000, wall = -90_000))
        assertEquals(base, inspected)
        assertEquals(10_000L, inspected.grace!!.remainingMs(time(15_000, wall = Long.MAX_VALUE)))
        val expired = engine.tick(inspected, time(25_000, wall = -80_000))
        assertNull(expired.activePeriodId)
        assertNull(expired.grace)
        assertFalse(expired.monitoringAllowed)
    }

    @Test fun `expired timer rejects stale connection callback and choice`() {
        val stopped = engine.tick(disconnected(), time(30_000))
        assertEquals(stopped, engine.connectionChanged(stopped, car.id, true, time(24_000)))
        assertEquals(stopped, engine.chooseResume(stopped, "period-1", "session-1", time(31_000)))
    }

    @Test fun `restored grace uses original deadline and fresh evidence`() {
        val saved = disconnected()
        val recovered = engine.reconcile(saved, ConnectionEvidence.DISCONNECTED, time(20_000))
        assertEquals(saved.grace, recovered.grace)
        assertFalse(recovered.monitoringAllowed)
        assertNull(engine.reconcile(saved, ConnectionEvidence.UNKNOWN, time(26_000)).activePeriodId)
    }

    @Test fun `unknown recovery cannot resurrect active monitoring or count missing time`() {
        val saved = connected()
        val recovered = engine.reconcile(saved, ConnectionEvidence.UNKNOWN, time(7_000))
        assertFalse(recovered.monitoringAllowed)
        assertEquals(ConnectionEvidence.UNKNOWN, recovered.connectionEvidence)
        assertEquals(21_000L, recovered.grace!!.deadline.elapsedRealtimeMs)
        assertEquals(0L, recovered.activePeriod!!.connectedDurationMs(time(7_000)))
        val live = engine.connectionChanged(recovered, car.id, true, time(8_000))
        assertTrue(live.monitoringAllowed)
        assertEquals(2_000L, live.activePeriod!!.connectedDurationMs(time(10_000)))
    }

    @Test fun `fresh connected recovery excludes unseen process gap and respects expired grace`() {
        val recovered = engine.reconcile(connected(), ConnectionEvidence.CONNECTED, time(10_000))
        assertEquals("period-1", recovered.activePeriodId)
        assertEquals(1_000L, recovered.activePeriod!!.connectedDurationMs(time(11_000)))
        val expired = engine.reconcile(disconnected(), ConnectionEvidence.CONNECTED, time(30_000))
        assertEquals("period-2", expired.activePeriodId)
        assertEquals(2, expired.records.size)
    }

    @Test fun `reboot invalidates previous monotonic interval and cannot imply connection`() {
        val saved = engine.connectionChanged(connected(), car.id, true, time(8_000))
        val rebooted = engine.reconcile(saved, ConnectionEvidence.UNKNOWN, time(500, "boot-b"))
        assertNull(rebooted.activePeriod)
        assertNull(rebooted.grace)
        assertFalse(rebooted.monitoringAllowed)
        assertEquals(PeriodEndReason.BOOT_CHANGED, rebooted.records.single().periods.single().endReason)
        assertEquals(7_000L, rebooted.records.single().connectedDurationMs(time(500, "boot-b")))
        val live = engine.connectionChanged(rebooted, car.id, true, time(1_000, "boot-b"))
        assertEquals("period-2", live.activePeriodId)
    }

    @Test fun `later connection defaults to new history and resume moves period once atomically`() {
        val active = engine.connectionChanged(ended(), car.id, true, time(40_000))
        assertEquals(2, active.records.size)
        assertNotNull(active.pendingChoice)
        val period = active.activePeriod!!
        val resumed = engine.chooseResume(active, period.id, "session-1", time(41_000))
        assertEquals(1, resumed.records.size)
        assertEquals(listOf("period-1", "period-2"), resumed.records.single().periods.map { it.id })
        assertEquals(period, resumed.activePeriod)
        assertNull(resumed.pendingChoice)
        assertEquals(resumed, engine.chooseResume(resumed, period.id, "session-1", time(42_000)))
        assertEquals(6_000L, resumed.records.single().connectedDurationMs(time(42_000)))
    }

    @Test fun `new selection consumes choice without restarting current period`() {
        val active = engine.connectionChanged(ended(), car.id, true, time(40_000))
        val chosen = engine.chooseNew(active, active.activePeriodId!!, time(41_000))
        assertNull(chosen.pendingChoice)
        assertEquals(active.records, chosen.records)
        assertEquals(chosen, engine.chooseResume(chosen, active.activePeriodId!!, "session-1", time(42_000)))
    }

    @Test fun `resume previous offers latest driven history after older history is resumed`() {
        val second = engine.connectionChanged(ended(), car.id, true, time(40_000))
        val secondChosen = engine.chooseNew(second, second.activePeriodId!!, time(41_000))
        val secondEnded = engine.tick(engine.connectionChanged(secondChosen, car.id, false, time(45_000)), time(65_000))
        val third = engine.connectionChanged(secondEnded, car.id, true, time(80_000))
        assertEquals("session-2", third.pendingChoice!!.previousRecordIds.last())
        val resumedFirst = engine.chooseResume(third, third.activePeriodId!!, "session-1", time(81_000))
        assertEquals(3L, resumedFirst.activeRecord!!.lastActivitySequence)
        val thirdEnded = engine.tick(engine.connectionChanged(resumedFirst, car.id, false, time(85_000)), time(105_000))
        // Wall time moving backwards must not change which history was most recently driven.
        val fourth = engine.connectionChanged(thirdEnded, car.id, true, time(120_000, wall = 100))
        assertEquals(listOf("session-2", "session-1"), fourth.pendingChoice!!.previousRecordIds)
    }

    @Test fun `prepared history updates activity ordering and legacy snapshots retain ordering`() {
        val second = engine.connectionChanged(ended(), car.id, true, time(40_000))
        val secondEnded = engine.tick(engine.connectionChanged(second, car.id, false, time(45_000)), time(65_000))
        val prepared = engine.prepareHistory(secondEnded, "session-1", time(70_000))
        val third = engine.connectionChanged(prepared, car.id, true, time(80_000))
        assertEquals(3L, third.activeRecord!!.lastActivitySequence)
        val thirdEnded = engine.tick(engine.connectionChanged(third, car.id, false, time(85_000)), time(105_000))
        // Simulate decoding JSON written before lastActivitySequence existed.
        val legacy = thirdEnded.copy(records = thirdEnded.records.map { it.copy(lastActivitySequence = 0) })
        val fourth = engine.connectionChanged(legacy, car.id, true, time(120_000))
        assertEquals(listOf("session-2", "session-1"), fourth.pendingChoice!!.previousRecordIds)
    }

    @Test fun `disconnected selection prepares history but never activates until fresh connection`() {
        val prepared = engine.prepareHistory(ended(), "session-1", time(30_000))
        assertEquals("session-1", prepared.preparedHistoryId)
        assertNull(prepared.activePeriodId)
        assertFalse(prepared.monitoringAllowed)
        val active = engine.connectionChanged(prepared, car.id, true, time(40_000))
        assertEquals(1, active.records.size)
        assertEquals(2, active.records.single().periods.size)
        assertNull(active.pendingChoice)
        assertTrue(active.monitoringAllowed)
    }

    @Test fun `old notification action cannot change another period or different car history`() {
        val firstReturn = engine.connectionChanged(ended(), car.id, true, time(40_000))
        val afterDisconnect = engine.connectionChanged(firstReturn, car.id, false, time(45_000))
        val next = engine.connectionChanged(afterDisconnect, car.id, true, time(70_000))
        assertEquals(next, engine.chooseResume(next, firstReturn.activePeriodId!!, "session-1", time(71_000)))
        val other = engine.selectCar(ended(), SelectedCar("other-car", "Other"), time(30_000))
        assertEquals(other, engine.prepareHistory(other, "session-1", time(31_000)))
    }

    @Test fun `resume cannot activate during grace or after period expiry`() {
        val active = engine.connectionChanged(ended(), car.id, true, time(40_000))
        val waiting = engine.connectionChanged(active, car.id, false, time(45_000))
        assertEquals(waiting, engine.chooseResume(waiting, active.activePeriodId!!, "session-1", time(46_000)))
        val expired = engine.chooseResume(waiting, active.activePeriodId!!, "session-1", time(65_000))
        assertNull(expired.activePeriodId)
        assertNull(expired.pendingChoice)
        assertFalse(expired.monitoringAllowed)
        assertEquals(2, expired.records.size)
    }

    @Test fun `old boot notification choice cannot resume persisted active resources`() {
        val active = engine.connectionChanged(ended(), car.id, true, time(40_000))
        val afterBoot = engine.chooseResume(active, active.activePeriodId!!, "session-1", time(500, "boot-b"))
        assertFalse(afterBoot.monitoringAllowed)
        assertNull(afterBoot.activePeriodId)
        assertEquals(2, afterBoot.records.size)
    }

    @Test fun `selection change closes resources and does not match by device name`() {
        val changed = engine.selectCar(connected(), SelectedCar("new-id", "Car"), time(3_000))
        assertNull(changed.activePeriodId)
        assertFalse(changed.monitoringAllowed)
        assertEquals(PeriodEndReason.CAR_CHANGED, changed.records.single().periods.single().endReason)
        assertEquals(changed, engine.connectionChanged(changed, car.id, true, time(4_000)))
        assertEquals("Car", changed.records.single().carName)
        assertEquals("Car", engine.selectCar(changed, null, time(5_000)).records.single().carName)
    }

    @Test fun `car selection after elapsed grace preserves actual timeout reason and deadline`() {
        val changed = engine.selectCar(disconnected(), SelectedCar("new-id", "Other"), time(30_000))
        val period = changed.records.single().periods.single()
        assertEquals(PeriodEndReason.DISCONNECT_TIMEOUT, period.endReason)
        assertEquals(25_000L, period.endedAt!!.elapsedRealtimeMs)
        assertEquals(4_000L, period.connectedDurationMs(time(30_000)))
    }

    @Test fun `json roundtrip preserves history intervals deadline and selection tokens`() {
        val active = engine.connectionChanged(ended(), car.id, true, time(40_000))
        val state = engine.connectionChanged(active, car.id, false, time(43_000))
        val encoded = Json.encodeToString(state)
        val restored = Json.decodeFromString<SessionSnapshot>(encoded)
        assertEquals(state, restored)
        val safe = engine.reconcile(restored, ConnectionEvidence.UNKNOWN, time(44_000))
        assertFalse(safe.monitoringAllowed)
        assertEquals(state.grace, safe.grace)
    }
}
