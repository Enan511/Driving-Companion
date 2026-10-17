[CmdletBinding()]
param(
    [switch]$AcceptAndroidLicenses,
    [switch]$SkipSdkPackages
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'env.ps1')

if ($env:OS -ne 'Windows_NT' -or -not [Environment]::Is64BitOperatingSystem -or
    $env:PROCESSOR_ARCHITECTURE -notin @('AMD64', 'x86')) {
    throw 'This bootstrap supports Windows x64. See docs/development.md for other hosts.'
}

$toolsRoot = Join-Path $ProjectRoot '.tools'
$downloadRoot = Join-Path $toolsRoot 'downloads'
foreach ($directory in @($toolsRoot, $downloadRoot, $env:GRADLE_USER_HOME, $env:ANDROID_USER_HOME)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}

function Get-VerifiedArchive {
    param([string]$Url, [string]$Name, [string]$Hash, [string]$Algorithm = 'SHA256')
    $archive = Join-Path $downloadRoot $Name
    if (Test-Path -LiteralPath $archive) {
        if ((Get-FileHash -LiteralPath $archive -Algorithm $Algorithm).Hash -eq $Hash) {
            return $archive
        }
        throw "Checksum mismatch for cached $Name. Inspect the archive before retrying."
    }
    $partial = "$archive.partial"
    Write-Host "Downloading $Name"
    & curl.exe --fail --location --retry 3 --silent --show-error --output $partial $Url
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Name" }
    if ((Get-FileHash -LiteralPath $partial -Algorithm $Algorithm).Hash -ne $Hash) {
        throw "Checksum mismatch for downloaded $Name. Archive was not installed."
    }
    # Both paths are fixed children of the project-owned download directory.
    Move-Item -LiteralPath $partial -Destination $archive -Force
    return $archive
}

if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    $jdkZip = Get-VerifiedArchive $Toolchain.jdk.url ('jdk-' + $Toolchain.jdk.version + '.zip') $Toolchain.jdk.sha256
    Expand-Archive -LiteralPath $jdkZip -DestinationPath (Join-Path $toolsRoot 'jdk') -Force
}
if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    throw "Expected JDK not found at $env:JAVA_HOME"
}

$gradleHome = Join-Path $toolsRoot ('gradle/gradle-' + $Toolchain.gradle.version)
if (-not (Test-Path -LiteralPath (Join-Path $gradleHome 'bin/gradle.bat'))) {
    $gradleZip = Get-VerifiedArchive $Toolchain.gradle.url ('gradle-' + $Toolchain.gradle.version + '.zip') $Toolchain.gradle.sha256
    Expand-Archive -LiteralPath $gradleZip -DestinationPath (Join-Path $toolsRoot 'gradle') -Force
}

$commandLineRoot = Join-Path $env:ANDROID_HOME ('cmdline-tools/' + $Toolchain.android.commandLineToolsVersion)
$sdkManager = Join-Path $commandLineRoot 'bin/sdkmanager.bat'
if (-not (Test-Path -LiteralPath $sdkManager)) {
    # Google's repository metadata publishes SHA-1 for this archive; use that exact digest.
    $sdkZip = Get-VerifiedArchive $Toolchain.android.commandLineToolsUrl ('android-command-line-tools-' + $Toolchain.android.commandLineToolsVersion + '.zip') $Toolchain.android.commandLineToolsSha1 'SHA1'
    $sdkExtractRoot = Join-Path $toolsRoot ('sdk-extract-' + $Toolchain.android.commandLineToolsVersion)
    Expand-Archive -LiteralPath $sdkZip -DestinationPath $sdkExtractRoot -Force
    New-Item -ItemType Directory -Path (Split-Path $commandLineRoot -Parent) -Force | Out-Null
    $sdkSource = [System.IO.Path]::GetFullPath((Join-Path $sdkExtractRoot 'cmdline-tools'))
    $sdkTarget = [System.IO.Path]::GetFullPath($commandLineRoot)
    $allowedPrefix = [System.IO.Path]::GetFullPath($toolsRoot) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $sdkSource.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        -not $sdkTarget.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'SDK move escaped the project tool directory.'
    }
    Move-Item -LiteralPath $sdkSource -Destination $sdkTarget
}

$sdkPath = $env:ANDROID_HOME.Replace('\', '/')
# Java properties use Unicode escapes; ASCII replacement would corrupt non-English paths.
$escapedSdkPath = -join ($sdkPath.ToCharArray() | ForEach-Object {
    if ([int]$_ -gt 126 -or [int]$_ -lt 32) { '\u{0:x4}' -f [int]$_ } else { [string]$_ }
})
Set-Content -LiteralPath (Join-Path $ProjectRoot 'android/local.properties') -Value "sdk.dir=$escapedSdkPath" -Encoding ascii

if (-not $SkipSdkPackages) {
    if ($AcceptAndroidLicenses) {
        Write-Host 'Accepting Android SDK package licenses for this local installation.'
        $licenseLog = Join-Path $ProjectRoot '.local/sdk-licenses.log'
        # Windows PowerShell 5.1 turns redirected native stderr warnings into error
        # records. Preserve those in the log and judge the command by its exit code.
        $savedErrorPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            1..100 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$env:ANDROID_HOME" --licenses *> $licenseLog
            $licenseExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $savedErrorPreference
        }
        if ($licenseExitCode -ne 0) { throw "SDK license command failed. See $licenseLog" }
    }
    & $sdkManager "--sdk_root=$env:ANDROID_HOME" @($Toolchain.android.packages)
    if ($LASTEXITCODE -ne 0) {
        throw 'SDK package installation failed. Run sdkmanager --licenses or bootstrap -AcceptAndroidLicenses, then retry.'
    }
    foreach ($requiredSdkFile in @('platform-tools/adb.exe', 'platforms/android-36/android.jar', 'build-tools/35.0.0/aapt2.exe')) {
        if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME $requiredSdkFile))) {
            throw "SDK manager did not install $requiredSdkFile, even if it returned success."
        }
    }
}

& (Join-Path $env:JAVA_HOME 'bin/java.exe') -version
if ($LASTEXITCODE -ne 0) { throw 'Java verification failed.' }
Write-Host 'Project-local tools are ready. Run scripts/doctor.ps1 for verification.'
