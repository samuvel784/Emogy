
# ============================================
# EMOGY - AUTOMATED ANDROID TEST RUNNER
# Windows 11 / Android Studio / PowerShell
# ============================================

$ErrorActionPreference = "Continue"

$Project = Get-Location
$AppId = "com.vurka.emogy"
$Report = Join-Path $Project "Emogy_Test_Report"

New-Item -ItemType Directory -Force -Path $Report | Out-Null

$StartTime = Get-Date
$LogFile = Join-Path $Report "emogy-logcat.txt"
$TestFile = Join-Path $Report "test-results.txt"
$BuildFile = Join-Path $Report "build-results.txt"

Write-Host "`n===== EMOGY TESTING STARTED =====" -ForegroundColor Cyan

# 1. Check ADB
Write-Host "`n[1] Checking ADB..."

$adb = Get-Command adb -ErrorAction SilentlyContinue

if (-not $adb) {
    Write-Host "ADB not found. Install Android SDK Platform Tools and configure PATH." -ForegroundColor Red
    exit 1
}

adb devices -l | Out-File "$Report\devices.txt"

$devices = @(adb devices | Select-String "device$")

if ($devices.Count -eq 0) {
    Write-Host "No connected Android device detected." -ForegroundColor Red
    exit 1
}

# 2. Clean old build outputs
Write-Host "`n[2] Cleaning project..."

.\gradlew.bat clean 2>&1 | Out-File $BuildFile

# 3. Build debug APK
Write-Host "`n[3] Building debug APK..."

.\gradlew.bat assembleDebug 2>&1 |
    Tee-Object -FilePath $BuildFile -Append

$buildExit = $LASTEXITCODE

if ($buildExit -ne 0) {
    Write-Host "BUILD FAILED. Check build-results.txt" -ForegroundColor Red
    exit 1
}

# 4. Run unit tests
Write-Host "`n[4] Running unit tests..."

.\gradlew.bat testDebugUnitTest 2>&1 |
    Tee-Object -FilePath $TestFile

$unitExit = $LASTEXITCODE

# 5. Run instrumentation tests
Write-Host "`n[5] Running instrumentation tests..."

.\gradlew.bat connectedDebugAndroidTest 2>&1 |
    Tee-Object -FilePath $TestFile -Append

$instrumentExit = $LASTEXITCODE

# 6. Locate APK
Write-Host "`n[6] Installing application..."

$apk = Get-ChildItem "$Project\app\build\outputs\apk\debug" `
    -Filter "*.apk" -ErrorAction SilentlyContinue |
    Select-Object -First 1

if ($apk) {
    adb install -r $apk.FullName
} else {
    Write-Host "APK not found." -ForegroundColor Red
}

# 7. Clear previous Logcat
Write-Host "`n[7] Starting fresh Logcat..."

adb logcat -c

# 8. Launch application
Write-Host "`n[8] Launching Emogy..."

adb shell monkey -p $AppId 1

# 9. Collect logs for 5 minutes
Write-Host "`n[9] Collecting logs for 5 minutes..."
Write-Host "Use the device normally during this period."
Write-Host "Test gameplay, navigation, audio and background behaviour."

$logProcess = Start-Process -FilePath "adb" `
    -ArgumentList "logcat -v threadtime" `
    -RedirectStandardOutput $LogFile `
    -NoNewWindow -PassThru

Start-Sleep -Seconds 300

if (-not $logProcess.HasExited) {
    Stop-Process -Id $logProcess.Id -Force
}

# 10. Extract important log entries
Write-Host "`n[10] Analysing logs..."

$patterns = @(
    "FATAL EXCEPTION",
    "AndroidRuntime",
    "ANR in",
    "Fatal signal",
    "SIGSEGV",
    "OutOfMemoryError",
    "mediaplayer went away",
    "doFrame",
    "Skipped .* frames",
    "Exception",
    "emogy"
)

Select-String -Path $LogFile -Pattern $patterns |
    ForEach-Object { $_.Line } |
    Set-Content "$Report\important-log-entries.txt"

# 11. Save summary
$EndTime = Get-Date

@"
EMOGY AUTOMATED TEST SUMMARY

Start: $StartTime
End: $EndTime

Build exit code: $buildExit
Unit test exit code: $unitExit
Instrumentation test exit code: $instrumentExit

Full Logcat: emogy-logcat.txt
Important Entries: important-log-entries.txt
Build Results: build-results.txt
Test Results: test-results.txt

NOTE:
A successful Gradle build does not prove that
all application functionality works correctly.

Manual gameplay verification is still required.
"@ | Set-Content "$Report\SUMMARY.txt"

Write-Host "`n===== TESTING FINISHED =====" -ForegroundColor Green
Write-Host "Reports saved in: $Report"
