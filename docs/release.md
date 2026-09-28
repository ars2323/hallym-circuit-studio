# 릴리스 내는 법 (v2부터)

Hallym Circuit Studio 2의 릴리스를 만드는 순서와 규칙이다(N-23, D-148). v1.0.x(Swing판)의 릴리스는 게시한 그대로 두고 지우지 않는다.

## 1. 무엇을 올리나

| 파일 | 어디서 | 비고 |
| --- | --- | --- |
| `HallymCircuitStudio-<버전>-win-x64-setup.exe` | CI `setup-exe` 작업 | **Windows 배포물은 이것 하나**(electron-builder NSIS, 사용자별 원클릭, 엔진과 번들 JRE 포함) |
| `hcs-mips.jar`, `hcs-mips-<버전>-windows.zip`, `hcs-mips-<버전>-linux.zip` | CI `linux` 작업 + `tools/package-track-a.sh` | 트랙 A: 원조 Logisim 2.7.1에서 쓰는 MIPS 부품 라이브러리 |
| 안내 PDF(`hallym-circuit-studio-GUIDE-ko.pdf` 등, 이름에 `guide`) | 손으로 만들어 올림 | 안내서 md에서 만든다 |

**올리면 안 되는 것:** 앱의 zip, MSI, 그 밖의 모든 파일(블록맵, `hcs-asm`, md 안내 등). 규칙은 `electron/tools/release-assets.ts` 하나이고, 세 곳에서 돈다.

- CI `release` 작업: 올리기 전에 모은 파일을, 올린 뒤에는 릴리스에 실제로 붙은 파일을 본다. PR·main push에서는 같은 파일을 모아 규칙만 본다(dry run).
- `.github/workflows/release-assets.yml`: 릴리스를 게시하거나 고칠 때마다(손으로 올린 PDF 포함) 다시 본다. 손으로 돌릴 수도 있다: `gh workflow run release-assets.yml -f tag=v2.0.0`.
- 손으로: `node electron/tools/release-assets.ts check --version 2.0.0 <파일>...`

2.0.0 이전 버전(1.0.x)은 이 규칙을 적용하지 않는다(그때는 zip과 MSI를 올렸다).

## 2. 순서

1. **버전:** `electron/package.json`의 `version`을 릴리스 버전으로 올린다(`npm version --no-git-tag-version <버전>`으로 lock도 함께). 사전 릴리스는 `2.0.0-alpha.1`처럼 `-`를 붙인다. 태그와 이 값이 다르면 CI가 실패한다.
2. **노트:** `docs/releases/TEMPLATE.md`를 `docs/releases/<버전>.md`로 복사해 채운다. 학생 눈높이로 쓰고, 설치 절(SmartScreen 안내 포함)은 템플릿 그대로 둔다. 파일이 없으면 CI가 실패한다.
3. 위 둘을 PR로 main에 넣는다(CI 초록).
4. **태그:** main에서 버전을 올린 그 커밋에 주석 태그를 단다. `git tag -a v<버전> -m "Hallym Circuit Studio <버전>" && git push origin v<버전>`
   - 그 커밋의 검사에서 문제가 나왔을 때만, 버전을 바꾸지 않고 그 문제만 고친 초록 후손 커밋에 태그를 달 수 있다. 이때 모든 검사를 태그 커밋에서 돌리고(태그 push가 CI 전체를 돌린다), 보고에 두 커밋(버전을 올린 커밋, 태그 커밋)을 적는다(D-154).
5. **CI(태그):** `setup-exe`(설치 파일) → `setup-e2e`(깨끗한 Windows에서 조용히 설치·실행·실행 전후 비교(Windows 자신이 바꾸는 정해진 자리만 빼고 달라진 것 없음, D-148 12)·다시 설치·제거·예전 버전 위에 설치), `setup-upgrade`(v1.0.2 MSI 위에 설치) → `release`: 파일 규칙 확인, 노트 + SHA-256으로 **draft** 릴리스를 만들고(버전에 `-`가 있으면 사전 릴리스로 표시), 붙은 파일을 다시 확인한다.
6. **안내 PDF:** 안내서 md에서 PDF를 만들어 draft에 올린다. `gh release upload v<버전> hallym-circuit-studio-GUIDE-ko.pdf hallym-circuit-studio-TA-GUIDE-ko.pdf`
7. **게시:** draft를 확인하고 게시한다. 사전 릴리스: `gh release edit v<버전> --draft=false`(Latest가 되지 않는다). 정식: `gh release edit v<버전> --draft=false --latest`. 게시하면 `release-assets.yml`이 파일을 다시 본다.
8. **배포 후 확인:** 공개 주소에서 setup exe를 받아(토큰 없이) SHA-256이 노트와 같은지 보고(크기와 해시는 이 공개 파일의 것만 의미가 있다: NSIS가 빌드 시각을 넣어 설치 파일 바이트가 빌드마다 다르므로, PR·main 실행에서 만든 파일의 값은 릴리스 값이 아니다. D-154), 깨끗한 Windows에 조용히 설치해 시작 화면을 본 뒤 제거한다(v2.0.0은 N-28의 전체 확인). 학생에게 알리는 것은 사람이 한다.

## 3. 설치 파일을 로컬에서 만들 때

```
./gradlew :engine:stage :engine:runtime      # 엔진 jar 둘과 번들 JRE (이 OS용)
cd electron && npm ci
node tools/package.ts                         # Windows: dist/HallymCircuitStudio-<버전>-win-x64-setup.exe
node tools/package.ts --dir                   # 이 OS용 풀린 앱만(확인용)
```

Windows 설치 파일은 Windows에서 만든다(번들 JRE가 OS마다 다르고, exe의 아이콘·버전 정보는 Windows에서만 넣는다). Linux에서 `--win`으로 NSIS 스크립트가 컴파일되는지만 볼 수 있다(electron-builder가 제거 프로그램을 만들 때 wine을 부르므로 wine이 있어야 끝까지 간다).

## 4. 코드 서명이 없다

설치 파일에 코드 서명을 하지 않는다(인증서가 없다). 처음 받은 PC에서 Microsoft Defender SmartScreen이 "Windows의 PC 보호" 창을 띄운다. 학생 안내와 릴리스 노트에 **추가 정보 → 실행** 안내를 넣는다(`docs/install-windows-ko.md`, `docs/releases/TEMPLATE.md`). 서명을 하게 되면 `electron/tools/package-config.ts`의 `win`에 인증서 설정을 더하고 CI의 `CSC_IDENTITY_AUTO_DISCOVERY`를 지운다.
