package io.github.enan511.drivingcompanion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF70E2CC)
private val Background = Color(0xFF0D1216)
private val Panel = Color(0xFF182126)
private val Muted = Color(0xFFB4C3C9)

/** Presentation only: callbacks and the authoritative session deadline are owned outside Compose. */
@Composable
fun DrivingCompanionApp(state: DrivingUiState, actions: DrivingUiActions) {
    var screen by rememberSaveable { mutableStateOf("home") }
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = screen != "home") { screen = "home" }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF00382D),
            background = Background,
            onBackground = Color(0xFFF0F5F6),
            surface = Panel,
            onSurface = Color(0xFFF0F5F6),
            surfaceVariant = Color(0xFF253239),
            onSurfaceVariant = Muted,
            outline = Color(0xFF71868E),
        ),
        typography = Typography(),
    ) {
        Scaffold(containerColor = Background) { insets ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(insets),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (screen == "home") {
                    item { AppHeading("Jarvis Drive", "Your car. Your session.") }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            HomeContent(
                                state, actions,
                                onChooseCar = { actions.refreshDevices(); screen = "cars" },
                                onHistory = { screen = "history" },
                                onForget = { confirmForget = true },
                            )
                        }
                    }
                } else {
                    item { TextButton(onClick = { screen = "home" }) { Text("Back to Home") } }
                    if (screen == "cars") {
                        item { AppHeading("Choose your car", "Configure this while parked.") }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                CarPicker(state, actions) { car -> actions.selectCar(car); screen = "home" }
                            }
                        }
                    } else {
                        item { AppHeading("Session history", "Saved on this phone.") }
                        if (state.history.isEmpty()) {
                            item {
                                InfoCard {
                                    Text("Your first drive starts here", style = MaterialTheme.typography.titleMedium)
                                    Text("Connect your selected car to save a session. History stays on this phone and survives app restarts.", color = Muted)
                                }
                            }
                        } else {
                            item { Text("A session can contain several connected driving periods. Time spent disconnected is excluded.", color = Muted) }
                            items(state.history.sortedByDescending { it.lastActivityAtMillis }, key = { it.id }) { session ->
                                HistorySession(session, state, actions)
                            }
                        }
                    }
                }
                state.statusMessage?.let { message ->
                    item {
                        InfoCard {
                            Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            TextButton(onClick = actions.dismissMessage) { Text("Dismiss message") }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
        if (state.showDisconnectWarning && state.connection == UiConnection.GRACE) {
            DisconnectDialog(state.remainingSeconds, actions.dismissDisconnectWarning)
        } else if (state.showResumePrompt && state.canChooseHistory) {
            ResumeDialog(state, actions)
        }
        if (confirmForget) {
            AlertDialog(
                onDismissRequest = { confirmForget = false },
                title = { Text("Forget this car?") },
                text = { Text("Automatic sessions will stop for this car. Your session history stays saved.") },
                confirmButton = {
                    TextButton(onClick = { confirmForget = false; actions.forgetCar() }) { Text("Forget car") }
                },
                dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Keep car") } },
            )
        }
    }
}

@Composable
private fun AppHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Muted)
    }
}

