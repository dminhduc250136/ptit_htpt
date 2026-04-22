$ErrorActionPreference = "Stop"

$gatewayConfig = "sources/backend/api-gateway/src/main/resources/application.yml"

if (-not (Test-Path $gatewayConfig)) {
  throw "Gateway config not found at $gatewayConfig"
}

$content = Get-Content -Path $gatewayConfig -Raw

$requiredPatterns = @(
  "Path=/api/users/**",
  "RewritePath=/api/users/(?<segment>.*), /${segment}",
  "Path=/api/products/**",
  "RewritePath=/api/products/(?<segment>.*), /${segment}"
)

$missing = @()
foreach ($pattern in $requiredPatterns) {
  if ($content -notmatch [regex]::Escape($pattern)) {
    $missing += $pattern
  }
}

if ($missing.Count -gt 0) {
  Write-Host "Gateway smoke check failed. Missing routes:" -ForegroundColor Red
  $missing | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
  exit 1
}

Write-Host "Gateway smoke check passed for user and product prefixes." -ForegroundColor Green
exit 0
