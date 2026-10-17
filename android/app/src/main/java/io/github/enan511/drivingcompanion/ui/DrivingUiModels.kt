package io.github.enan511.drivingcompanion.ui

data class CarUi(val address: String, val name: String, val associated: Boolean = false)

data class SessionUi(
    val id: String,
    val carName: String,
    val startedAtMillis: Long,
    val lastActivityAtMillis: Long,
    val connectedDurationMillis: Long,
    val periodCount: Int,
    val isActive: Boolean,
    val canResume: Boolean = true,
)

enum class UiConnection { NOT_CONFIGURED, STANDBY, STARTING, ACTIVE, GRACE, RECOVERING }

data class DrivingUiState(
    val selectedCar: CarUi? = null,
    val pairedCars: List<CarUi> = emptyList(),
    val connection: UiConnection = UiConnection.NOT_CONFIGURED,
    val remainingSeconds: Int? = null,
    val history: List<SessionUi> = emptyList(),
    val activeSessionId: String? = null,
    val pendingResumeSessionId: String? = null,
    val showResumePrompt: Boolean = false,
    val showDisconnectWarning: Boolean = false,
    val bluetoothPermissionGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val associationReady: Boolean = false,
    val statusMessage: String? = null,
    val preparedSessionId: String? = null,
    val canChooseHistory: Boolean = false,
    val periodToken: String? = null,
)

data class DrivingUiActions(
    val requestBluetoothPermission: () -> Unit,
    val requestNotificationPermission: () -> Unit,
    val enableBluetooth: () -> Unit,
    val refreshDevices: () -> Unit,
    val selectCar: (CarUi) -> Unit,
    val forgetCar: () -> Unit,
    val retryAssociation: () -> Unit,
    val resumeSession: (String) -> Unit,
    val chooseNewSession: () -> Unit,
    val dismissDisconnectWarning: () -> Unit,
    val dismissMessage: () -> Unit,
)
