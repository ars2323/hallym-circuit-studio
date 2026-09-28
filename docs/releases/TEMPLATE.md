# Hallym Circuit Studio <버전>

<!-- 새 릴리스의 노트: 이 파일을 docs/releases/<버전>.md로 복사해 채운다(docs/release.md).
     CI release 작업이 이 노트 뒤에 파일별 SHA-256을 붙여 draft를 만든다. 이 주석 줄들은 지운다. -->

<한두 문장: 이번 버전이 무엇인지. 학생 눈높이로.>

## 새로 바뀐 것

- <바뀐 것>

## 받을 파일

| 파일 | 무엇 |
| --- | --- |
| `HallymCircuitStudio-<버전>-win-x64-setup.exe` | Windows 설치 파일(이것 하나면 됩니다) |
| `hcs-mips.jar`, `hcs-mips-<버전>-windows.zip` | 원조 Logisim 2.7.1에서 쓰는 MIPS 부품 라이브러리(트랙 A) |
| `hallym-circuit-studio-GUIDE-ko.pdf` 등 | 안내서 |

## 설치

- 설치 파일을 받아 실행합니다. 진행 화면 뒤 "설치가 완료되었습니다" 화면에서 **지금 실행하기**가 체크된 채로 **마침**을 누르면 바로 열립니다. 관리자 권한도 Java도 필요 없습니다. 다음부터는 시작 메뉴에서 엽니다: **Hallym Circuit Studio**.
- **"Windows의 PC 보호" 창이 뜨면** 창에서 **추가 정보** → **실행** 순서로 누릅니다. 설치 파일에 코드 서명이 없어서 Microsoft Defender SmartScreen이 막는 것입니다. 받은 파일은 아래 SHA-256으로 확인할 수 있습니다.
- 1.0.x를 MSI로 설치한 PC에서는 예전 설치본을 조용히 지우고 설치합니다. 1.0.3 zip 폴더는 그대로 둡니다.
- 설치해도 `.circ` 파일의 연결 프로그램은 바뀌지 않습니다. 설정은 기억하지 않아 켤 때마다 기본값입니다(실습실 PC 규칙).
- 제거: 설정 › 앱에서 다음 항목을 제거합니다: **Hallym Circuit Studio <버전>**.

## 알려진 한계

- <한계>
