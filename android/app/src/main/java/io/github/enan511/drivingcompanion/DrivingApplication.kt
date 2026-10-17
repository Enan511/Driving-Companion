package io.github.enan511.drivingcompanion

import android.app.Application
import io.github.enan511.drivingcompanion.drive.DriveCoordinator
import io.github.enan511.drivingcompanion.vehicle.VehicleEventHost
import io.github.enan511.drivingcompanion.vehicle.VehicleEvents

class DrivingApplication : Application(), VehicleEventHost {
    val coordinator: DriveCoordinator by lazy { DriveCoordinator(this) }
    override val vehicleEvents: VehicleEvents get() = coordinator
}
