param(
    [string]$BuildDir = "cross_platform\build\windows-release",
    [string]$DistName = "crypto-service-windows-x64",
    [string]$Config = "Release",
    [string]$Triplet = "x64-windows",
    [string]$Generator = ""
)

$ErrorActionPreference = "Stop"

$rootDir = Resolve-Path (Join-Path $PSScriptRoot "..")
$buildPath = Join-Path $rootDir $BuildDir
$distRoot = Join-Path $rootDir "dist"
$distDir = Join-Path $distRoot $DistName
$archive = Join-Path $distRoot "$DistName.zip"

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

& $vcpkgExe install "openssl:$Triplet" "nlohmann-json:$Triplet" "cpp-httplib:$Triplet"

if (-not $Generator) {
    $cmakeHelp = cmake --help | Out-String
    if ($cmakeHelp -match "Visual Studio 18 2026") {
        $Generator = "Visual Studio 18 2026"
    }
    else {
        $Generator = "Visual Studio 17 2022"
    }
}

Write-Host "Using CMake generator: $Generator"

cmake -S (Join-Path $rootDir "cross_platform") -B $buildPath `
    -G "$Generator" `
    -A x64 `
    -DCMAKE_TOOLCHAIN_FILE="$toolchain" `
    -DVCPKG_TARGET_TRIPLET="$Triplet"

cmake --build $buildPath --config $Config

Remove-Item -Recurse -Force $distDir -ErrorAction SilentlyContinue
Remove-Item -Force $archive -ErrorAction SilentlyContinue

New-Item -ItemType Directory -Force $distDir | Out-Null
New-Item -ItemType Directory -Force (Join-Path $distDir "crypto_keys") | Out-Null

Copy-Item (Join-Path $buildPath "$Config\crypto-service.exe") $distDir
Copy-Item (Join-Path $buildPath "$Config\*.dll") $distDir
Copy-Item (Join-Path $rootDir "deploy\runtime\start.bat") $distDir
Copy-Item (Join-Path $rootDir "deploy\runtime\install-windows-service.ps1") $distDir
Copy-Item (Join-Path $rootDir "deploy\README.md") $distDir
Copy-Item (Join-Path $env:VCPKG_ROOT "installed\$Triplet\bin\libssl-3-x64.dll") $distDir
Copy-Item (Join-Path $env:VCPKG_ROOT "installed\$Triplet\bin\libcrypto-3-x64.dll") $distDir

Compress-Archive -Path $distDir -DestinationPath $archive -Force

Write-Host "Windows package created: $archive"
