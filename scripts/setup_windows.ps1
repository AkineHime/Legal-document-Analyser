<#
.SYNOPSIS
    One-shot dev-machine setup for Statigate on Windows: installs JDK 21 and Maven if either is
    missing, builds every module, and checks whether the InLegalBERT model files are in place.

.DESCRIPTION
    Safe to re-run - each step is skipped if already satisfied. This script only installs the
    Java/Maven *tooling*; it does not fetch or build the InLegalBERT model itself (that's either
    `scripts\export_inlegalbert_onnx.py`, which needs Python, or copying `model.int8.onnx` and
    `vocab.txt` from someone who already has them - see the warning this script prints if they're
    missing).

.USAGE
    From the repository root, in PowerShell:
        powershell -ExecutionPolicy Bypass -File scripts\setup_windows.ps1

    If it installs JDK/Maven for the first time, close and reopen your terminal afterwards so the
    updated PATH takes effect, then run it again to build.
#>

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Test-CommandAvailable([string]$name) {
    return $null -ne (Get-Command $name -ErrorAction SilentlyContinue)
}

function Update-SessionPath {
    $machine = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
    $user = [System.Environment]::GetEnvironmentVariable("Path", "User")
    $env:Path = "$machine;$user"
}

Write-Host "== Statigate setup ==" -ForegroundColor Cyan
Write-Host "Repository root: $repoRoot"

# ---- JDK 21 -----------------------------------------------------------------

$hasJdk21 = $false
if (Test-CommandAvailable "java") {
    $versionText = (& java -version 2>&1) -join "`n"
    if ($versionText -match '"21\.') {
        $hasJdk21 = $true
    }
}

if ($hasJdk21) {
    Write-Host "[ok] JDK 21 already installed." -ForegroundColor Green
} else {
    Write-Host "[..] Installing Temurin JDK 21 (Eclipse Adoptium)..." -ForegroundColor Cyan
    $jdkInstaller = Join-Path $env:TEMP "temurin21.msi"
    $jdkUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
    Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkInstaller
    # /qn = silent, ADDLOCAL enables "set JAVA_HOME" and "add to PATH" features of the Temurin MSI.
    Start-Process msiexec.exe -ArgumentList @(
        "/i", "`"$jdkInstaller`"", "/qn", "/norestart",
        "ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome"
    ) -Wait
    Remove-Item $jdkInstaller -Force -ErrorAction SilentlyContinue
    Update-SessionPath
    if (Test-CommandAvailable "java") {
        Write-Host "[ok] JDK 21 installed." -ForegroundColor Green
    } else {
        Write-Warning "JDK 21 was installed, but this terminal doesn't see it on PATH yet. Close this window, open a new PowerShell, and re-run this script."
        exit 1
    }
}

# ---- Maven --------------------------------------------------------------------

if (Test-CommandAvailable "mvn") {
    Write-Host "[ok] Maven already installed." -ForegroundColor Green
} else {
    Write-Host "[..] Installing Apache Maven..." -ForegroundColor Cyan
    $mvnVersion = "3.9.9"
    $mvnZip = Join-Path $env:TEMP "maven.zip"
    $mvnUrl = "https://dlcdn.apache.org/maven/maven-3/$mvnVersion/binaries/apache-maven-$mvnVersion-bin.zip"
    $installDir = "C:\tools"
    Invoke-WebRequest -Uri $mvnUrl -OutFile $mvnZip
    New-Item -ItemType Directory -Force -Path $installDir | Out-Null
    Expand-Archive -Path $mvnZip -DestinationPath $installDir -Force
    Remove-Item $mvnZip -Force -ErrorAction SilentlyContinue

    $mvnBin = Join-Path $installDir "apache-maven-$mvnVersion\bin"
    $currentUserPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
    if ($currentUserPath -notlike "*$mvnBin*") {
        [System.Environment]::SetEnvironmentVariable("Path", "$currentUserPath;$mvnBin", "User")
    }
    $env:Path += ";$mvnBin"

    if (Test-CommandAvailable "mvn") {
        Write-Host "[ok] Maven installed to $mvnBin." -ForegroundColor Green
    } else {
        Write-Warning "Maven was installed to $mvnBin, but this terminal doesn't see it on PATH yet. Close this window, open a new PowerShell, and re-run this script."
        exit 1
    }
}

# ---- Build ----------------------------------------------------------------------

Write-Host "[..] Building all modules (mvn install -DskipTests)..." -ForegroundColor Cyan
mvn install -DskipTests
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed - see the output above."
}
Write-Host "[ok] Build succeeded." -ForegroundColor Green

# ---- Model check ------------------------------------------------------------------

$modelOnnx = Join-Path $repoRoot "models\inlegalbert\model.int8.onnx"
$modelVocab = Join-Path $repoRoot "models\inlegalbert\vocab.txt"

if ((Test-Path $modelOnnx) -and (Test-Path $modelVocab)) {
    Write-Host "[ok] InLegalBERT model found - the full clause classifier will be used." -ForegroundColor Green
} else {
    Write-Warning @"
InLegalBERT model files not found under models\inlegalbert\.
Statigate still works without them (keyword + BM25 only, no semantic classification) - but for the
same behavior as the reference build, get these two files from a teammate who has already exported
them and place them at:
    $modelOnnx
    $modelVocab
(Alternatively, run scripts\export_inlegalbert_onnx.py yourself - it needs Python plus torch,
transformers and onnxruntime, and downloads ~450MB from Hugging Face.)
"@
}

Write-Host ""
Write-Host "== Setup complete ==" -ForegroundColor Cyan
Write-Host "To launch the desktop app:"
Write-Host "    cd desktop; mvn javafx:run" -ForegroundColor Yellow
Write-Host "To run the command-line tool instead:"
Write-Host "    java --enable-native-access=ALL-UNNAMED -jar app\target\statigate.jar samples\sample_service_agreement.pdf" -ForegroundColor Yellow
