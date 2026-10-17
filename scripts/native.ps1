[CmdletBinding()]
param(
    [ValidateSet('Devices', 'Install', 'Test', 'Logs')]
    [string]$Action = 'Devices',
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'env.ps1')
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw 'Run scripts/bootstrap.ps1 first.' }

if ($Action -eq 'Devices') {
    & $adb devices -l
    if ($LASTEXITCODE -ne 0) { throw 'ADB device discovery failed.' }
    return
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    throw 'Specify -Serial from native.ps1 Devices so the intended phone/emulator is explicit.'
}
$deviceState = & $adb -s $Serial get-state
if ($LASTEXITCODE -ne 0 -or "$deviceState".Trim() -ne 'device') {
    throw 'The selected device is not connected and authorized.'
}

if ($Action -eq 'Logs') {
    & $adb -s $Serial logcat 'DrivingCompanion:V' '*:S'
    if ($LASTEXITCODE -ne 0) { throw 'Logcat failed.' }
    return
}

if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot 'android/app/build.gradle.kts'))) {
    throw 'The first functional Android app has not been implemented. No dummy APK is available to install or test.'
}
if ($Action -eq 'Install') {
    & (Join-Path $PSScriptRoot 'gradle.ps1') ':app:assembleDebug'
    if ($LASTEXITCODE -ne 0) { throw 'Debug build failed.' }
    $apk = Join-Path $ProjectRoot 'android/app/build/outputs/apk/debug/app-debug.apk'
    if (-not (Test-Path -LiteralPath $apk)) { throw 'Expected debug APK was not produced.' }
    & $adb -s $Serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
} else {
    $previousSerial = $env:ANDROID_SERIAL
    try {
        $env:ANDROID_SERIAL = $Serial
        & (Join-Path $PSScriptRoot 'gradle.ps1') ':app:connectedDebugAndroidTest'
        if ($LASTEXITCODE -ne 0) { throw 'Device tests failed.' }
    } finally {
        $env:ANDROID_SERIAL = $previousSerial
    }
}
