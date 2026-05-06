param(
    [string]$ServiceName = "CryptoService",
    [string]$InstallDir = "$env:ProgramFiles\CryptoService",
    [string]$HostName = "0.0.0.0",
    [int]$Port = 9080
)

$ErrorActionPreference = "Stop"

$sourceDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$dataDir = Join-Path $env:ProgramData "CryptoService"
$keyDir = Join-Path $dataDir "crypto_keys"

New-Item -ItemType Directory -Force $InstallDir | Out-Null
New-Item -ItemType Directory -Force $keyDir | Out-Null

Copy-Item -Path (Join-Path $sourceDir "*") -Destination $InstallDir -Recurse -Force

$exePath = Join-Path $InstallDir "crypto-service.exe"
if (-not (Test-Path $exePath)) {
    throw "crypto-service.exe not found in $InstallDir"
}

$existing = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($existing) {
    sc.exe stop $ServiceName | Out-Null
    sc.exe delete $ServiceName | Out-Null
    Start-Sleep -Seconds 2
}

$binPath = "`"$exePath`" --host $HostName --port $Port --key-dir `"$keyDir`""
sc.exe create $ServiceName binPath= $binPath start= auto DisplayName= "Crypto Service" | Out-Null
sc.exe start $ServiceName | Out-Null

Get-Service -Name $ServiceName
