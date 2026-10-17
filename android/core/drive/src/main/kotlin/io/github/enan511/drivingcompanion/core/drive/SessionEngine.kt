package io.github.enan511.drivingcompanion.core.drive

/**
 * Stateless reducer. The Android owner serializes events and persists each returned snapshot.
 * Times must come from one boot-aware clock. Never use saved connection evidence as a live query.
 */
class SessionEngine(val graceDurationMs: Long = 20_000) {
    init { require(graceDurationMs > 0) }

    fun selectCar(state: SessionSnapshot, car: SelectedCar?, now: TimePoint): SessionSnapshot {
        if (isStale(state, now)) return state
        val current = if (differentBoot(state, now)) reconcile(state, ConnectionEvidence.UNKNOWN, now) else expire(state, now)
        if (current.selectedCar?.id == car?.id) return current.copy(selectedCar = car, lastEventAt = now)
        return closePeriod(current, now, PeriodEndReason.CAR_CHANGED).copy(
            selectedCar = car, connectionEvidence = ConnectionEvidence.UNKNOWN,
            preparedHistoryId = null, lastEventAt = now, lastConnectionAt = null,
        )
    }

    /** Only call after validating a real selected-device connection, not mere radio proximity. */
    fun connectionChanged(state: SessionSnapshot, carId: String, connected: Boolean, now: TimePoint): SessionSnapshot {
        if (state.selectedCar?.id != carId || isStale(state, now)) return state
        if (state.lastEventAt?.bootId?.let { it != now.bootId } == true) {
            return reconcile(state, if (connected) ConnectionEvidence.CONNECTED else ConnectionEvidence.DISCONNECTED, now)
        }
        var next = expire(state, now)
        next = if (connected) connect(next, now) else disconnect(next, now)
        return next.copy(lastEventAt = now, lastConnectionAt = now)
    }

    /** Reconcile once on every process restore, with fresh evidence (UNKNOWN when unavailable). */
    fun reconcile(state: SessionSnapshot, evidence: ConnectionEvidence, now: TimePoint): SessionSnapshot {
        if (isStale(state, now)) return state
        var next = state.copy(connectionEvidence = ConnectionEvidence.UNKNOWN)
        val oldBoot = state.lastEventAt?.bootId?.let { it != now.bootId } == true
        if (oldBoot) {
            // A monotonic deadline from another boot is meaningless. Close at the last observation,
            // never at the new boot's elapsed time, and never charge the unknown gap as driving.
            next = closePeriod(next, lastKnownTime(state, now), PeriodEndReason.BOOT_CHANGED)
        } else if (next.activePeriod != null && next.grace == null) {
            val observed = lastKnownTime(state, now)
            next = stopOpenInterval(next, observed).copy(grace = DisconnectGrace(observed, observed.plus(graceDurationMs)))
        }
        next = expire(next, now)
        next = when (evidence) {
            ConnectionEvidence.CONNECTED -> connect(next, now)
            ConnectionEvidence.DISCONNECTED -> disconnect(next, now)
            ConnectionEvidence.UNKNOWN -> next.copy(connectionEvidence = ConnectionEvidence.UNKNOWN)
        }
        return next.copy(lastEventAt = now, lastConnectionAt = if (evidence != ConnectionEvidence.UNKNOWN) now else state.lastConnectionAt)
    }

    /** Rechecking a timer cannot extend it. Visibility is deliberately absent from this API. */
    fun tick(state: SessionSnapshot, now: TimePoint): SessionSnapshot {
        if (isStale(state, now)) return state
        if (state.lastEventAt?.bootId?.let { it != now.bootId } == true) return reconcile(state, ConnectionEvidence.UNKNOWN, now)
        return expire(state, now)
    }

    fun chooseResume(state: SessionSnapshot, expectedPeriodId: String, historyId: String, now: TimePoint): SessionSnapshot {
        if (isStale(state, now)) return state
        if (differentBoot(state, now)) return reconcile(state, ConnectionEvidence.UNKNOWN, now)
        val next = expire(state, now)
        val choice = next.pendingChoice ?: return next
        if (!next.monitoringAllowed || choice.periodId != expectedPeriodId || next.activePeriodId != expectedPeriodId || historyId !in choice.previousRecordIds) return next
        val current = next.activeRecord ?: return next
        val period = next.activePeriod ?: return next
        val target = next.records.firstOrNull { it.id == historyId && it.carId == next.selectedCar?.id } ?: return next
        if (target.id == current.id) return next
        val records = next.records.mapNotNull { record ->
            when (record.id) {
                target.id -> record.copy(periods = record.periods + period, lastActivitySequence = next.nextSequence - 1)
                current.id -> record.copy(periods = record.periods.filterNot { it.id == expectedPeriodId }).takeIf { it.periods.isNotEmpty() }
                else -> record
            }
        }
        return next.copy(records = records, pendingChoice = null, lastEventAt = now)
    }

