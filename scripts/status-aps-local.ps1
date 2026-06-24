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

wsl bash -lc "cd '$wslRepoRoot' && bash scripts/aps-local.sh status"
