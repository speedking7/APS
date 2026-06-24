$ErrorActionPreference = 'Stop'

$files = @(
  'aps-system/src/main/resources/static/12-equipment-catalog.html',
  'aps-system/src/main/resources/static/13-part-master.html'
)

$requiredLinks = @(
  '02-forecast-list.html',
  '03-bom-list.html',
  '13-part-master.html',
  '12-equipment-catalog.html',
  '14-shared-mold-rules.html',
  '04-material-params.html',
  '05-operating-days.html',
  '06-inventory-count.html'
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$failed = $false

foreach ($relativePath in $files) {
  $fullPath = Join-Path $repoRoot $relativePath
  $content = Get-Content -Raw -Path $fullPath
  $missing = @()

  foreach ($link in $requiredLinks) {
    if ($content -notmatch [regex]::Escape($link)) {
      $missing += $link
    }
  }

  if ($missing.Count -gt 0) {
    Write-Host "FAIL $relativePath"
    foreach ($item in $missing) {
      Write-Host "  missing: $item"
    }
    $failed = $true
  } else {
    Write-Host "PASS $relativePath"
  }
}

if ($failed) {
  exit 1
}
