param(
    [switch]$KeepMySql
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

$command = "cd '$wslRepoRoot' && bash scripts/aps-local.sh stop"
if ($KeepMySql) {
    $command += " --keep-mysql"
}

wsl bash -lc $command
