package io.github.enan511.drivingcompanion.vehicle

/** Android adapters report facts here; the serialized session owner makes all decisions. */
interface VehicleEvents {
    fun onCarConnection(
        address: String,
        connected: Boolean,
        source: String,
        completion: (() -> Unit)? = null,
    )
    fun onVehicleRestoreRequested(completion: (() -> Unit)? = null)
    fun onPlatformProblem(message: String)
}

interface VehicleEventHost {
    val vehicleEvents: VehicleEvents
}

data class VehicleDevice(val address: String, val name: String)

/** UNKNOWN must never be interpreted as either a confirmed connection or disconnection. */
enum class VehicleConnection { CONNECTED, DISCONNECTED, UNKNOWN }
