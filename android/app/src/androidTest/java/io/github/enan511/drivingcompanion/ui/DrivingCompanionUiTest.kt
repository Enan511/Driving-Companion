package io.github.enan511.drivingcompanion.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DrivingCompanionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun setupHasRealCarSelectionAndNoManualStart() {
        compose.setContent { DrivingCompanionApp(DrivingUiState(), actions()) }
        compose.onNodeWithTag("choose_car").assertIsDisplayed().performClick()
        compose.onNodeWithText("Allow nearby devices").assertIsDisplayed()
        compose.onNodeWithText("Start Drive", ignoreCase = true).assertDoesNotExist()
    }

    @Test fun disconnectDialogFollowsVisibilityWithoutOwningDeadline() {
        val state = mutableStateOf(readyState().copy(
            connection = UiConnection.GRACE,
            remainingSeconds = 12,
            showDisconnectWarning = false,
        ))
        compose.setContent { DrivingCompanionApp(state.value, actions()) }
        compose.onNodeWithTag("disconnect_warning").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(showDisconnectWarning = true) }
        compose.onNodeWithText("Car disconnected").assertIsDisplayed()
        compose.onNodeWithText("00:12").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(showDisconnectWarning = false, remainingSeconds = 9) }
        compose.onNodeWithTag("disconnect_warning").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(showDisconnectWarning = true) }
        compose.onNodeWithText("00:09").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(connection = UiConnection.STANDBY, remainingSeconds = null) }
        compose.onNodeWithTag("disconnect_warning").assertDoesNotExist()
    }

    @Test fun warningDismissalOnlyCallsDismissAction() {
        var dismissed = 0
        var newSessions = 0
        val state = mutableStateOf(readyState().copy(
            connection = UiConnection.GRACE, remainingSeconds = 15, showDisconnectWarning = true,
        ))
        compose.setContent {
            DrivingCompanionApp(state.value, actions().copy(
                dismissDisconnectWarning = { dismissed++; state.value = state.value.copy(showDisconnectWarning = false) },
                chooseNewSession = { newSessions++ },
            ))
        }
        compose.onNodeWithText("Dismiss warning").performClick()
        compose.runOnIdle {
            assertEquals(1, dismissed)
            assertEquals(0, newSessions)
            assertEquals(UiConnection.GRACE, state.value.connection)
            assertEquals(15, state.value.remainingSeconds)
        }
        compose.onNodeWithTag("disconnect_warning").assertDoesNotExist()
    }

    @Test fun resumeAndNewUseTheirSeparateCallbacks() {
        var resumedId: String? = null
        var newSessions = 0
        val state = mutableStateOf(readyState().copy(
            connection = UiConnection.ACTIVE,
            history = listOf(previous()),
            showResumePrompt = true,
            canChooseHistory = true,
            pendingResumeSessionId = "previous",
        ))
        compose.setContent {
            DrivingCompanionApp(state.value, actions().copy(
                resumeSession = { resumedId = it; state.value = state.value.copy(showResumePrompt = false) },
                chooseNewSession = { newSessions++; state.value = state.value.copy(showResumePrompt = false) },
            ))
        }
        compose.onNodeWithTag("resume_previous").performClick()
        compose.runOnIdle {
            assertEquals("previous", resumedId)
            assertEquals(0, newSessions)
            state.value = state.value.copy(showResumePrompt = true)
        }
        compose.onNodeWithText("New session").performClick()
        compose.runOnIdle { assertEquals(1, newSessions) }
    }

    @Test fun historyPreparationDoesNotRequireManualDriveStart() {
        var resumedId: String? = null
        compose.setContent {
            DrivingCompanionApp(
                readyState().copy(history = listOf(previous()), canChooseHistory = true),
                actions().copy(resumeSession = { resumedId = it }),
            )
        }
        compose.onNodeWithTag("session_history").performScrollTo().performClick()
        compose.onNodeWithTag("resume_previous").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("previous", resumedId) }
        compose.onNodeWithText("Start Drive", ignoreCase = true).assertDoesNotExist()
    }

    @Test fun consumedHistoryChoiceCannotReassignPeriodAgain() {
        var resumedId: String? = null
        compose.setContent {
            DrivingCompanionApp(
                readyState().copy(connection = UiConnection.ACTIVE, history = listOf(previous()), canChooseHistory = false),
                actions().copy(resumeSession = { resumedId = it }),
            )
        }
        compose.onNodeWithTag("session_history").performScrollTo().performClick()
        compose.onNodeWithTag("resume_previous").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(null, resumedId) }
    }

    private fun readyState() = DrivingUiState(
        selectedCar = CarUi("00:00:00:00:00:01", "Test car", associated = true),
        connection = UiConnection.STANDBY,
        bluetoothPermissionGranted = true,
        bluetoothEnabled = true,
        notificationsGranted = true,
        associationReady = true,
    )

    private fun previous() = SessionUi(
        id = "previous", carName = "Test car", startedAtMillis = 1_000,
        lastActivityAtMillis = 61_000, connectedDurationMillis = 60_000, periodCount = 1, isActive = false,
    )

    private fun actions() = DrivingUiActions(
        requestBluetoothPermission = {}, requestNotificationPermission = {}, enableBluetooth = {},
        refreshDevices = {}, selectCar = {}, forgetCar = {}, retryAssociation = {},
        resumeSession = {}, chooseNewSession = {}, dismissDisconnectWarning = {}, dismissMessage = {},
    )
}
