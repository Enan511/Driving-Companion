package io.github.enan511.drivingcompanion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import androidx.compose.runtime.getValue
import io.github.enan511.drivingcompanion.ui.CarUi
import io.github.enan511.drivingcompanion.ui.DrivingCompanionApp
import io.github.enan511.drivingcompanion.ui.DrivingUiActions

class MainActivity : ComponentActivity() {
    private val coordinator get() = (application as DrivingApplication).coordinator
    private var askedBluetooth = false
    private var askedNotifications = false
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) coordinator.showProblem("Nearby devices access was denied. Use Allow nearby devices again to open Android app permissions if the request is blocked.")
        coordinator.refresh()
    }
    private val notificationsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) coordinator.showProblem("Notifications are disabled. The background reconnect countdown cannot appear until notifications are allowed in Android settings.")
        coordinator.refresh()
    }
    private val enableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { coordinator.refresh() }
    private val association = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { coordinator.associationChanged() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askedBluetooth = savedInstanceState?.getBoolean("askedBluetooth") ?: false
        askedNotifications = savedInstanceState?.getBoolean("askedNotifications") ?: false
        enableEdgeToEdge()
        setContent {
            val state by coordinator.ui.collectAsStateWithLifecycle()
            val token = state.periodToken
            DrivingCompanionApp(state, DrivingUiActions(
                requestBluetoothPermission = {
                    if (askedBluetooth && !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
                        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                    } else {
                        askedBluetooth = true
                        bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
                requestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                        (!askedNotifications || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                        askedNotifications = true
                        notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
                },
                enableBluetooth = {
                    runCatching { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                        .onFailure { coordinator.showProblem("Open Android Bluetooth settings to enable Bluetooth.") }
                },
                refreshDevices = coordinator::refresh,
                selectCar = { car -> coordinator.selectCar(car); associate(car) },
                forgetCar = coordinator::forgetCar,
                retryAssociation = { state.selectedCar?.let(::associate) },
                resumeSession = { coordinator.resumeHistory(it, token) },
                chooseNewSession = { coordinator.chooseNew(token) },
                dismissDisconnectWarning = coordinator::dismissWarning,
                dismissMessage = coordinator::dismissMessage,
            ))
        }
    }

    private fun associate(car: CarUi) {
        coordinator.vehicles.requestAssociation(this, car.address,
            onIntentSender = { sender ->
                runCatching { association.launch(IntentSenderRequest.Builder(sender).build()) }
                    .onFailure { coordinator.showProblem("Android could not open the car association dialog. Try again while parked.") }
            },
            onComplete = { coordinator.associationChanged() },
            onError = coordinator::showProblem,
        )
    }

    override fun onResume() { super.onResume(); coordinator.setFocused(true) }
    override fun onPause() { coordinator.setFocused(false); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("askedBluetooth", askedBluetooth)
        outState.putBoolean("askedNotifications", askedNotifications)
        super.onSaveInstanceState(outState)
    }
}
