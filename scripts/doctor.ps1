[CmdletBinding()]
param([switch]$Devices)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'env.ps1')
$failures = @()

function Test-RequiredFile {
    param([string]$Label, [string]$Path)
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        Write-Host "OK: $Label"
    } else {
        Write-Host "MISSING: $Label ($Path)"
        $script:failures += $Label
    }
}

Test-RequiredFile 'JDK runtime' (Join-Path $env:JAVA_HOME 'bin/java.exe')
Test-RequiredFile 'JDK compiler' (Join-Path $env:JAVA_HOME 'bin/javac.exe')
Test-RequiredFile 'SDK manager' (Join-Path $env:ANDROID_HOME ('cmdline-tools/' + $Toolchain.android.commandLineToolsVersion + '/bin/sdkmanager.bat'))
Test-RequiredFile 'Android platform 36' (Join-Path $env:ANDROID_HOME 'platforms/android-36/android.jar')
Test-RequiredFile 'Android build tools' (Join-Path $env:ANDROID_HOME 'build-tools/35.0.0/aapt2.exe')
Test-RequiredFile 'ADB' (Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe')
Test-RequiredFile 'Gradle wrapper' (Join-Path $ProjectRoot 'android/gradle/wrapper/gradle-wrapper.jar')

$wrapperProperties = Join-Path $ProjectRoot 'android/gradle/wrapper/gradle-wrapper.properties'
if (Test-Path -LiteralPath $wrapperProperties) {
    $wrapperValues = @{}
    Get-Content -LiteralPath $wrapperProperties | Where-Object { $_ -match '^\w+=' } | ForEach-Object {
        $parts = $_ -split '=', 2
        $wrapperValues[$parts[0]] = $parts[1]
    }
    if ($wrapperValues['distributionSha256Sum'] -ne $Toolchain.gradle.sha256) {
        $failures += 'Wrapper distribution SHA-256 does not match tools/toolchain.json'
    }
    $expectedUrl = 'https\://services.gradle.org/distributions/gradle-' + $Toolchain.gradle.version + '-bin.zip'
    if ($wrapperValues['distributionUrl'] -ne $expectedUrl) { $failures += 'Wrapper URL/version does not match toolchain manifest' }
} else {
    $failures += 'Wrapper properties are missing'
}
$wrapperJar = Join-Path $ProjectRoot 'android/gradle/wrapper/gradle-wrapper.jar'
if ((Test-Path -LiteralPath $wrapperJar) -and
    (Get-FileHash -LiteralPath $wrapperJar -Algorithm SHA256).Hash -ne $Toolchain.gradle.wrapperSha256) {
    $failures += 'Wrapper JAR differs from Gradle published SHA-256'
}

Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' | ForEach-Object {
    $parseTokens = $null
    $parseErrors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($_.FullName, [ref]$parseTokens, [ref]$parseErrors) | Out-Null
    if ($parseErrors.Count -gt 0) { $script:failures += "PowerShell syntax: $($_.Name): $parseErrors" }
}

if ($failures.Count -gt 0) { throw ($failures -join [Environment]::NewLine) }
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -version
if ($LASTEXITCODE -ne 0) { throw 'Java did not run successfully.' }
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') -version
if ($LASTEXITCODE -ne 0) { throw 'Java compiler did not run successfully.' }
& (Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe') version
if ($LASTEXITCODE -ne 0) { throw 'ADB did not run successfully.' }

if ($Devices) {
    & (Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe') devices -l
    if ($LASTEXITCODE -ne 0) { throw 'ADB device discovery failed.' }
    Write-Host 'An empty list means no device is connected. Unauthorized requires confirmation on the unlocked phone.'
}
Write-Host 'Environment checks passed. This does not establish that any application feature is implemented or tested.'
