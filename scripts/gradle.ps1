# Forward all arguments to the checked-in wrapper with project-local tools and caches.
$ErrorActionPreference = 'Stop'
$gradleArguments = $args
. (Join-Path $PSScriptRoot 'env.ps1')
if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'Project JDK is missing. Run scripts/bootstrap.ps1 first.'
}
& (Join-Path $ProjectRoot 'android/gradlew.bat') --project-dir (Join-Path $ProjectRoot 'android') @gradleArguments
if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
