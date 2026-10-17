package io.github.enan511.drivingcompanion.vehicle

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.Locale

/** Public Android Bluetooth APIs only. No discovery loop, reflective connection API, or auto-pairing. */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class VehicleManager(context: Context) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val companion: CompanionDeviceManager?
        get() = if (supportsCompanionSetup()) {
            context.getSystemService(CompanionDeviceManager::class.java)
        } else null

    fun hasBluetoothPermission(): Boolean = context.checkSelfPermission(
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun isBluetoothEnabled(): Boolean = hasBluetoothPermission() &&
        runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<VehicleDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        return runCatching {
            adapter?.bondedDevices.orEmpty().map {
                VehicleDevice(it.address, it.name?.takeIf(String::isNotBlank) ?: "Unnamed paired device")
            }.sortedWith(compareBy<VehicleDevice> { it.name.lowercase() }.thenBy { it.address })
        }.getOrDefault(emptyList())
    }

    fun supportsCompanionSetup(): Boolean = context.packageManager.hasSystemFeature(
        PackageManager.FEATURE_COMPANION_DEVICE_SETUP,
    )

    /** Association IDs were introduced in API 33. Null does not mean unassociated on API 31–32. */
    fun associationId(address: String): Int? = if (Build.VERSION.SDK_INT >= 33) {
        runCatching {
            companion?.myAssociations?.firstOrNull {
                it.deviceMacAddress?.toString().equals(address, ignoreCase = true)
            }?.id
        }.getOrNull()
    } else null

    fun isAssociated(address: String): Boolean = if (Build.VERSION.SDK_INT >= 33) {
        associationId(address) != null
    } else {
        runCatching {
            companion?.associations?.any { it.equals(address, ignoreCase = true) } == true
        }.getOrDefault(false)
    }

    fun observe(address: String): Result<Unit> = runCatching {
        check(hasBluetoothPermission()) { "Allow Nearby devices access to observe the selected car." }
        val manager = checkNotNull(companion) { "Companion-device setup is unavailable on this phone." }
        check(isAssociated(address)) { "Associate the selected car before enabling automatic connection monitoring." }
        if (Build.VERSION.SDK_INT >= 36) {
            manager.startObservingDevicePresence(
                ObservingDevicePresenceRequest.Builder()
                    .setAssociationId(checkNotNull(associationId(address)))
                    .build(),
            )
        } else {
            manager.startObservingDevicePresence(address)
        }
    }

    fun stopObserving(address: String): Result<Unit> = runCatching {
        val manager = checkNotNull(companion) { "Companion-device setup is unavailable on this phone." }
        if (Build.VERSION.SDK_INT >= 36) {
            val id = associationId(address) ?: return@runCatching
            manager.stopObservingDevicePresence(
                ObservingDevicePresenceRequest.Builder().setAssociationId(id).build(),
            )
        } else if (isAssociated(address)) {
            manager.stopObservingDevicePresence(address)
        }
    }

    /** Call only after the selected-car change is durably saved; history is retained separately. */
    fun forgetAssociation(address: String): Result<Unit> = runCatching {
        val manager = companion ?: return@runCatching
        // Removing the association also removes its observation/background privileges. Still try
        // disassociation if stopping observation failed (for example, after permission revocation).
        stopObserving(address)
        if (Build.VERSION.SDK_INT >= 33) {
            val association = manager.myAssociations.firstOrNull {
                it.deviceMacAddress?.toString().equals(address, ignoreCase = true)
            }
            association?.let { manager.disassociate(it.id) }
        } else if (manager.associations.any { it.equals(address, ignoreCase = true) }) {
            manager.disassociate(address)
        }
        Unit
    }

    /**
     * The caller launches [onIntentSender] with an Activity result contract. On API 31–32, reconcile
     * [isAssociated] from that result; Android has no onAssociationCreated callback on those versions.
     * The exact saved address scopes the system chooser, and only already paired devices are eligible.
     */
    fun requestAssociation(
        activity: Activity,
        address: String,
        onIntentSender: (IntentSender) -> Unit,
        onComplete: (associationId: Int?) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!hasBluetoothPermission()) {
            onError("Allow Nearby devices access before selecting the car.")
            return
        }
        if (pairedDevices().none { it.address.equals(address, ignoreCase = true) }) {
            onError("Pair the car in Android Bluetooth settings before selecting it here.")
            return
        }
        val manager = companion
        if (manager == null) {
            onError("Companion-device setup is unavailable on this phone.")
            return
        }
        if (isAssociated(address)) {
            onComplete(associationId(address))
            return
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setSingleDevice(true)
            .build()
        val callback = object : CompanionDeviceManager.Callback() {
            private var finished = false

            override fun onDeviceFound(chooserLauncher: IntentSender) {
                if (!finished) onIntentSender(chooserLauncher)
            }

            override fun onAssociationPending(intentSender: IntentSender) {
                if (!finished) onIntentSender(intentSender)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                if (Build.VERSION.SDK_INT < 33) return
                if (finished) return
                finished = true
                if (associationInfo.deviceMacAddress?.toString().equals(address, ignoreCase = true)) {
                    onComplete(associationInfo.id)
                } else {
                    onError("Android returned a different device. Select the car again.")
                }
            }

            override fun onFailure(error: CharSequence?) {
                if (!finished) {
                    finished = true
                    // Do not place device identifiers or arbitrary platform text into public logs.
                    onError("Car association was cancelled, timed out, or could not be completed. Try again while parked.")
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                manager.associate(request, activity.mainExecutor, callback)
            } else {
                manager.associate(request, callback, main)
            }
        } catch (_: RuntimeException) {
            onError("Android could not open car association. Check Nearby devices access and Bluetooth settings.")
        }
    }

    /**
     * A bounded snapshot of BOTH public audio profiles, delivered once on the main thread. Either
     * connected profile is sufficient; both must be known disconnected to report DISCONNECTED.
     * A missing proxy, permission failure, transition, or timeout remains UNKNOWN. Profiles are
     * closed immediately after reading, including any proxy arriving after the timeout.
     */
    @SuppressLint("MissingPermission")
    fun currentConnection(address: String, callback: (VehicleConnection) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { currentConnection(address, callback) }
            return
        }
        if (!hasBluetoothPermission()) {
            callback(VehicleConnection.UNKNOWN)
            return
        }
        val bluetooth = adapter
        if (bluetooth == null) {
            callback(VehicleConnection.DISCONNECTED)
            return
        }
        val enabled = try {
            bluetooth.isEnabled
        } catch (_: RuntimeException) {
            // Revocation or a temporarily unavailable Bluetooth service is not a disconnect fact.
            callback(VehicleConnection.UNKNOWN)
            return
        }
        if (!enabled) {
            callback(VehicleConnection.DISCONNECTED)
            return
        }
        val device = runCatching { bluetooth.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            callback(VehicleConnection.UNKNOWN)
            return
        }

        val results = mutableMapOf<Int, VehicleConnection>()
        val profiles = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
        val key = address.uppercase(Locale.ROOT)
        val capturedRadioRevision = radioRevision
        val capturedDeviceRevision = deviceRevisions[key]
        var finished = false
        lateinit var timeout: Runnable
        fun finish(value: VehicleConnection) {
            if (finished) return
            finished = true
            main.removeCallbacks(timeout)
            callback(if (capturedRadioRevision != radioRevision || capturedDeviceRevision != deviceRevisions[key]) {
                VehicleConnection.UNKNOWN
            } else value)
        }
        fun record(profile: Int, value: VehicleConnection) {
            if (finished) return
            results[profile] = value
            if (value == VehicleConnection.CONNECTED) finish(value)
            else if (results.size == profiles.size) {
                finish(if (results.values.all { it == VehicleConnection.DISCONNECTED }) {
                    VehicleConnection.DISCONNECTED
                } else VehicleConnection.UNKNOWN)
            }
        }
        timeout = Runnable { finish(VehicleConnection.UNKNOWN) }
        main.postDelayed(timeout, PROFILE_TIMEOUT_MS)
        profiles.forEach { profileId ->
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    // Normalize vendor callbacks to one thread, and always release late proxies.
                    main.post {
                        val value = try {
                            if (finished) VehicleConnection.UNKNOWN
                            else when (proxy.getConnectionState(device)) {
                                BluetoothProfile.STATE_CONNECTED -> VehicleConnection.CONNECTED
                                BluetoothProfile.STATE_DISCONNECTED -> VehicleConnection.DISCONNECTED
                                else -> VehicleConnection.UNKNOWN
                            }
                        } catch (_: RuntimeException) {
                            VehicleConnection.UNKNOWN
                        } finally {
                            runCatching { bluetooth.closeProfileProxy(profile, proxy) }
                        }
                        record(profile, value)
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    main.post { record(profile, VehicleConnection.UNKNOWN) }
                }
            }
            val accepted = runCatching { bluetooth.getProfileProxy(context, listener, profileId) }
                .getOrDefault(false)
            if (!accepted) record(profileId, VehicleConnection.UNKNOWN)
        }
    }

    internal fun addressForAssociation(id: Int): String? = if (Build.VERSION.SDK_INT >= 33) {
        runCatching {
            companion?.myAssociations?.firstOrNull { it.id == id }?.deviceMacAddress?.toString()
        }.getOrNull()
    } else null

    /** Invalidate older async snapshots before delivering a newer platform event. Main thread only. */
    internal fun notePlatformEvent(address: String? = null) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (address == null) radioRevision++
        else {
            val key = address.uppercase(Locale.ROOT)
            deviceRevisions[key] = (deviceRevisions[key] ?: 0L) + 1L
        }
    }

    private companion object {
        const val PROFILE_TIMEOUT_MS = 4_000L
        var radioRevision = 0L
        val deviceRevisions = mutableMapOf<String, Long>()
    }
}
