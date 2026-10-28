$ErrorActionPreference = "Stop"

$jarPath = Join-Path $PSScriptRoot "backend\target\fitness-tracker-api-0.2.0.jar"
if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Backend JAR not found at '$jarPath'. Build the backend first."
}

$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($bytes)
} finally {
    $rng.Dispose()
}

$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
java -jar $jarPath
