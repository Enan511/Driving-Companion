# Dot-source this file to use the project's isolated Windows toolchain.
$ProjectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$Toolchain = Get-Content -LiteralPath (Join-Path $ProjectRoot 'tools/toolchain.json') -Raw | ConvertFrom-Json
$env:JAVA_HOME = Join-Path $ProjectRoot ('.tools/jdk/' + $Toolchain.jdk.directory)
$env:ANDROID_HOME = Join-Path $ProjectRoot '.tools/android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $ProjectRoot '.local/gradle'
$env:ANDROID_USER_HOME = Join-Path $ProjectRoot '.local/android'
$env:ANDROID_AVD_HOME = Join-Path $ProjectRoot '.local/android/avd'
$env:ANDROID_EMULATOR_HOME = Join-Path $ProjectRoot '.local/android'
$env:Path = @(
    (Join-Path $env:JAVA_HOME 'bin')
    (Join-Path $env:ANDROID_HOME 'platform-tools')
    (Join-Path $env:ANDROID_HOME ('cmdline-tools/' + $Toolchain.android.commandLineToolsVersion + '/bin'))
    $env:Path
) -join [System.IO.Path]::PathSeparator
