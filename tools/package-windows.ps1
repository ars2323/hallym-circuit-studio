# Windows 배포물(R-01): jpackage로 JRE를 포함한 앱 폴더(zip, 관리자 권한 없이 풀어 실행)를 만든다.
# MSI는 배포하지 않는다(D-122). -Msi는 v1.0.x MSI 설치본 위에 setup exe를 까는 검사(v1.1.0)에서만 쓴다.
#   pwsh tools/package-windows.ps1 -Version 1.0.0 -Dest dist
# 앞서 `gradlew :app:stage :lib-mips:jar`가 끝나 있어야 한다(app/build/stage/). hcs-asm.exe는 없어졌다(D-141).
# .circ 파일 연결은 넣지 않는다(D-094, D-122).
param(
  [string]$Version = "1.0.0",
  [string]$Dest = "dist",
  [switch]$Msi
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root
$stage = "app/build/stage"
if (-not (Test-Path "$stage/hallym-circuit-studio.jar")) { throw "run ./gradlew :app:stage first" }
if (Test-Path "$stage/lib/hcs-asm*") { throw "hcs-asm is no longer shipped (D-141)" }
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
# 1) 앱 폴더(zip): runtime + app/hallym-circuit-studio.jar + app/lib/hcs-mips.jar
& jpackage --type app-image --input $stage --main-jar hallym-circuit-studio.jar --main-class com.cburch.logisim.Main --dest $Dest @common
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed" }
Copy-Item LICENSE "$Dest/$name/LICENSE.txt"
Copy-Item NOTICE "$Dest/$name/NOTICE.txt"
Copy-Item docs/GUIDE-ko.md "$Dest/$name/GUIDE-ko.md"  # ASCII 이름: 설치 도구(WiX·NSIS)가 코드 페이지 밖 파일 이름에서 막히지 않게
$zip = "$Dest/hallym-circuit-studio-$Version-windows.zip"
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path "$Dest/$name" -DestinationPath $zip
Write-Host "zip: $zip"
# 2) MSI(검사용, 배포하지 않음): v1.0.x와 같은 옵션(사용자별, 시작 메뉴·바탕화면 바로가기, 폴더 선택, 파일 연결 없음)
if ($Msi) {
  & jpackage --type msi --app-image "$Dest/$name" --dest $Dest --name $name --app-version $Version --vendor "AIAC Lab, Hallym University" --win-menu --win-shortcut --win-dir-chooser --win-per-user-install --win-menu-group "Hallym Circuit Studio"
  if ($LASTEXITCODE -ne 0) { throw "jpackage msi failed" }
  Get-ChildItem $Dest -Filter *.msi | ForEach-Object { Rename-Item $_.FullName "hallym-circuit-studio-$Version-windows.msi" -Force; Write-Host "msi: $($_.Name)" }
}
Get-ChildItem $Dest
