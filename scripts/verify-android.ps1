param([string]$DeviceId = '')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    New-Item -ItemType Directory -Path 'test-results' -Force | Out-Null
    & '.\gradlew.bat' ':app:assembleDebug' ':app:testDebugUnitTest' ':app:assembleDebugAndroidTest' '--console=plain' 2>&1 |
        Tee-Object -FilePath 'test-results\android-build.log'
    if ($LASTEXITCODE -ne 0) { throw 'Android build or JVM tests failed.' }
    if (-not $DeviceId) { Write-Output 'Build complete. Supply -DeviceId to install and run instrumented tests on one selected device.'; return }
    $taskAdb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }
    & $taskAdb -s $DeviceId get-state
    if ($LASTEXITCODE -ne 0) { throw 'Selected device is unavailable.' }
    & $taskAdb -s $DeviceId install -r 'app\build\outputs\apk\debug\app-debug.apk'
    if ($LASTEXITCODE -ne 0) { throw 'App install failed.' }
    & $taskAdb -s $DeviceId install -r -t 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if ($LASTEXITCODE -ne 0) { throw 'Test APK install failed.' }
    $taskTestOutput = & $taskAdb -s $DeviceId shell am instrument -w -r 'com.wanglemai.app.test/androidx.test.runner.AndroidJUnitRunner'
    $taskTestOutput | Tee-Object -FilePath 'test-results\android-instrumentation.log'
    $taskTestText = $taskTestOutput -join "`n"
    if ($taskTestText -notmatch 'OK \(\d+ tests?\)' -or $taskTestText -match 'FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED') {
        throw 'Device tests failed; adb exit code alone is not a test result. See test-results/android-instrumentation.log.'
    }
} finally { Pop-Location }
