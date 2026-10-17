package io.github.enan511.drivingcompanion.drive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.enan511.drivingcompanion.core.drive.ConnectionEvidence
import io.github.enan511.drivingcompanion.core.drive.SelectedCar
import io.github.enan511.drivingcompanion.core.drive.SessionEngine
import io.github.enan511.drivingcompanion.core.drive.SessionSnapshot
import io.github.enan511.drivingcompanion.data.SessionStore
import io.github.enan511.drivingcompanion.ui.CarUi
import io.github.enan511.drivingcompanion.ui.DrivingUiState
import io.github.enan511.drivingcompanion.ui.SessionUi
import io.github.enan511.drivingcompanion.ui.UiConnection
import io.github.enan511.drivingcompanion.vehicle.VehicleConnection
import io.github.enan511.drivingcompanion.vehicle.VehicleEvents
import io.github.enan511.drivingcompanion.vehicle.VehicleManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Single serialized owner of platform evidence, durable state, timers and presentation. */
class DriveCoordinator(private val context: Context) : VehicleEvents {
    val vehicles = VehicleManager(context)
    private val clock = SessionClock(context)
    private val engine = SessionEngine()
    private val store = SessionStore.create(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private var snapshot = SessionSnapshot()
    private var focused = false
    private var restoring = true
    private var storageHealthy = true
    private var service: DrivingService? = null
    private var starting = false
    private var observed = ConnectionEvidence.UNKNOWN
    private var observationGeneration = 0L
    private var expiryCheckPending = false
    private var observationReady = false
    private var pairedCars = emptyList<CarUi>()
    private var bluetoothPermissionGranted = false
    private var bluetoothEnabled = false
    private var selectedCarAssociated = false
    private var dismissedWarning: String? = null
    private var problem: String? = null
    private var ticker: Job? = null
    private var startupTimeout: Job? = null
    private val mutableUi = MutableStateFlow(DrivingUiState(connection = UiConnection.RECOVERING))
    val ui: StateFlow<DrivingUiState> = mutableUi

    init {
        scope.launch {
            mutex.withLock {
                try {
                    snapshot = store.read()
                } catch (_: Exception) {
                    storageHealthy = false
                    restoring = false
                    problem = "Session history could not be loaded. Restart the app; existing data has not been erased."
                } finally {
                    loaded.complete(Unit)
                }
                refreshPlatformFacts()
                refreshPresentation()
            }
            if (storageHealthy) mutex.withLock { restoreObservation() }
        }
    }

    fun setFocused(value: Boolean) = event {
        focused = value
        refreshPresentation()
        service?.update(snapshot, focused)
        ensureTicker()
        if (value) restoreObservation()
    }

    fun refresh() = event {
        restoreObservation()
    }

    fun selectCar(car: CarUi) = event {
        val address = car.address.uppercase(Locale.ROOT)
        val previous = snapshot.selectedCar?.takeIf { it.id != address }
        observationGeneration++
        observed = ConnectionEvidence.UNKNOWN
        restoring = false
        observationReady = false
        val next = engine.selectCar(snapshot, SelectedCar(address, car.name, vehicles.associationId(address)), clock.now())
        persist(next)
        previous?.let { vehicles.forgetAssociation(it.id) }
        stopServiceIfIdle()
        restoreObservation()
    }

    fun forgetCar() = event {
        val previous = snapshot.selectedCar
        observationGeneration++
        observed = ConnectionEvidence.UNKNOWN
        observationReady = false
        restoring = false
        persist(engine.selectCar(snapshot, null, clock.now()))
        previous?.let { vehicles.forgetAssociation(it.id) }
        refreshPlatformFacts()
        refreshPresentation()
        stopServiceIfIdle()
    }

    fun associationChanged() = event {
        snapshot.selectedCar?.let { car ->
            persist(engine.selectCar(snapshot, car.copy(associationId = vehicles.associationId(car.id)), clock.now()))
        }
        restoreObservation()
    }

    override fun onCarConnection(address: String, connected: Boolean, source: String, completion: (() -> Unit)?) = event(completion) {
        if (!snapshot.selectedCar?.id.equals(address, ignoreCase = true)) return@event
        observationGeneration++ // Invalidate profile queries initiated before this exact event.
        expiryCheckPending = false
        observed = if (connected) ConnectionEvidence.CONNECTED else ConnectionEvidence.DISCONNECTED
        if (connected) activateOrStartService() else applyEvidence(observed)
    }

    override fun onVehicleRestoreRequested(completion: (() -> Unit)?) {
        val finished = AtomicBoolean()
        val finish = { if (finished.compareAndSet(false, true)) completion?.invoke(); Unit }
        event {
            try { restoreObservation(finish) }
            catch (error: Exception) { finish(); throw error }
        }
    }
    override fun onPlatformProblem(message: String) { showProblem(message) }

    fun showProblem(message: String) = event {
        problem = message
        refreshPresentation()
    }

    fun dismissMessage() = event { problem = null; refreshPresentation() }

    fun dismissWarning() = event {
        dismissedWarning = warningToken()
        refreshPresentation()
    }

    fun resumeHistory(historyId: String, expectedPeriodId: String?) = event {
        val next = if (snapshot.activePeriod == null && expectedPeriodId == null) {
            engine.prepareHistory(snapshot, historyId, clock.now())
        } else if (expectedPeriodId != null) {
            engine.chooseResume(snapshot, expectedPeriodId, historyId, clock.now())
        } else snapshot
        persist(next)
    }

    fun chooseNew(expectedPeriodId: String?) = event {
        val next = if (expectedPeriodId != null) engine.chooseNew(snapshot, expectedPeriodId, clock.now())
        else engine.prepareHistory(snapshot, null, clock.now())
        persist(next)
    }

    fun notificationChoice(periodId: String, historyId: String?, resume: Boolean, completion: () -> Unit) {
        scope.launch {
            try {
                loaded.await()
                mutex.withLock {
                    // A background action never starts a service or trusts persisted connection.
                    if (!storageHealthy || restoring || service == null || !snapshot.monitoringAllowed) return@withLock
                    val next = if (resume && historyId != null) engine.chooseResume(snapshot, periodId, historyId, clock.now())
                    else engine.chooseNew(snapshot, periodId, clock.now())
                    persist(next)
                }
            } catch (_: Exception) {
                problem = "The session choice could not be saved. Open the app to review it."
                refreshPresentation()
            } finally {
                completion()
            }
        }
    }

    fun serviceReady(activeService: DrivingService) = event {
        service = activeService
        starting = false
        startupTimeout?.cancel()
        if (!storageHealthy) {
            activeService.finishSession()
            return@event
        }
        if (observed == ConnectionEvidence.CONNECTED) applyEvidence(observed)
        else if (observed == ConnectionEvidence.DISCONNECTED) applyEvidence(observed)
        else requestConnectionSnapshot()
        // A newly created service has only its generic startup notification. Even
        // an unchanged reconciled grace snapshot must initialize its countdown/lock.
        if (!restoring) activeService.update(snapshot, focused)
        stopServiceIfIdle()
    }

    fun serviceFailed(message: String) = event {
        starting = false
        startupTimeout?.cancel()
        service = null
        observed = ConnectionEvidence.UNKNOWN
        if (restoring || snapshot.activePeriod != null) {
            val suspended = engine.reconcile(snapshot, ConnectionEvidence.UNKNOWN, clock.now())
            restoring = false
            persist(suspended)
        }
        restoring = false
        problem = message
        refreshPresentation()
    }

    fun serviceDestroyed(activeService: DrivingService) = event {
        if (service === activeService) {
            service = null
            starting = false
            if (snapshot.activePeriod != null && snapshot.grace == null) {
                val now = clock.now()
                val car = snapshot.selectedCar
                observed = ConnectionEvidence.UNKNOWN
                observationGeneration++
                val interrupted = if (car != null) engine.connectionChanged(snapshot, car.id, false, now) else snapshot
                persist(engine.reconcile(interrupted, ConnectionEvidence.UNKNOWN, now))
                problem = "The driving service stopped. Reopen the app to verify the car connection."
            }
            refreshPresentation()
        }
    }

    private suspend fun restoreObservation(completion: (() -> Unit)? = null) {
        if (!storageHealthy) {
            completion?.invoke()
            return
        }
        refreshPlatformFacts()
        val car = snapshot.selectedCar
        if (car == null) {
            restoring = false
            refreshPresentation()
            completion?.invoke()
            return
        }
        if (!vehicles.hasBluetoothPermission()) {
            observationGeneration++
            observed = ConnectionEvidence.UNKNOWN
            observationReady = false
            if (restoring || snapshot.activePeriod != null) persist(engine.reconcile(snapshot, observed, clock.now()))
            restoring = false
            service?.finishSession()
            service = null
            refreshPresentation()
            ensureTicker()
            completion?.invoke()
            return
        }
        observationReady = vehicles.observe(car.id).isSuccess
        requestConnectionSnapshot(completion = completion)
        refreshPresentation()
    }

    private fun requestConnectionSnapshot(expiring: Boolean = false, completion: (() -> Unit)? = null) {
        if (!storageHealthy) {
            completion?.invoke()
            return
        }
        val car = snapshot.selectedCar
        if (car == null) {
            completion?.invoke()
            return
        }
        val generation = ++observationGeneration
        vehicles.currentConnection(car.id) { evidence ->
            event(completion) {
                if (expiring) expiryCheckPending = false
                if (generation != observationGeneration || snapshot.selectedCar?.id != car.id) return@event
                val fresh = when (evidence) {
                    VehicleConnection.CONNECTED -> ConnectionEvidence.CONNECTED
                    VehicleConnection.DISCONNECTED -> ConnectionEvidence.DISCONNECTED
                    VehicleConnection.UNKNOWN -> ConnectionEvidence.UNKNOWN
                }
                if (fresh == ConnectionEvidence.UNKNOWN && !restoring) {
                    if (expiring) {
                        persist(engine.tick(snapshot, clock.now()))
                        problem = "Reconnection could not be verified before the deadline. Check the car connection."
                    }
                    // Inconclusive profile queries do not override a newer, known live ACL fact.
                    refreshPresentation()
                    return@event
                }
                observed = fresh
                if (fresh == ConnectionEvidence.CONNECTED) activateOrStartService()
                else applyEvidence(fresh)
            }
        }
    }

    private suspend fun activateOrStartService() {
        if (!storageHealthy) return
        if (!vehicles.hasBluetoothPermission()) {
            restoreObservation()
            return
        }
        if (service != null) {
            applyEvidence(ConnectionEvidence.CONNECTED)
            return
        }
        if (starting) return
        requestServiceStart()
    }

    private fun requestServiceStart() {
        starting = true
        refreshPresentation()
        try {
            ContextCompat.startForegroundService(context, Intent(context, DrivingService::class.java))
            startupTimeout?.cancel()
            startupTimeout = scope.launch {
                delay(10_000)
                event {
                    if (starting) {
                        serviceFailed("The driving service did not become ready. Open the app and check vehicle setup.")
                    }
                }
            }
        } catch (_: RuntimeException) {
            starting = false
            serviceFailed("Android blocked background activation. Open the app and complete car association and permissions.")
        }
    }

    private suspend fun applyEvidence(evidence: ConnectionEvidence) {
        val car = snapshot.selectedCar ?: return
        val now = clock.now()
        val next = if (restoring) engine.reconcile(snapshot, evidence, now)
        else when (evidence) {
            ConnectionEvidence.CONNECTED -> engine.connectionChanged(snapshot, car.id, true, now)
            ConnectionEvidence.DISCONNECTED -> engine.connectionChanged(snapshot, car.id, false, now)
            ConnectionEvidence.UNKNOWN -> snapshot
        }
        restoring = false
        persist(next)
        // A restored grace period should remain supervised when Android permits it.
        if (snapshot.grace != null && service == null && !starting && vehicles.hasBluetoothPermission()) {
            requestServiceStart()
        }
        stopServiceIfIdle()
        ensureTicker()
    }

    private suspend fun persist(next: SessionSnapshot) {
        if (!storageHealthy) return
        if (next == snapshot) {
            refreshPresentation()
            return
        }
        store.write(next) // Publish state only after its atomic checkpoint has been saved.
        snapshot = next
        refreshPresentation()
        service?.update(snapshot, focused)
        stopServiceIfIdle()
        ensureTicker()
    }

    private fun stopServiceIfIdle() {
        if (snapshot.activePeriod == null && !restoring && !starting) {
            service?.finishSession()
            service = null
        }
    }

    private fun ensureTicker() {
        if (ticker?.isActive == true || (!focused && snapshot.grace == null)) return
        ticker = scope.launch {
            while (focused || snapshot.grace != null) {
                delay(if (snapshot.grace == null) 1_000 else 250)
                mutex.withLock {
                    if (storageHealthy && !restoring) {
                        val grace = snapshot.grace
                        if (grace != null && grace.remainingMs(clock.now()) == 0L) {
                            if (!expiryCheckPending) {
                                expiryCheckPending = true
                                service?.update(snapshot, focused)
                                requestConnectionSnapshot(expiring = true)
                            }
                            refreshPresentation()
                        } else persist(engine.tick(snapshot, clock.now()))
                    } else refreshPresentation()
                }
            }
        }
    }

    private fun warningToken(): String? = snapshot.grace?.let { "${snapshot.activePeriodId}:${it.deadline.elapsedRealtimeMs}" }

    private fun refreshPlatformFacts() {
        bluetoothPermissionGranted = vehicles.hasBluetoothPermission()
        bluetoothEnabled = vehicles.isBluetoothEnabled()
        selectedCarAssociated = snapshot.selectedCar?.let { vehicles.isAssociated(it.id) } == true
        pairedCars = vehicles.pairedDevices().map { CarUi(it.address, it.name, vehicles.isAssociated(it.address)) }
    }

    private fun refreshPresentation() {
        val now = clock.now()
        val displayTime = if (restoring) snapshot.lastConnectionAt ?: snapshot.lastEventAt ?: now else now
        val car = snapshot.selectedCar
        val activeRecord = snapshot.activeRecord.takeUnless { restoring }
        val pending = snapshot.pendingChoice
        val seconds = snapshot.grace?.remainingMs(now)?.let { ((it + 999) / 1000).toInt() }
        val phase = when {
            restoring -> UiConnection.RECOVERING
            car == null -> UiConnection.NOT_CONFIGURED
            starting -> UiConnection.STARTING
            snapshot.grace != null && snapshot.activePeriod != null -> UiConnection.GRACE
            service != null && snapshot.monitoringAllowed -> UiConnection.ACTIVE
            else -> UiConnection.STANDBY
        }
        mutableUi.value = DrivingUiState(
            selectedCar = car?.let { CarUi(it.id, it.name, selectedCarAssociated) },
            pairedCars = pairedCars,
            connection = phase,
            remainingSeconds = seconds,
            history = snapshot.records.asReversed().map { record ->
                SessionUi(record.id, record.carName, record.createdAt.wallTimeMs,
                    record.periods.lastOrNull()?.let { it.endedAt?.wallTimeMs ?: displayTime.wallTimeMs } ?: record.createdAt.wallTimeMs,
                    record.connectedDurationMs(displayTime), record.periods.size, activeRecord?.id == record.id,
                    canResume = record.carId == car?.id)
            },
            activeSessionId = activeRecord?.id,
            pendingResumeSessionId = pending?.previousRecordIds?.lastOrNull(),
            showResumePrompt = focused && phase == UiConnection.ACTIVE && pending != null,
            showDisconnectWarning = focused && phase == UiConnection.GRACE && seconds != null && seconds > 0 && dismissedWarning != warningToken(),
            bluetoothPermissionGranted = bluetoothPermissionGranted,
            notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
            bluetoothEnabled = bluetoothEnabled,
            associationReady = car != null && observationReady,
            statusMessage = problem,
            preparedSessionId = snapshot.preparedHistoryId,
            canChooseHistory = !restoring && (snapshot.activePeriod == null || (pending != null && phase == UiConnection.ACTIVE)),
            periodToken = snapshot.activePeriodId,
        )
    }

    private fun event(completion: (() -> Unit)? = null, block: suspend () -> Unit) {
        scope.launch {
            try {
                loaded.await()
                mutex.withLock {
                    try { block() }
                    catch (_: Exception) {
                        storageHealthy = false
                        restoring = false
                        starting = false
                        problem = "Session state could not be saved. Monitoring stopped to protect your history. Restart the app."
                        service?.finishSession()
                        service = null
                        refreshPresentation()
                    }
                }
            } finally {
                completion?.invoke()
            }
        }
    }
}
