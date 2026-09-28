# Windows 설치 안내 (조각)

안내서(GUIDE-ko, TA-GUIDE-ko, README)와 릴리스 노트가 가져다 쓰는 설치 문단이다(N-23, D-148; 안내형 설치 D-155). 문장을 고치면 가져다 쓴 곳도 함께 고친다.

## 학생용

**설치:** 릴리스 페이지에서 `HallymCircuitStudio-<버전>-win-x64-setup.exe`를 받아 실행합니다. 진행 화면 뒤에 "설치가 완료되었습니다" 화면이 나오고, **지금 실행하기**가 체크된 채로 **마침**을 누르면 바로 열립니다. 폴더나 사용자 범위는 묻지 않고, 관리자 권한도 Java도 필요 없습니다. 다음부터는 시작 메뉴에서 엽니다: **Hallym Circuit Studio**.

**"Windows의 PC 보호" 창이 뜨면:** 설치 파일에 코드 서명이 없어서 Microsoft Defender SmartScreen이 막는 것입니다. 창에서 **추가 정보** → **실행** 순서로 누릅니다. 받은 파일이 릴리스의 파일과 같은지는 릴리스 노트의 SHA-256으로 확인할 수 있습니다(명령 프롬프트: `certutil -hashfile HallymCircuitStudio-<버전>-win-x64-setup.exe SHA256`).

**예전 버전:** 1.0.x를 MSI(`hallym-circuit-studio-1.0.x-windows.msi`)로 설치한 PC에서는 새 버전을 설치할 때 예전 설치본을 조용히 지웁니다. 1.0.3 zip을 풀어 쓰던 폴더는 설치가 아니므로 그대로 둡니다(필요 없으면 직접 지웁니다). 새 버전을 다시 설치하거나 더 새 버전을 설치하면 같은 자리에 덮어씁니다.

**제거:** 설정 › 앱에서 다음 항목을 제거합니다: **Hallym Circuit Studio <버전>**. 진행 화면 뒤에 "제거가 끝났습니다" 화면이 나옵니다. 직접 저장한 `.circ` 파일은 지우지 않습니다.

**`.circ` 파일:** 설치해도 `.circ` 파일을 두 번 눌렀을 때 여는 프로그램은 바뀌지 않습니다(원조 Logisim 연결도 그대로입니다). 이 프로그램에서 열 때는 시작 화면의 [파일 열기] 단추나 Ctrl+O 키를 씁니다.

## 조교·실습실 관리자용

- **설치 파일이 쓰는 것(이 넷이 전부):**
  1. 설치 폴더 `%LOCALAPPDATA%\Programs\Hallym Circuit Studio`(사용자마다)
  2. 시작 메뉴 바로 가기 `%APPDATA%\Microsoft\Windows\Start Menu\Programs\Hallym Circuit Studio.lnk`
  3. 제거 항목 `HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\eb84d729-7626-52ce-aff8-71eda9d27e59`
  4. electron-builder의 설치 기록 키 `HKCU\Software\eb84d729-7626-52ce-aff8-71eda9d27e59`(설치 위치·바로 가기 이름: 다음 설치가 같은 폴더에 덮어 쓰려고 읽는다)

  제거하면 넷 모두 지운다. 제거 프로그램은 도는 동안 `%TEMP%\~nsu<X>.tmp`에 제 사본을 두고, 끝나면 그 폴더도 지운다. 바탕 화면 바로 가기, 파일 연결, 자동 업데이트, 설치 파일 사본은 없다.
