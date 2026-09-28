# Windows 설치 안내 (조각)

안내서(GUIDE-ko, TA-GUIDE-ko, README)와 릴리스 노트가 가져다 쓰는 설치 문단이다(N-23, D-148). 문장을 고치면 가져다 쓴 곳도 함께 고친다.

## 학생용

**설치:** 릴리스 페이지에서 `HallymCircuitStudio-<버전>-win-x64-setup.exe`를 받아 실행합니다. 묻는 화면 없이 바로 설치되고, 관리자 권한도 Java도 필요 없습니다. 설치가 끝나면 시작 메뉴에서 엽니다: **Hallym Circuit Studio**.

**"Windows의 PC 보호" 창이 뜨면:** 설치 파일에 코드 서명이 없어서 Microsoft Defender SmartScreen이 막는 것입니다. 창에서 **추가 정보** → **실행** 순서로 누릅니다. 받은 파일이 릴리스의 파일과 같은지는 릴리스 노트의 SHA-256으로 확인할 수 있습니다(명령 프롬프트: `certutil -hashfile HallymCircuitStudio-<버전>-win-x64-setup.exe SHA256`).

**예전 버전:** 1.0.x를 MSI(`hallym-circuit-studio-1.0.x-windows.msi`)로 설치한 PC에서는 새 버전을 설치할 때 예전 설치본을 조용히 지웁니다. 1.0.3 zip을 풀어 쓰던 폴더는 설치가 아니므로 그대로 둡니다(필요 없으면 직접 지웁니다). 새 버전을 다시 설치하거나 더 새 버전을 설치하면 같은 자리에 덮어씁니다.

**제거:** 설정 › 앱에서 다음 항목을 제거합니다: **Hallym Circuit Studio <버전>**. 직접 저장한 `.circ` 파일은 지우지 않습니다.

**`.circ` 파일:** 설치해도 `.circ` 파일을 두 번 눌렀을 때 여는 프로그램은 바뀌지 않습니다(원조 Logisim 연결도 그대로입니다). 이 프로그램에서 열 때는 시작 화면의 [파일 열기] 단추나 Ctrl+O 키를 씁니다.

## 조교·실습실 관리자용

- **설치 위치:** 사용자마다 `%LOCALAPPDATA%\Programs\Hallym Circuit Studio`. 설치 파일이 쓰는 것은 이 폴더, 시작 메뉴 바로 가기(`%APPDATA%\Microsoft\Windows\Start Menu\Programs\Hallym Circuit Studio.lnk`), `HKCU`의 제거 항목(`Software\Microsoft\Windows\CurrentVersion\Uninstall\eb84d729-7626-52ce-aff8-71eda9d27e59`)과 그 옆 설치 기록(`Software\eb84d729-7626-52ce-aff8-71eda9d27e59`: 설치 위치, 덮어 설치에 씀)뿐이다. 바탕 화면 바로 가기, 파일 연결, 자동 업데이트, 설치 파일 사본은 없다.
- **조용한 설치·제거:** `HallymCircuitStudio-<버전>-win-x64-setup.exe /S`. 제거는 제거 항목의 `QuietUninstallString`(`"...\Uninstall HallymCircuitStudio.exe" /currentuser /S`).
- **실행해도 남는 것이 없다:** 프로그램은 설정을 기억하지 않는다. 실행하는 동안 `%TEMP%\HallymCircuitStudio\run-<pid>-<시각>`을 쓰고 끝나면 지운다. 레지스트리(`HKCU\Software\JavaSoft\Prefs` 포함), `%APPDATA%`, `%LOCALAPPDATA%`(설치 폴더 밖)에 쓰지 않는다. CI가 설치본을 실행해 확인한다(`setup-e2e`).
- **복원 소프트웨어가 있는 PC:** 사용자별 설치가 재부팅 때 지워지는 PC에서는 설치 파일을 공용 폴더에 두고 매번 `/S`로 설치하거나, 복원 기준 이미지에 설치해 둔다.
- **예전 MSI를 관리자가 모든 사용자용으로 설치했다면:** 새 설치 파일은 관리자 권한을 쓰지 않으므로 그것을 지우지 못하고 "설정 › 앱에서 다음 항목을 직접 제거하세요: HallymCircuitStudio" 안내를 띄운다(새 버전은 설치된다). 관리자가 `msiexec /x {145CACD7-ADE5-3DF4-8496-7C4ACF95DF62} /qn`(1.0.2)처럼 지운다.
