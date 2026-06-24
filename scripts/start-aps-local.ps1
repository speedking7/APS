param(
    [int]$Port,
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Convert-ToWslPath {
    param([string]$Path)

    $resolved = (Resolve-Path $Path).Path
    $drive = $resolved.Substring(0, 1).ToLowerInvariant()
    $rest = $resolved.Substring(2).Replace("\", "/")
    return "/mnt/$drive$rest"
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$wslRepoRoot = Convert-ToWslPath $repoRoot

if (-not $PSBoundParameters.ContainsKey("Port")) {
    $Port = if (Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue) { 8081 } else { 8080 }
}

$args = @("bash", "-lc", "cd '$wslRepoRoot' && bash scripts/aps-local.sh start --port $Port")
if ($SkipBuild) {
    $args[2] += " --skip-build"
}

wsl @args
