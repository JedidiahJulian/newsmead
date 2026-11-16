param(
    [string]$Serial,
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
    if ($authorized.Count -ne 1) { throw "Pass -Serial explicitly; authorized device count is $($authorized.Count)." }
    $Serial = $authorized[0]
}
$state = (& $adb -s $Serial get-state 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $state -ne 'device') { throw "Device $Serial is not authorized and ready: $state" }

function Get-Percentile([double[]]$Values, [double]$Fraction) {
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { return [double]::NaN }
    $index = [Math]::Ceiling($Fraction * $sorted.Count) - 1
    return [double]$sorted[[Math]::Max(0, [Math]::Min($index, $sorted.Count - 1))]
}

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
        if ($LASTEXITCODE -ne 0) { throw 'Main APK install failed; app data was not removed.' }
        & $adb -s $Serial install -r -t $testApk
        if ($LASTEXITCODE -ne 0) { throw 'Test APK install failed; app data was not removed.' }
    }

    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $resultDir = Join-Path $repo "output\thread-count-performance-$stamp"
    New-Item -ItemType Directory -Path $resultDir | Out-Null
    $order = @(2, 3, 4, 4, 3, 2)
    $runs = @()
    for ($index = 0; $index -lt $order.Count; $index++) {
        $threads = $order[$index]
        $runNumber = $index + 1
        Write-Host "Run $runNumber/$($order.Count): fresh CPU$threads process"
        & $adb -s $Serial shell am force-stop com.newsmead
        $instrumentation = & $adb -s $Serial shell am instrument -w -r `
            -e class com.newsmead.gaze.mgazenet.EstimatorSingleThreadPerformanceDeviceTest `
            -e newsmeadSingleThreadPerf true `
            -e newsmeadSingleThreadPerfThreads $threads `
            com.newsmead.test/androidx.test.runner.AndroidJUnitRunner 2>&1
        $instrumentExit = $LASTEXITCODE
        $logPath = Join-Path $resultDir ("run-{0:D2}-cpu{1}-instrumentation.txt" -f $runNumber, $threads)
        $instrumentation | Set-Content -LiteralPath $logPath -Encoding utf8
        $receiptText = (& $adb -s $Serial exec-out run-as com.newsmead cat "cache/newsmead-single-thread-performance-$threads.json" 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or -not $receiptText.StartsWith('{')) {
            throw "Run $runNumber CPU$threads receipt retrieval failed (instrumentation exit $instrumentExit). Evidence: $resultDir"
        }
        $receiptPath = Join-Path $resultDir ("run-{0:D2}-cpu{1}.json" -f $runNumber, $threads)
        $receiptText | Set-Content -LiteralPath $receiptPath -Encoding utf8
        $receipt = $receiptText | ConvertFrom-Json
        if ($instrumentExit -ne 0 -or $receipt.measurement_completed -ne $true) {
            throw "Run $runNumber CPU$threads failed. Evidence: $resultDir"
        }
        $runs += $receipt
    }

    $identities = @($runs | ForEach-Object { "$($_.pid):$($_.process_start_ticks)" })
    if (@($identities | Select-Object -Unique).Count -ne $runs.Count) { throw "Fresh-process verification failed. Evidence: $resultDir" }
    foreach ($run in $runs) {
        if ([int]$run.execution.requested_session_threads -ne [int]$run.requested_threads -or
            [int]$run.execution.initial_process_pool_thread_count -ne [int]$run.requested_threads -or
            [int]$run.execution.thread_count -ne [int]$run.requested_threads) {
            throw "Worker-pool verification failed for CPU$($run.requested_threads). Evidence: $resultDir"
        }
    }

    $stats = foreach ($threadGroup in ($runs | Group-Object requested_threads | Sort-Object Name)) {
        [double[]]$wall = @($threadGroup.Group | ForEach-Object { $_.samples | ForEach-Object { [double]$_.wall_ms } })
        [double[]]$preprocessing = @($threadGroup.Group | ForEach-Object { $_.preprocessing_samples_ms | ForEach-Object { [double]$_ } })
        [double[]]$combined = @($threadGroup.Group | ForEach-Object { $_.combined_preprocessing_inference_samples_ms | ForEach-Object { [double]$_ } })
        [pscustomobject]@{
            Threads = [int]$threadGroup.Name
            Runs = $threadGroup.Count
            Samples = $wall.Count
            MeanMs = ($wall | Measure-Object -Average).Average
            MedianMs = Get-Percentile $wall 0.50
            P95Ms = Get-Percentile $wall 0.95
            MinMs = ($wall | Measure-Object -Minimum).Minimum
            MaxMs = ($wall | Measure-Object -Maximum).Maximum
            PreprocessMedianMs = Get-Percentile $preprocessing 0.50
            CombinedMedianMs = Get-Percentile $combined 0.50
            CombinedP95Ms = Get-Percentile $combined 0.95
            SustainableCombinedHz = 1000.0 / (Get-Percentile $combined 0.50)
        }
    }

    $best = @($stats | Sort-Object CombinedMedianMs)[0]

    $baseline = @($runs | Where-Object { [int]$_.requested_threads -eq 2 })[0]
    $parity = foreach ($run in $runs) {
        $max = 0.0
        $sum = 0.0
        $count = 0
        for ($fixture = 0; $fixture -lt $baseline.outputs.Count; $fixture++) {
            for ($feature = 0; $feature -lt $baseline.outputs[$fixture].values.Count; $feature++) {
                $delta = [Math]::Abs([double]$baseline.outputs[$fixture].values[$feature] - [double]$run.outputs[$fixture].values[$feature])
                $max = [Math]::Max($max, $delta)
                $sum += $delta
                $count++
            }
        }
        [pscustomobject]@{ Threads = [int]$run.requested_threads; MaxAbs = $max; MeanAbs = if ($count) { $sum / $count } else { 0 } }
    }

    $aggregate = [ordered]@{
        schema = 'newsmead_thread_count_aggregate_v1'
        device_serial = $Serial
        device_model = $runs[0].device_model
        model_sha256 = $runs[0].model_sha256
        order = $order
        fresh_process_identities = $identities
        statistics = $stats
        numerical_comparisons_to_first_cpu2 = $parity
        recommended_camera_free_candidate = [ordered]@{
            threads = $best.Threads
            combined_median_ms = $best.CombinedMedianMs
            combined_p95_ms = $best.CombinedP95Ms
            maximum_serial_preprocess_inference_hz = $best.SustainableCombinedHz
            suggested_latest_frame_inference_cap_hz = [Math]::Max(1, [Math]::Floor(800.0 / $best.CombinedP95Ms))
            reason = 'Leaves about 20% service-time margin at P95; end-to-end live validation remains required.'
        }
        interpretation = 'Performance diagnostic only. Do not promote a default from this result without end-to-end confirmation.'
    }
    $aggregate | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $resultDir 'aggregate.json') -Encoding utf8

    $lines = @(
        '# Fresh-process CPU thread benchmark', '',
        "- Device: $($runs[0].device_model) ($Serial)",
        "- Model SHA-256: $($runs[0].model_sha256)",
        "- Run order: $($order -join ', ')",
        '- Each configuration was started in a distinct process and its native worker count matched the request.', '',
        '| Threads | Runs | Samples | Mean ms | Median ms | P95 ms | Min ms | Max ms |',
        '| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |'
    )
    foreach ($row in $stats) {
        $lines += "| $($row.Threads) | $($row.Runs) | $($row.Samples) | $($row.MeanMs.ToString('F2')) | $($row.MedianMs.ToString('F2')) | $($row.P95Ms.ToString('F2')) | $($row.MinMs.ToString('F2')) | $($row.MaxMs.ToString('F2')) |"
    }
    $lines += @('',
        '| Threads | 640x480 preprocessing median ms | Preprocess + inference median ms | Combined P95 ms | Maximum serial Hz |',
        '| ---: | ---: | ---: | ---: | ---: |')
    foreach ($row in $stats) {
        $lines += "| $($row.Threads) | $($row.PreprocessMedianMs.ToString('F2')) | $($row.CombinedMedianMs.ToString('F2')) | $($row.CombinedP95Ms.ToString('F2')) | $($row.SustainableCombinedHz.ToString('F2')) |"
    }
    $cap = [Math]::Max(1, [Math]::Floor(800.0 / $best.CombinedP95Ms))
    $lines += @('', "Recommended camera-free candidate: CPU$($best.Threads), latest-frame inference capped at about $cap Hz to retain roughly 20% P95 service-time margin.",
        '', 'Numerical differences are reported in `aggregate.json`. This synthetic test does not measure face detection, camera acquisition/copy, UI, end-to-end FPS, gaze accuracy, or human validity.')
    $reportPath = Join-Path $resultDir 'report.md'
    $lines | Set-Content -LiteralPath $reportPath -Encoding utf8
    Write-Host "Completed: $reportPath"
} finally {
    Pop-Location
}
