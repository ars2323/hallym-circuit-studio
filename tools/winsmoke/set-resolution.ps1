# 러너 화면을 원하는 해상도로 바꾼다(X-05). 성공하면 "OK WxH", 아니면 "SKIP <이유>"를 찍는다. 종료 코드는 늘 0이다:
# 러너가 허용하지 않으면 1920×1080 장면을 건너뛰고 그 사실을 로그에 남긴다.
param([int]$Width = 1920, [int]$Height = 1080)
$ErrorActionPreference = 'Continue'
Add-Type -AssemblyName System.Windows.Forms
$before = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
Write-Host "screen before: $($before.Width)x$($before.Height)"
$code = -1
if (Get-Command Set-DisplayResolution -ErrorAction SilentlyContinue) {
    try { Set-DisplayResolution -Width $Width -Height $Height -Force -ErrorAction Stop; $code = 0 } catch { Write-Host "Set-DisplayResolution: $_" }
}
if ($code -ne 0) {
    try {
        Add-Type -Path (Join-Path $PSScriptRoot 'Display.cs')
        $code = [Display]::Set($Width, $Height)
        Write-Host "ChangeDisplaySettings: $code"
    } catch { Write-Host "Add-Type Display.cs: $_" }
}
Start-Sleep -Seconds 2
$after = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
Write-Host "screen after: $($after.Width)x$($after.Height)"
if ($after.Width -ge $Width -and $after.Height -ge $Height) { Write-Output "OK $($after.Width)x$($after.Height)" }
else { Write-Output "SKIP screen is $($after.Width)x$($after.Height) (code $code)" }
