param(
    [string]$Serial,
    [ValidateRange(5, 60)][int]$DurationSeconds = 30,
    [ValidateRange(1, 30)][int]$WarmupSeconds = 5,
    [switch]$SkipBuild,
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) { throw "adb not found at $adb" }

$deviceRows = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' }
if (-not $Serial) {
    $authorized = @($deviceRows | Where-Object { $_ -match "\sdevice$" } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($authorized.Count -ne 1) {
        throw "Pass -Serial explicitly; authorized device count is $($authorized.Count). Check the phone's USB-debugging prompt."
    }
    $Serial = $authorized[0]
}
$state = (& $adb -s $Serial get-state 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $state -ne 'device') { throw "Device $Serial is not authorized and ready: $state" }

Push-Location $repo
try {
    if (-not $SkipBuild) {
        & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
    }

    $mainApk = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
    $testApk = Join-Path $repo 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    foreach ($apk in @($mainApk, $testApk)) {
        if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "Missing APK: $apk" }
    }
    if (-not $SkipInstall) {
        & $adb -s $Serial install -r -t $mainApk
        if ($LASTEXITCODE -ne 0) { throw 'Main APK install failed; existing app data was not removed.' }
        & $adb -s $Serial install -r -t $testApk
        if ($LASTEXITCODE -ne 0) { throw 'Test APK install failed; existing app data was not removed.' }
    }

    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $resultDir = Join-Path $repo "output\phone-performance-$stamp"
    New-Item -ItemType Directory -Path $resultDir | Out-Null
    $instrumentationLog = Join-Path $resultDir 'instrumentation.txt'
    $summaryPath = Join-Path $resultDir 'summary.json'
    $stageLogPath = Join-Path $resultDir 'stage-log.txt'
    $reportPath = Join-Path $resultDir 'report.md'

    Write-Host 'The test will prompt for these camera scenes:'
    Write-Host '  1. Stay completely out of the front-camera view.'
    Write-Host '  2. Look toward the front camera for the remaining four blocks.'
    Write-Host 'No camera images, gaze coordinates, or model features are exported.'

    $instrumentArgs = @(
        '-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r',
        '-e', 'class', 'com.newsmead.research.study.ArticleFullAppPerformanceDeviceTest',
        '-e', 'newsmeadLivePerf', 'true',
        '-e', 'newsmeadPerfWarmupSeconds', $WarmupSeconds.ToString(),
        '-e', 'newsmeadPerfDurationSeconds', $DurationSeconds.ToString(),
        '-e', 'newsmeadPerfOrder', 'OFF,OFF,ON,ON,OFF',
        '-e', 'newsmeadPerfPresenceOrder', 'ABSENT,PRESENT,PRESENT,PRESENT,PRESENT',
        'com.newsmead.test/androidx.test.runner.AndroidJUnitRunner'
    )
    $instrumentation = & $adb @instrumentArgs 2>&1
    $instrumentation | Set-Content -LiteralPath $instrumentationLog -Encoding utf8
    $instrumentExit = $LASTEXITCODE

    $summaryText = (& $adb -s $Serial exec-out run-as com.newsmead cat cache/newsmead-live-performance-summary.json 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $summaryText.StartsWith('{')) {
        throw "Aggregate summary retrieval failed. Instrumentation exit=$instrumentExit. See $instrumentationLog"
    }
    $summaryText | Set-Content -LiteralPath $summaryPath -Encoding utf8
    & $adb -s $Serial logcat -d -v threadtime NewsMeadPerfTest:I MGazeNetPerf:I '*:S' 2>&1 |
        Set-Content -LiteralPath $stageLogPath -Encoding utf8

    $summary = $summaryText | ConvertFrom-Json
    $rows = foreach ($phase in $summary.phases) {
        [pscustomobject]@{
            Condition = "$($phase.expected_presence)/$($phase.recording)"
            Seconds = [double]$phase.timing.actual_timed_seconds
            CameraResults = [double]$phase.timing.camera_result_callbacks
            Coordinates = [double]$phase.timing.reason_counts.coordinate
            Fresh = [double]$phase.timing.camera_result_capture_age_at_most_250ms
            BusyDrops = [double]$phase.timing.observed_busy_drop_delta
            NoFaceRatio = [double]$phase.timing.no_face_ratio_camera_results
        }
    }
    $aggregates = foreach ($group in ($rows | Group-Object Condition)) {
        $seconds = ($group.Group | Measure-Object Seconds -Sum).Sum
        $camera = ($group.Group | Measure-Object CameraResults -Sum).Sum
        $coordinates = ($group.Group | Measure-Object Coordinates -Sum).Sum
        $fresh = ($group.Group | Measure-Object Fresh -Sum).Sum
        $drops = ($group.Group | Measure-Object BusyDrops -Sum).Sum
        [pscustomobject]@{
            Condition = $group.Name
            Blocks = $group.Count
            CameraHz = if ($seconds -gt 0) { $camera / $seconds } else { 0 }
            CoordinateHz = if ($seconds -gt 0) { $coordinates / $seconds } else { 0 }
            FreshHz = if ($seconds -gt 0) { $fresh / $seconds } else { 0 }
            BusyDrops = $drops
        }
    }
    $lines = @(
        '# Phone performance diagnostic', '',
        "- Device: $($summary.device_model) ($Serial)",
        "- Successful: $($summary.successful)",
        "- Thermal status: $($summary.thermal_status_initial) -> $($summary.thermal_status_final)",
        "- Calibration unchanged: $($summary.calibration_fingerprint_unchanged)",
        "- Isolated raw recordings deleted: $($summary.isolated_raw_recordings_deleted)", '',
        '| Condition | Blocks | Camera results/s | Coordinates/s | Results <=250ms/s | Busy drops |',
        '| --- | ---: | ---: | ---: | ---: | ---: |'
    )
    foreach ($row in $aggregates) {
        $lines += "| $($row.Condition) | $($row.Blocks) | $($row.CameraHz.ToString('F2')) | $($row.CoordinateHz.ToString('F2')) | $($row.FreshHz.ToString('F2')) | $($row.BusyDrops.ToString('F0')) |"
    }
    $lines += @('', 'These are diagnostic throughput and freshness measurements, not gaze-accuracy or study-validity evidence.',
        'Raw aggregate receipt: `summary.json`', 'Stage timing log: `stage-log.txt`')
    $lines | Set-Content -LiteralPath $reportPath -Encoding utf8

    if ($instrumentExit -ne 0 -or $summary.successful -ne $true) {
        throw "Device diagnostic failed. Evidence retained at $resultDir"
    }
    Write-Host "Completed: $reportPath"
} finally {
    Pop-Location
}