    fun chooseNew(state: SessionSnapshot, expectedPeriodId: String, now: TimePoint): SessionSnapshot {
        if (isStale(state, now)) return state
        if (differentBoot(state, now)) return reconcile(state, ConnectionEvidence.UNKNOWN, now)
        val next = expire(state, now)
        return if (next.pendingChoice?.periodId == expectedPeriodId && next.activePeriodId == expectedPeriodId) {
            next.copy(pendingChoice = null, lastEventAt = now)
        } else next
    }

    /** A disconnected choice prepares grouping only; it never creates a driving period. */
    fun prepareHistory(state: SessionSnapshot, historyId: String?, now: TimePoint): SessionSnapshot {
        if (isStale(state, now)) return state
        if (differentBoot(state, now)) return reconcile(state, ConnectionEvidence.UNKNOWN, now)
        val next = expire(state, now)
        if (next.activePeriod != null) return next
        if (historyId != null && next.records.none { it.id == historyId && it.carId == next.selectedCar?.id }) return next
        return next.copy(preparedHistoryId = historyId, lastEventAt = now)
    }

    private fun connect(state: SessionSnapshot, now: TimePoint): SessionSnapshot {
        if (state.selectedCar == null) return state.copy(connectionEvidence = ConnectionEvidence.UNKNOWN)
        if (state.activePeriod != null) {
            val next = if (state.grace != null) changeActive(state) { period ->
                period.copy(intervals = period.intervals + ConnectedInterval(now))
            } else state
            return next.copy(grace = null, connectionEvidence = ConnectionEvidence.CONNECTED)
        }
        val carId = state.selectedCar.id
        val prior = state.records.filter { it.carId == carId }.sortedBy(::activitySequence)
        val prepared = prior.firstOrNull { it.id == state.preparedHistoryId }
        val period = DrivingPeriod("period-${state.nextSequence}", now, listOf(ConnectedInterval(now)))
        val recordId = prepared?.id ?: "session-${state.nextSequence}"
        val records = if (prepared != null) state.records.map {
            if (it.id == recordId) it.copy(periods = it.periods + period, lastActivitySequence = state.nextSequence) else it
        } else state.records + SessionRecord(recordId, carId, now, listOf(period), state.selectedCar.name, state.nextSequence)
        return state.copy(
            records = records, activePeriodId = period.id, grace = null,
            preparedHistoryId = null,
            pendingChoice = if (prepared == null && prior.isNotEmpty()) HistoryChoice(period.id, prior.map { it.id }) else null,
            connectionEvidence = ConnectionEvidence.CONNECTED, nextSequence = state.nextSequence + 1,
        )
    }

    private fun disconnect(state: SessionSnapshot, now: TimePoint): SessionSnapshot {
        val next = if (state.activePeriod != null && state.grace == null) {
            stopOpenInterval(state, now).copy(grace = DisconnectGrace(now, now.plus(graceDurationMs)))
        } else state
        return next.copy(connectionEvidence = ConnectionEvidence.DISCONNECTED)
    }

    private fun expire(state: SessionSnapshot, now: TimePoint): SessionSnapshot {
        val grace = state.grace ?: return state
        return if (grace.remainingMs(now) == 0L) {
            closePeriod(state, grace.deadline, PeriodEndReason.DISCONNECT_TIMEOUT).copy(connectionEvidence = ConnectionEvidence.DISCONNECTED, lastEventAt = now)
        } else state
    }

    private fun stopOpenInterval(state: SessionSnapshot, at: TimePoint): SessionSnapshot = changeActive(state) { period ->
        period.copy(intervals = period.intervals.map { if (it.endedAt == null) it.copy(endedAt = at) else it })
    }

    private fun closePeriod(state: SessionSnapshot, at: TimePoint, reason: PeriodEndReason): SessionSnapshot {
        val stopped = stopOpenInterval(state, at)
        return changeActive(stopped) { it.copy(endedAt = at, endReason = reason) }
            .copy(activePeriodId = null, grace = null, pendingChoice = null)
    }

    private fun changeActive(state: SessionSnapshot, transform: (DrivingPeriod) -> DrivingPeriod): SessionSnapshot = state.copy(
        records = state.records.map { record -> record.copy(periods = record.periods.map { period ->
            if (period.id == state.activePeriodId) transform(period) else period
        }) },
    )

    private fun lastKnownTime(state: SessionSnapshot, fallback: TimePoint): TimePoint =
        state.lastConnectionAt ?: state.activePeriod?.intervals?.lastOrNull()?.startedAt ?: state.lastEventAt ?: fallback

    private fun isStale(state: SessionSnapshot, now: TimePoint): Boolean =
        state.lastEventAt?.let { it.bootId == now.bootId && now.elapsedRealtimeMs < it.elapsedRealtimeMs } == true

    private fun differentBoot(state: SessionSnapshot, now: TimePoint): Boolean =
        state.lastEventAt?.bootId?.let { it != now.bootId } == true

    /** Older JSON checkpoints predate the explicit field but already have deterministic period IDs. */
    private fun activitySequence(record: SessionRecord): Long = maxOf(
        record.lastActivitySequence,
        record.periods.maxOfOrNull { it.id.removePrefix("period-").toLongOrNull() ?: 0 } ?: 0,
    )
}
