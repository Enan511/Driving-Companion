package io.github.enan511.drivingcompanion.vehicle

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

/** Protected system Bluetooth broadcasts complement companion wake-up; they do not replace it. */
class CarConnectionReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission") // Checked through VehicleManager before reading the device.
    override fun onReceive(context: Context, intent: Intent) {
        val events = (context.applicationContext as? VehicleEventHost)?.vehicleEvents ?: return
        val manager = VehicleManager(context)
        if (!manager.hasBluetoothPermission()) {
            events.onPlatformProblem("Nearby devices access is required to monitor the selected car.")
            return
        }
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            manager.notePlatformEvent()
            val completion = goAsync().finishOnce()
            try {
                events.onVehicleRestoreRequested(completion)
            } catch (_: RuntimeException) {
                completion()
                events.onPlatformProblem("Vehicle restoration could not be requested. Open the app to check setup.")
            }
            return
        }
        val device = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        } ?: return
        val address = device.address
        manager.notePlatformEvent(address)
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            -> {
                val completion = goAsync().finishOnce()
                try {
                    events.onCarConnection(
                        address,
                        intent.action == BluetoothDevice.ACTION_ACL_CONNECTED,
                        "bluetooth-acl",
                        completion,
                    )
                } catch (_: RuntimeException) {
                    completion()
                    events.onPlatformProblem("The Bluetooth event could not be processed. Open the app to check the car connection.")
                }
            }
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            -> {
                // One profile disconnecting does not mean the other profile has disconnected.
                val completion = goAsync().finishOnce()
                try {
                    manager.currentConnection(address) { connection ->
                        try {
                            when (connection) {
                                VehicleConnection.CONNECTED -> events.onCarConnection(address, true, "bluetooth-profiles", completion)
                                VehicleConnection.DISCONNECTED -> events.onCarConnection(address, false, "bluetooth-profiles", completion)
                                VehicleConnection.UNKNOWN -> {
                                    completion()
                                    events.onPlatformProblem(
                                        "Bluetooth profile state is temporarily unavailable. Connection is unverified.",
                                    )
                                }
                            }
                        } catch (_: RuntimeException) {
                            completion()
                            events.onPlatformProblem("The Bluetooth event could not be processed. Open the app to check the car connection.")
                        }
                    }
                } catch (_: RuntimeException) {
                    completion()
                    events.onPlatformProblem("Bluetooth profile state could not be queried. Open the app to check the car connection.")
                }
            }
        }
    }
}

/** Re-register observation and reconcile fresh Bluetooth evidence; never restore a remembered drive blindly. */
class VehicleRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val events = (context.applicationContext as? VehicleEventHost)?.vehicleEvents ?: return
            val completion = goAsync().finishOnce()
            try {
                events.onVehicleRestoreRequested(completion)
            } catch (_: RuntimeException) {
                completion()
                events.onPlatformProblem("Vehicle restoration could not be requested. Open the app to check setup.")
            }
        }
    }
}

/** Ownership passes to the serialized host; failure paths may safely race its completion. */
private fun BroadcastReceiver.PendingResult.finishOnce(): () -> Unit {
    val completed = AtomicBoolean(false)
    return { if (completed.compareAndSet(false, true)) finish() }
}
