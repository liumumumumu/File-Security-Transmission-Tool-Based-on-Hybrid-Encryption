param(
    [string]$BuildDir = "cross_platform\build\windows-release",
    [string]$DistDir = "dist\crypto-service-windows-x64",
    [string]$Config = "Release"
)

$ErrorActionPreference = "Stop"

if (-not $env:VCPKG_ROOT) {
    throw "VCPKG_ROOT is not set. Example: `$env:VCPKG_ROOT='C:\vcpkg'"
}

$toolchain = Join-Path $env:VCPKG_ROOT "scripts\buildsystems\vcpkg.cmake"
$vcpkgExe = Join-Path $env:VCPKG_ROOT "vcpkg.exe"

if (-not (Test-Path $toolchain)) {
    throw "vcpkg toolchain file not found: $toolchain"
}

if (-not (Test-Path $vcpkgExe)) {
    throw "vcpkg.exe not found: $vcpkgExe"
}

& $vcpkgExe install openssl:x64-windows nlohmann-json:x64-windows

cmake -S cross_platform -B $BuildDir `
    -G "Visual Studio 17 2022" `
    -A x64 `
    -DCMAKE_TOOLCHAIN_FILE="$toolchain"

cmake --build $BuildDir --config $Config

Remove-Item -Recurse -Force $DistDir -ErrorAction SilentlyContinue
Remove-Item -Force "dist\crypto-service-windows-x64.zip" -ErrorAction SilentlyContinue

New-Item -ItemType Directory -Force $DistDir | Out-Null
New-Item -ItemType Directory -Force "$DistDir\crypto_keys" | Out-Null

Copy-Item "$BuildDir\$Config\crypto-service.exe" $DistDir
Copy-Item "$env:VCPKG_ROOT\installed\x64-windows\bin\libssl-3-x64.dll" $DistDir
Copy-Item "$env:VCPKG_ROOT\installed\x64-windows\bin\libcrypto-3-x64.dll" $DistDir

Compress-Archive -Path $DistDir -DestinationPath "dist\crypto-service-windows-x64.zip" -Force

Write-Host "Windows package created: dist\crypto-service-windows-x64.zip"
