$ErrorActionPreference = "Stop"

$gatewayConfig = "sources/backend/api-gateway/src/main/resources/application.yml"

if (-not (Test-Path $gatewayConfig)) {
  throw "Gateway config not found at $gatewayConfig"
}

$content = Get-Content -Path $gatewayConfig -Raw

$requiredPatterns = @(
  "Path=/api/orders/**",
  "RewritePath=/api/orders/(?<segment>.*), /${segment}",
  "Path=/api/payments/**",
  "RewritePath=/api/payments/(?<segment>.*), /${segment}"
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

Write-Host "Gateway smoke check passed for order and payment prefixes." -ForegroundColor Green
exit 0
