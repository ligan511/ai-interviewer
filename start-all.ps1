$ErrorActionPreference = "Continue"

$base = "D:\AI 智能面试官与求职能力评估系统"
$aiDir = Join-Path $base "src\ai-service"
$beDir = Join-Path $base "src\backend"
$feDir = Join-Path $base "src\frontend"

# Start AI Service
$aiProc = Start-Process python (Join-Path $aiDir "main.py") -WorkingDirectory $aiDir -PassThru -WindowStyle Normal
Write-Host "AI Service PID: $($aiProc.Id)"

# Start Backend
$jar = Get-ChildItem (Join-Path $beDir "target") -Filter "*.jar" | Where-Object { $_.Name -notlike "*original*" } | Select-Object -First 1
if ($jar) {
    $beProc = Start-Process java -ArgumentList "-jar", "`"$($jar.FullName)`"" -PassThru -WindowStyle Normal
    Write-Host "Backend PID: $($beProc.Id)"
} else {
    Write-Host "No jar found in target/"
    Get-ChildItem (Join-Path $beDir "target") -Filter "*.jar" | ForEach-Object { Write-Host "Found: $($_.Name)" }
}

# Start Frontend
$feProc = Start-Process npm -ArgumentList "run","dev" -WorkingDirectory $feDir -PassThru -WindowStyle Normal
Write-Host "Frontend PID: $($feProc.Id)"

# Wait and check
Start-Sleep -Seconds 20

Write-Host ""
Write-Host "--- Service Status ---"
$ports = @(8001, 8081, 5173)
foreach ($p in $ports) {
    $conn = Get-NetTCPConnection -LocalPort $p -ErrorAction SilentlyContinue
    if ($conn) {
        Write-Host "Port $p : LISTENING (PID $($conn.OwningProcess))"
    } else {
        Write-Host "Port $p : NOT RUNNING"
    }
}
