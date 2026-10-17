package io.github.enan511.drivingcompanion

import io.github.enan511.drivingcompanion.core.drive.SelectedCar
import io.github.enan511.drivingcompanion.core.drive.SessionEngine
import io.github.enan511.drivingcompanion.core.drive.SessionSnapshot
import io.github.enan511.drivingcompanion.core.drive.TimePoint

/** Synthetic identities only; tests must never carry a real vehicle identifier or driving history. */
internal object SessionFixtures {
    val engine = SessionEngine()
    val car = SelectedCar("test-selected-car", "Car · \"commuter\"", 7)
    fun time(elapsed: Long) = TimePoint(1_000_000L + elapsed, elapsed, "test-boot")

    fun returningDrive(): SessionSnapshot {
        var snapshot = engine.selectCar(SessionSnapshot(), car, time(0))
        snapshot = engine.connectionChanged(snapshot, car.id, true, time(1_000))
        snapshot = engine.connectionChanged(snapshot, car.id, false, time(2_000))
        snapshot = engine.tick(snapshot, time(22_000))
        return engine.connectionChanged(snapshot, car.id, true, time(30_000))
    }

    fun returningDriveInGrace(): SessionSnapshot = engine.connectionChanged(
        returningDrive(), car.id, false, time(32_000),
    )
}
