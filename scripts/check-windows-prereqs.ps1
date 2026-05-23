param(
    [switch]$IncludeOptional
)

$ErrorActionPreference = "Stop"

function Test-CommandVersion {
    param(
        [string]$Name,
        [string]$Command,
        [string]$Recommended,
        [switch]$Optional
    )

    $result = [ordered]@{
        Name = $Name
        Recommended = $Recommended
        Optional = [bool]$Optional
        Installed = $false
        Detail = ""
    }

    try {
        $output = @(Invoke-Expression $Command 2>&1 | Select-Object -First 3)
        if ($LASTEXITCODE -eq 0 -or $output.Count -gt 0) {
            $result.Installed = $true
            $result.Detail = ($output -join " ").Trim()
        }
    }
    catch {
        $result.Detail = $_.Exception.Message
    }

    if (-not $result.Installed -and [string]::IsNullOrWhiteSpace($result.Detail)) {
        $result.Detail = "Command not found"
    }

    [pscustomobject]$result
}

function Test-MSVCCompiler {
    $result = [ordered]@{
        Name = "MSVC cl"
        Recommended = "VS 2022 C++ Build Tools"
        Optional = $false
        Installed = $false
        Detail = ""
    }

    try {
        $command = Get-Command cl -ErrorAction SilentlyContinue
        if ($command) {
            $output = & $command.Source 2>&1 | Select-Object -First 3
            $result.Installed = $true
            $result.Detail = ($output -join " ").Trim()
            return [pscustomobject]$result
        }

        $vswhere = "C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe"
        if (-not (Test-Path $vswhere)) {
            $result.Detail = "vswhere.exe not found"
            return [pscustomobject]$result
        }

        $installationPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
        if (-not $installationPath) {
            $result.Detail = "Visual Studio C++ workload not found"
            return [pscustomobject]$result
        }

        $vsDevCmd = Join-Path $installationPath "Common7\Tools\VsDevCmd.bat"
        if (-not (Test-Path $vsDevCmd)) {
            $result.Detail = "VsDevCmd.bat not found"
            return [pscustomobject]$result
        }

        $cmdOutput = @(& cmd /c """$vsDevCmd"" -arch=x64 >nul && cl" 2>&1 | Select-Object -First 3)
        if ($LASTEXITCODE -eq 0 -or $cmdOutput.Count -gt 0) {
            $result.Installed = $true
            $result.Detail = "Available through VsDevCmd.bat"
        }
    }
    catch {
        $result.Detail = $_.Exception.Message
    }

    if (-not $result.Installed -and [string]::IsNullOrWhiteSpace($result.Detail)) {
        $result.Detail = "MSVC compiler not found"
    }

    [pscustomobject]$result
}

$checks = @(
    @{ Name = "Java"; Command = "java -version"; Recommended = "JDK 21+"; Optional = $false },
    @{ Name = "jpackage"; Command = "jpackage --version"; Recommended = "Bundled with JDK 21+"; Optional = $false },
    @{ Name = "Node.js"; Command = "node -v"; Recommended = "22+"; Optional = $false },
    @{ Name = "npm"; Command = "npm -v"; Recommended = "10+"; Optional = $false },
    @{ Name = "Maven"; Command = "mvn -version"; Recommended = "3.9+"; Optional = $false },
    @{ Name = "CMake"; Command = "cmake --version"; Recommended = "3.28+"; Optional = $false },
    @{ Name = "WiX"; Command = "wix --version"; Recommended = "WiX Toolset"; Optional = $false },
    @{ Name = "vcpkg"; Command = "vcpkg version"; Recommended = "Latest"; Optional = $false }
)

if ($IncludeOptional) {
    $checks += @(
        @{ Name = "Git"; Command = "git --version"; Recommended = "Latest"; Optional = $true },
        @{ Name = "7-Zip"; Command = "7z"; Recommended = "Latest"; Optional = $true }
    )
}

$results = foreach ($check in $checks) {
    Test-CommandVersion @check
}

$results += Test-MSVCCompiler

$results | Format-Table Name, Installed, Recommended, Optional, Detail -AutoSize

$missingRequired = $results | Where-Object { -not $_.Optional -and -not $_.Installed }

if ($missingRequired.Count -gt 0) {
    Write-Host ""
    Write-Host "Missing required Windows packaging tools:" -ForegroundColor Yellow
    $missingRequired | ForEach-Object { Write-Host " - $($_.Name) ($($_.Recommended))" -ForegroundColor Yellow }
    exit 1
}

Write-Host ""
Write-Host "All required Windows packaging tools are available." -ForegroundColor Green