- **설치 화면(D-155, Hallym MIPS 2.5.0과 같음):** 진행 화면("설치하는 중", 앱 파랑 진행 막대) → 마침 화면("설치가 완료되었습니다", "지금 실행하기" 체크, 왼쪽에 남색 띠와 학교 심벌) 두 장뿐이다. 설치 폴더·모든 사용자용 선택·사용권 화면은 없다. 제거도 진행 → "제거가 끝났습니다" 두 장이다.
- **조용한 설치·제거:** `HallymCircuitStudio-<버전>-win-x64-setup.exe /S`(화면 없음, 끝나도 프로그램을 띄우지 않는다). 제거는 제거 항목의 `QuietUninstallString`(`"...\Uninstall HallymCircuitStudio.exe" /currentuser /S`).
- **실행한 뒤:** 프로그램은 설정을 기억하지 않는다. 실행하는 동안 `%TEMP%\HallymCircuitStudio\run-<pid>-<시각>`을 쓰고, 끝나면 그 폴더를 지운다(다른 실행이 없으면 `HallymCircuitStudio` 폴더까지). Windows 맞춤법 검사기의 언어도 열지 않는다(단어 목록을 만들지 않는다). CI(`setup-e2e`)가 설치한 프로그램을 띄워 쓰고 끝낸 뒤, 레지스트리 `HKCU\Software` 전체(Microsoft·Classes, `JavaSoft\Prefs` 포함)와 `%APPDATA%`, `%LOCALAPPDATA%`(설치 폴더 밖), `%TEMP%`를 실행 전과 비교해 달라진 것이 없음을 확인한다. 비교에서 빼는 것은 두 가지뿐이다(`electron/tools/windows/state.ts`, D-148 12): 그 실행 바로 앞의 대조 구간 하나(프로그램을 돌리지 않고 20초 기다린 동안, 실행이 20초보다 길면 실패)에 Windows가 스스로 바꾼 정확한 자리, 그리고 어떤 프로그램이 시작해도 Windows와 시험 도구가 남기는 짧은 목록. 다른 단계의 대조 구간은 쓰지 않는다. CI는 러너의 첫 로그온 작업이 끝난 뒤(로그온 + 6분 30초)에 재고, 그동안 Windows의 사용자 세션 예약 작업(`\Microsoft\Windows\`)은 꺼 둔다. 그래서 실습실 PC에서 그 작업들이 하는 일은 CI가 보지 못한다(D-148 12 "이 검사가 볼 수 없는 것"). 그중 하나: Chromium이 시작할 때 Windows 맞춤법 기능에 지원 언어를 물으면 Windows가 값 없는 빈 키 `HKCU\Software\Microsoft\Spelling`을 만들 수 있다(다른 프로그램도 같다). 이 프로그램의 이름이 든 바뀜은 대조 구간에서 보였어도 센다.
- **Windows가 모든 프로그램에 대해 두는 기록:** 설치·실행·제거하면 Windows 자신이 탐색기의 실행 횟수(`HKCU\...\Explorer\UserAssist`, 이 프로그램의 앱 ID), Windows Search의 시작 메뉴 아이콘(`%LOCALAPPDATA%\Packages\Microsoft.Windows.Search_cw5n1h2txyewy\LocalState\AppIconCache\100\kr_ac_hallym_circuit-studio`), 1.0.x MSI를 지운 PC라면 셸의 바로 가기 기록(`HKCU\...\UFH\SHC`)을 남길 수 있다. 설치 파일이나 프로그램이 쓰는 것이 아니고, 다른 프로그램에도 똑같이 생기며, 제거해도 Windows가 둔다.
- **복원 소프트웨어가 있는 PC:** 사용자별 설치가 재부팅 때 지워지는 PC에서는 설치 파일을 공용 폴더에 두고 매번 `/S`로 설치하거나, 복원 기준 이미지에 설치해 둔다.
- **예전 MSI를 관리자가 모든 사용자용으로 설치했다면:** 새 설치 파일은 관리자 권한을 쓰지 않으므로 그것을 지우지 못하고 "설정 › 앱에서 다음 항목을 직접 제거하세요: HallymCircuitStudio" 안내를 띄운다(새 버전은 설치된다). 관리자가 `msiexec /x {145CACD7-ADE5-3DF4-8496-7C4ACF95DF62} /qn`(1.0.2)처럼 지운다.
