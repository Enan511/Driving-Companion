package io.github.enan511.drivingcompanion.vehicle

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build

/** Only Android may bind this service (manifest BIND_COMPANION_DEVICE_SERVICE protection). */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class CarPresenceService : CompanionDeviceService() {
    private val events: VehicleEvents?
        get() = (application as? VehicleEventHost)?.vehicleEvents

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (Build.VERSION.SDK_INT < 36) return
        val connected = when (event.event) {
            DevicePresenceEvent.EVENT_BT_CONNECTED -> true
            DevicePresenceEvent.EVENT_BT_DISCONNECTED -> false
            // BLE proximity is never evidence that the car is connected.
            else -> return
        }
        val manager = VehicleManager(this)
        val address = manager.addressForAssociation(event.associationId)
        if (address == null) {
            events?.onVehicleRestoreRequested()
            return
        }
        manager.notePlatformEvent(address)
        events?.onCarConnection(address, connected, "companion-bt")
    }

    override fun onDeviceAppeared(address: String) {
        if (Build.VERSION.SDK_INT < 36) reconcile(address)
    }

    override fun onDeviceDisappeared(address: String) {
        if (Build.VERSION.SDK_INT < 36) reconcile(address)
    }

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        if (Build.VERSION.SDK_INT in 33..35) {
            associationInfo.deviceMacAddress?.toString()?.let(::reconcile)
        }
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        if (Build.VERSION.SDK_INT in 33..35) {
            associationInfo.deviceMacAddress?.toString()?.let(::reconcile)
        }
    }

    private fun reconcile(address: String) {
        val manager = VehicleManager(this)
        manager.notePlatformEvent(address)
        manager.currentConnection(address) { connection ->
            when (connection) {
                VehicleConnection.CONNECTED -> events?.onCarConnection(address, true, "companion-profile-check")
                VehicleConnection.DISCONNECTED -> events?.onCarConnection(address, false, "companion-profile-check")
                VehicleConnection.UNKNOWN -> events?.onPlatformProblem(
                    "The car connection could not be verified. Check Bluetooth and Nearby devices access.",
                )
            }
        }
    }
}
