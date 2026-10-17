package io.github.enan511.drivingcompanion.core.drive

import kotlinx.serialization.Serializable

/** Wall time is display metadata; deadlines and durations use elapsed time within one boot. */
@Serializable
data class TimePoint(val wallTimeMs: Long, val elapsedRealtimeMs: Long, val bootId: String) {
    init { require(elapsedRealtimeMs >= 0); require(bootId.isNotBlank()) }
    fun plus(milliseconds: Long) = copy(
        wallTimeMs = wallTimeMs + milliseconds,
        elapsedRealtimeMs = elapsedRealtimeMs + milliseconds,
    )
}

@Serializable
data class SelectedCar(val id: String, val name: String, val associationId: Int? = null) {
    init { require(id.isNotBlank()) }
}

@Serializable
enum class ConnectionEvidence { CONNECTED, DISCONNECTED, UNKNOWN }

@Serializable
enum class PeriodEndReason { DISCONNECT_TIMEOUT, CAR_CHANGED, BOOT_CHANGED, RECOVERY_UNCERTAIN }

@Serializable
data class ConnectedInterval(val startedAt: TimePoint, val endedAt: TimePoint? = null) {
    fun durationMs(now: TimePoint): Long {
        val end = endedAt ?: now
        return if (end.bootId == startedAt.bootId) {
            (end.elapsedRealtimeMs - startedAt.elapsedRealtimeMs).coerceAtLeast(0)
        } else 0
    }
}

@Serializable
data class DrivingPeriod(
    val id: String,
    val startedAt: TimePoint,
    val intervals: List<ConnectedInterval>,
    val endedAt: TimePoint? = null,
    val endReason: PeriodEndReason? = null,
) {
    fun connectedDurationMs(now: TimePoint) = intervals.sumOf { it.durationMs(now) }
}

@Serializable
data class SessionRecord(
    val id: String,
    val carId: String,
    val createdAt: TimePoint,
    val periods: List<DrivingPeriod>,
    val carName: String = "Car",
    /** Monotonic persisted activity order, independent of wall-clock changes or record creation. */
    val lastActivitySequence: Long = 0,
) {
    fun connectedDurationMs(now: TimePoint) = periods.sumOf { it.connectedDurationMs(now) }
}

@Serializable
data class DisconnectGrace(val disconnectedAt: TimePoint, val deadline: TimePoint) {
    fun remainingMs(now: TimePoint): Long =
        if (now.bootId == deadline.bootId) (deadline.elapsedRealtimeMs - now.elapsedRealtimeMs).coerceAtLeast(0) else 0
}

/** A choice token belongs to one period. A consumed/ended period cannot be reassigned again. */
@Serializable
data class HistoryChoice(val periodId: String, val previousRecordIds: List<String>)

@Serializable
data class SessionSnapshot(
    val selectedCar: SelectedCar? = null,
    val records: List<SessionRecord> = emptyList(),
    val activePeriodId: String? = null,
    val grace: DisconnectGrace? = null,
    val preparedHistoryId: String? = null,
    val pendingChoice: HistoryChoice? = null,
    val connectionEvidence: ConnectionEvidence = ConnectionEvidence.UNKNOWN,
    val lastEventAt: TimePoint? = null,
    val lastConnectionAt: TimePoint? = null,
    val nextSequence: Long = 1,
) {
    val activeRecord: SessionRecord? get() = records.firstOrNull { record -> record.periods.any { it.id == activePeriodId } }
    val activePeriod: DrivingPeriod? get() = activeRecord?.periods?.firstOrNull { it.id == activePeriodId }
    /** Persisted snapshots MUST be reconciled against fresh platform evidence before using this. */
    val monitoringAllowed: Boolean get() = activePeriod != null && grace == null && connectionEvidence == ConnectionEvidence.CONNECTED
}
