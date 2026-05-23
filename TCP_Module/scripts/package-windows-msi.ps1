param(
    [string]$AppName = "FileSecurityTransmission",
    [string]$AppVersion = "1.0.0",
    [string]$Vendor = "File Security Transmission Team",
    [switch]$SkipBuild,
    [switch]$Console
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ModuleDir = (Resolve-Path (Join-Path $ScriptDir "..")).Path
$JarName = "FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule-1.0-SNAPSHOT.jar"
$JarPath = Join-Path $ModuleDir "target\$JarName"
$OutputDir = Join-Path $ModuleDir "dist"

function Require-Command {
    param([string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found. Please install it and make sure it is on PATH."
    }
}

function Invoke-Checked {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath failed with exit code $LASTEXITCODE."
    }
}

Require-Command "java"
Require-Command "jpackage"

if (-not $SkipBuild) {
    Require-Command "mvn"
    Push-Location $ModuleDir
    try {
        Invoke-Checked "mvn" @("-q", "-DskipTests", "package")
    }
    finally {
        Pop-Location
    }
}

if (-not (Test-Path $JarPath)) {
    throw "Jar not found: $JarPath"
}

if (Test-Path $OutputDir) {
    Remove-Item $OutputDir -Recurse -Force
}
New-Item -ItemType Directory -Path $OutputDir | Out-Null

$JPackageArgs = @(
    "--type", "msi",
    "--name", $AppName,
    "--app-version", $AppVersion,
    "--vendor", $Vendor,
    "--dest", $OutputDir,
    "--input", (Join-Path $ModuleDir "target"),
    "--main-jar", $JarName,
    "--arguments", "--app.role=client --spring.profiles.active=client",
    "--java-options", "-Dfile.encoding=UTF-8",
    "--win-menu",
    "--win-shortcut",
    "--win-dir-chooser"
)

if ($Console) {
    $JPackageArgs += "--win-console"
}

Invoke-Checked "jpackage" $JPackageArgs

Write-Host ""
Write-Host "MSI package created under: $OutputDir"
