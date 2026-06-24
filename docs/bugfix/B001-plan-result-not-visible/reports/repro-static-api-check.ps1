$path = "aps-system/src/main/resources/static/08-plan-result.html"
$content = Get-Content -Raw -Encoding UTF8 $path
if ($content -match "const API\s*=\s*'http://localhost:8080'") {
  Write-Error "BUG reproduced: 08-plan-result.html hard-codes API to http://localhost:8080 instead of using current origin."
  exit 1
}
Write-Host "PASS: plan result page uses dynamic/current origin API base."
