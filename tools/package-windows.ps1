# Windows 배포물(R-01): jpackage로 JRE를 포함한 앱 폴더(zip, 관리자 권한 없이 풀어 실행)와 MSI를 만든다.
#   pwsh tools/package-windows.ps1 -Version 1.0.0 -Dest dist
# 앞서 `gradlew :app:stage :lib-mips:jar`와 hcs-asm.exe 빌드가 끝나 있어야 한다(app/build/stage/, native/hcs-asm/build/).
# .circ 파일 연결은 넣지 않는다(설치 옵션으로만 두기로 한 결정, D-094). MSI는 사용자별 설치, 시작 메뉴·바로가기, 폴더 선택.
param(
  [string]$Version = "1.0.0",
  [string]$Dest = "dist",
  [switch]$NoMsi
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root
$stage = "app/build/stage"
if (-not (Test-Path "$stage/hallym-circuit-studio.jar")) { throw "run ./gradlew :app:stage first" }
if (-not (Test-Path "$stage/lib/hcs-asm.exe")) {
  if (Test-Path "native/hcs-asm/build/hcs-asm.exe") { Copy-Item native/hcs-asm/build/hcs-asm.exe "$stage/lib/" } else { throw "hcs-asm.exe missing" }
}
New-Item -ItemType Directory -Force $Dest | Out-Null
$name = "HallymCircuitStudio"
if (Test-Path "$Dest/$name") { Remove-Item -Recurse -Force "$Dest/$name" }
$common = @(
  "--name", $name,
  "--app-version", $Version,
  "--vendor", "AIAC Lab, Hallym University",
  "--copyright", "Copyright (c) 2026 AIAC Lab, Hallym University. GPL-2.0-or-later.",
  "--description", "Hallym Circuit Studio - Micro-architecture lab tool (Logisim 2.7.1 fork)",
  "--icon", "assets/hallym/logo/app.ico",
  "--java-options", "-Dfile.encoding=UTF-8",
  "--java-options", "-Xss4m"
)
# 1) 앱 폴더(zip): runtime + app/hallym-circuit-studio.jar + app/lib/(hcs-mips.jar, hcs-asm.exe)
& jpackage --type app-image --input $stage --main-jar hallym-circuit-studio.jar --main-class com.cburch.logisim.Main --dest $Dest @common
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed" }
Copy-Item LICENSE "$Dest/$name/LICENSE.txt"
Copy-Item NOTICE "$Dest/$name/NOTICE.txt"
Copy-Item docs/GUIDE-ko.md "$Dest/$name/사용안내.md" -ErrorAction SilentlyContinue
$zip = "$Dest/hallym-circuit-studio-$Version-windows.zip"
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path "$Dest/$name" -DestinationPath $zip
Write-Host "zip: $zip"
# 2) MSI(설치형): 파일 연결 없음, 사용자별, 시작 메뉴·바탕화면 바로가기, 폴더 선택
if (-not $NoMsi) {
  & jpackage --type msi --app-image "$Dest/$name" --dest $Dest --name $name --app-version $Version --vendor "AIAC Lab, Hallym University" --win-menu --win-shortcut --win-dir-chooser --win-per-user-install --win-menu-group "Hallym Circuit Studio"
  if ($LASTEXITCODE -ne 0) { throw "jpackage msi failed" }
  Get-ChildItem $Dest -Filter *.msi | ForEach-Object { Rename-Item $_.FullName "hallym-circuit-studio-$Version-windows.msi" -Force; Write-Host "msi: $($_.Name)" }
}
Get-ChildItem $Dest
