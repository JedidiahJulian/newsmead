# Pure Kotlin/JUnit check using existing Gradle cache. No Android SDK or downloads.
param(
    [string]$Java = 'java',
    [string]$GradleCache = (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1')
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$outDir = Join-Path $repoRoot 'output/study-writer-v1-checks'
$null = New-Item -ItemType Directory -Path $outDir -Force
$testTemp = Join-Path $outDir 'tmp'
$null = New-Item -ItemType Directory -Path $testTemp -Force
function CachedJar([string]$module, [string]$version, [string]$artifact) {
    $directory = Join-Path $GradleCache "$module/$version"
    $found = Get-ChildItem -LiteralPath $directory -Recurse -Filter $artifact -File |
        Select-Object -First 1
    if ($null -eq $found) { throw "Missing cached $artifact. This check does not download dependencies." }
    return $found.FullName
}
$compiler = CachedJar 'org.jetbrains.kotlin/kotlin-compiler-embeddable' '1.9.0' 'kotlin-compiler-embeddable-1.9.0.jar'
$stdlib = CachedJar 'org.jetbrains.kotlin/kotlin-stdlib' '1.9.0' 'kotlin-stdlib-1.9.0.jar'
$reflect = CachedJar 'org.jetbrains.kotlin/kotlin-reflect' '1.6.10' 'kotlin-reflect-1.6.10.jar'
$runtime = CachedJar 'org.jetbrains.kotlin/kotlin-script-runtime' '1.9.0' 'kotlin-script-runtime-1.9.0.jar'
$trove = CachedJar 'org.jetbrains.intellij.deps/trove4j' '1.0.20200330' 'trove4j-1.0.20200330.jar'
$annotations = CachedJar 'org.jetbrains/annotations' '13.0' 'annotations-13.0.jar'
$junit = CachedJar 'junit/junit' '4.13.2' 'junit-4.13.2.jar'
$hamcrest = CachedJar 'org.hamcrest/hamcrest-core' '1.3' 'hamcrest-core-1.3.jar'
$json = CachedJar 'org.json/json' '20231013' 'json-20231013.jar'
$separator = [IO.Path]::PathSeparator
$compilerPath = @($compiler, $stdlib, $reflect, $runtime, $trove, $annotations) -join $separator
$testPath = @($stdlib, $annotations, $junit, $hamcrest, $json) -join $separator
$classes = Join-Path $outDir 'study-writer-tests.jar'
$sources = @(
    (Join-Path $repoRoot 'app/src/main/java/com/newsmead/research/study/StudyPackageWriter.kt'),
    (Join-Path $repoRoot 'app/src/main/java/com/newsmead/research/study/StudyChunkIO.kt'),
    (Join-Path $repoRoot 'app/src/main/java/com/newsmead/research/study/StudyRecordingCatalog.kt'),
    (Join-Path $repoRoot 'app/src/test/java/com/newsmead/research/study/StudyRecordingCatalogTest.kt'),
    (Join-Path $repoRoot 'app/src/test/java/com/newsmead/research/study/StudyChunkIOTest.kt'),
    (Join-Path $repoRoot 'app/src/test/java/com/newsmead/research/study/StudyPackageWriterTest.kt')
)
Push-Location $repoRoot
try {
    & $Java -cp $compilerPath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 1.8 -classpath $testPath -d $classes @sources
    if ($LASTEXITCODE -ne 0) { throw "Kotlin compiler failed ($LASTEXITCODE)." }
    & $Java "-Djava.io.tmpdir=$testTemp" "-Dstudy.writer.interop=$(Join-Path $outDir 'interop')" -cp (@($classes, $testPath) -join $separator) org.junit.runner.JUnitCore com.newsmead.research.study.StudyPackageWriterTest com.newsmead.research.study.StudyRecordingCatalogTest com.newsmead.research.study.StudyChunkIOTest
    if ($LASTEXITCODE -ne 0) { throw "Study writer JUnit failed ($LASTEXITCODE)." }
} finally {
    Pop-Location
}
