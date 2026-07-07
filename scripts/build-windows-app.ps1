param(
    [ValidateSet("app-image", "exe", "msi")]
    [string]$Type = "app-image",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$mvn = Join-Path $root ".tools\apache-maven-3.9.16\bin\mvn.cmd"
if (-not (Test-Path $mvn)) {
    $mvn = "mvn"
}

$jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not $env:JAVA_HOME -or -not (Test-Path $jpackage)) {
    $jpackage = "jpackage"
}

$localWix = Join-Path $root ".tools\wix314"
if (Test-Path (Join-Path $localWix "candle.exe")) {
    $env:PATH = "$localWix;$env:PATH"
}

$dist = Join-Path $root "dist\windows-app"
$input = Join-Path $root "target\jpackage-input"
$jar = "tcg-inventory-bot-0.0.1-SNAPSHOT.jar"

Push-Location $root
try {
    $mvnArgs = @("package")
    if ($SkipTests) {
        $mvnArgs += "-DskipTests"
    }
    & $mvn @mvnArgs

    if (Test-Path $dist) {
        Remove-Item $dist -Recurse -Force
    }
    if (Test-Path $input) {
        Remove-Item $input -Recurse -Force
    }
    New-Item -ItemType Directory -Path $input | Out-Null
    Copy-Item (Join-Path $root "target\$jar") (Join-Path $input $jar)

    $jpackageArgs = @(
        "--type", $Type,
        "--name", "TCG Inventory",
        "--app-version", "0.1.0",
        "--vendor", "TCG Inventory",
        "--input", $input,
        "--main-jar", $jar,
        "--dest", $dist
    )
    if ($Type -ne "app-image") {
        $jpackageArgs += @("--win-menu", "--win-shortcut")
    }

    & $jpackage @jpackageArgs
} finally {
    Pop-Location
}