@Composable
private fun HomeContent(
    state: DrivingUiState,
    actions: DrivingUiActions,
    onChooseCar: () -> Unit,
    onHistory: () -> Unit,
    onForget: () -> Unit,
) {
    InfoCard {
        Text("YOUR CAR", style = MaterialTheme.typography.labelLarge, color = Accent)
        Text(state.selectedCar?.name ?: "No car selected", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium)
        ConnectionStatus(state)
        Text(connectionExplanation(state), color = Muted, style = MaterialTheme.typography.bodyLarge)
        if (state.selectedCar == null) {
            Button(onClick = onChooseCar, modifier = Modifier.fillMaxWidth().testTag("choose_car")) {
                Text("Choose car")
            }
        } else {
            // Configuration is intentionally unavailable during an active driving period.
            if (state.connection in setOf(UiConnection.STANDBY, UiConnection.NOT_CONFIGURED)) {
                OutlinedButton(onClick = onChooseCar, modifier = Modifier.fillMaxWidth()) { Text("Change car") }
                TextButton(onClick = onForget) { Text("Forget car") }
            }
        }
    }

    SetupContent(state, actions)

    val current = state.history.firstOrNull { it.id == state.activeSessionId }
    if (current != null) {
        InfoCard {
            Text("CURRENT SESSION", style = MaterialTheme.typography.labelLarge, color = Accent)
            Text(formatDate(current.startedAtMillis), style = MaterialTheme.typography.titleMedium)
            Text("${duration(current.connectedDurationMillis)} connected", style = MaterialTheme.typography.headlineSmall)
            Text(periodLabel(current.periodCount), color = Muted)
        }
    }
    state.preparedSessionId?.let { id ->
        state.history.firstOrNull { it.id == id }?.let { prepared ->
            InfoCard {
                Text("Ready to resume", style = MaterialTheme.typography.titleMedium, color = Accent)
                Text("Session from ${formatDate(prepared.startedAtMillis)} will resume on the next verified car connection.")
                TextButton(onClick = actions.chooseNewSession) { Text("Use a new session instead") }
            }
        }
    }
    OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().testTag("session_history")) {
        Text("Session history${if (state.history.isEmpty()) "" else " · ${state.history.size}"}")
    }
    Text("Sessions start automatically when your selected car connects.", color = Muted,
        style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ConnectionStatus(state: DrivingUiState) {
    val label = when (state.connection) {
        UiConnection.NOT_CONFIGURED -> "Setup needed"
        UiConnection.STANDBY -> "Waiting for car"
        UiConnection.STARTING -> "Starting session"
        UiConnection.ACTIVE -> "Connected"
        UiConnection.GRACE -> "Reconnecting${state.remainingSeconds?.let { " · ${countdown(it)}" } ?: ""}"
        UiConnection.RECOVERING -> "Checking connection"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("connection_status")) {
        Box(Modifier.size(8.dp).background(if (state.connection == UiConnection.ACTIVE) Accent else Muted, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (state.connection == UiConnection.ACTIVE) Accent else Muted,
            style = MaterialTheme.typography.labelLarge)
    }
}

private fun connectionExplanation(state: DrivingUiState): String = when (state.connection) {
    UiConnection.NOT_CONFIGURED -> "Choose your paired car and allow Bluetooth access to enable automatic sessions."
    UiConnection.STANDBY -> "Ready for your next connection. No driving session is running."
    UiConnection.STARTING -> "Confirming the connection and starting the driving service."
    UiConnection.ACTIVE -> "This driving period is being saved to your session history."
    UiConnection.GRACE -> "Your car disconnected. Reconnect before the countdown ends to continue this period."
    UiConnection.RECOVERING -> "Verifying the current car connection before restoring session operation."
}

@Composable
private fun SetupContent(state: DrivingUiState, actions: DrivingUiActions) {
    when {
        !state.bluetoothPermissionGranted -> SetupCard(
            "Allow Bluetooth access", "Nearby devices access lets Jarvis Drive find your paired car and recognize its connection.",
            "Allow nearby devices", actions.requestBluetoothPermission,
        )
        !state.bluetoothEnabled -> SetupCard(
            "Bluetooth is off", "Turn on Bluetooth so Android can connect to your car.",
            "Turn on Bluetooth", actions.enableBluetooth,
        )
        state.selectedCar != null && !state.associationReady -> SetupCard(
            "Link your car", "Android's companion-device link supports observing your selected car in the background. Confirm the car in Android's next screen.",
            "Link car with Android", actions.retryAssociation,
        )
        state.selectedCar != null && !state.notificationsGranted -> SetupCard(
            "Keep the reconnect timer visible", "Allow notifications to see the countdown and session choices when another app is open or your screen is locked.",
            "Allow notifications", actions.requestNotificationPermission,
        )
    }
}

@Composable
private fun SetupCard(title: String, description: String, label: String, action: () -> Unit) {
    InfoCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, color = Muted)
        Button(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}

@Composable
private fun CarPicker(state: DrivingUiState, actions: DrivingUiActions, select: (CarUi) -> Unit) {
    when {
        !state.bluetoothPermissionGranted || !state.bluetoothEnabled -> SetupContent(state, actions)
        else -> {
            Text("Select a car already paired in Android Bluetooth settings. Other paired devices will not start a driving session.", color = Muted)
            if (state.pairedCars.isEmpty()) {
                InfoCard {
                    Text("No paired devices found", style = MaterialTheme.typography.titleMedium)
                    Text("Pair your car in Android Bluetooth settings, then return and refresh.", color = Muted)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.pairedCars.forEach { car ->
                        Card(
                            onClick = { select(car) },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().testTag("paired_car_${car.address}"),
                            colors = CardDefaults.cardColors(containerColor = Panel),
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(car.name, style = MaterialTheme.typography.titleMedium)
                                Text("Device ending ${car.address.takeLast(5)}${if (car.associated) " · Linked" else ""}", color = Muted)
                                if (state.selectedCar?.address == car.address) Text("Selected car", color = Accent)
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = actions.refreshDevices, modifier = Modifier.fillMaxWidth()) { Text("Refresh paired devices") }
        }
    }
}

@Composable
private fun HistorySession(session: SessionUi, state: DrivingUiState, actions: DrivingUiActions) {
    InfoCard {
        Text(session.carName, style = MaterialTheme.typography.titleMedium)
        Text(formatDate(session.startedAtMillis), color = Muted)
        Text("${duration(session.connectedDurationMillis)} connected", style = MaterialTheme.typography.titleLarge)
        Text(periodLabel(session.periodCount), color = Muted)
        when {
            session.isActive -> Text("Current session", color = Accent)
            state.preparedSessionId == session.id -> Text("Prepared for next car connection", color = Accent)
            !session.canResume -> Text("Select this session's car to resume it.", color = Muted)
            else -> OutlinedButton(
                onClick = { actions.resumeSession(session.id) },
                enabled = state.canChooseHistory && state.connection in setOf(UiConnection.STANDBY, UiConnection.NOT_CONFIGURED, UiConnection.ACTIVE),
                modifier = Modifier.fillMaxWidth().testTag("resume_${session.id}"),
            ) {
                Text(when (state.connection) {
                    UiConnection.ACTIVE -> if (state.canChooseHistory) "Resume this history" else "History choice already saved"
                    UiConnection.GRACE -> "Available after this period ends"
                    UiConnection.STARTING, UiConnection.RECOVERING -> "Checking current session"
                    else -> "Use on next connection"
                })
            }
        }
    }
}

@Composable
private fun ResumeDialog(state: DrivingUiState, actions: DrivingUiActions) {
    val candidate = state.history.firstOrNull { it.id == state.pendingResumeSessionId && !it.isActive && it.canResume }
        ?: state.history.filter { !it.isActive && it.canResume }.maxByOrNull { it.lastActivityAtMillis }
    if (candidate == null) return
    AlertDialog(
        onDismissRequest = actions.chooseNewSession,
        title = { Text("Continue your previous session?") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
            ) {
                Text("Your car is connected. Choose where to save this driving period.")
                HorizontalDivider()
                Text(candidate.carName, style = MaterialTheme.typography.titleMedium)
                Text(formatDate(candidate.startedAtMillis), color = Muted)
                Text("${duration(candidate.connectedDurationMillis)} connected · ${periodLabel(candidate.periodCount)}", color = Muted)
                Text("Resuming keeps earlier history and adds this period. It does not restart the drive.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.resumeSession(candidate.id) }, modifier = Modifier.testTag("resume_previous")) { Text("Resume previous") }
        },
        dismissButton = { TextButton(onClick = actions.chooseNewSession) { Text("New session") } },
    )
}

@Composable
private fun DisconnectDialog(seconds: Int?, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Car disconnected") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()).testTag("disconnect_warning"),
            ) {
                Text("Waiting to reconnect", color = Muted)
                Text(seconds?.let(::countdown) ?: "Checking…", style = MaterialTheme.typography.displayMedium, color = Accent)
                Text("Reconnecting continues this period. Otherwise it ends when the countdown expires and stays in your history.")
                Text("You can dismiss this warning. The countdown will continue.", color = Muted,
                    style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Dismiss warning") } },
    )
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

private fun formatDate(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
private fun countdown(seconds: Int): String = String.format(Locale.getDefault(), "%02d:%02d", seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
private fun duration(millis: Long): String {
    val minutes = millis.coerceAtLeast(0) / 60_000
    return when {
        minutes == 0L -> "Less than a minute"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} hr ${minutes % 60} min"
    }
}
private fun periodLabel(count: Int): String = "$count driving ${if (count == 1) "period" else "periods"}"
