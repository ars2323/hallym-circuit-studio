# assets

앱이 쓰는 글꼴과 한림대학교 식별요소. 모두 `tools/import-assets.py`가 사용자 원본(`resources/`, git 밖)과 Hallym MIPS(`ref/`, git 밖)에서 **꺼내거나 복사만** 해서 만든다. 이미지를 다시 인코딩하거나 고치지 않는다. `MANIFEST.sha256`에 모든 파일의 SHA-256이 있고, CI의 `tools/verify-assets.sh`가 매번 대조한다.

```sh
python3 tools/import-assets.py   # resources/와 ref/가 있을 때 다시 가져오기
tools/verify-assets.sh           # 바이트 그대로인지 확인
```

## fonts/pretendard/

`resources/font/Pretendard-1.3.9.zip`의 `public/static/`에서 Regular, Medium, SemiBold, Bold OTF와 `LICENSE.txt`(SIL OFL 1.1). 가변 폰트와 웹 폰트는 넣지 않는다. 앱 시작 때 `Font.createFont`로 등록한다.

## fonts/d2coding/

D2Coding 1.3.2(NAVER, SIL OFL 1.1, 예약 이름 D2Coding) 공식 배포 zip의 `D2Coding/D2Coding-Ver1.3.2-20180524.ttf`를 고치지 않고 그대로(고치면 예약 이름을 쓸 수 없다). `LICENSE.txt`는 Hallym MIPS v2.3.0의 `OFL-D2Coding.txt`. 주소·기계어·레지스터 값 글꼴(Hallym MIPS와 같음, Z-12b). `tools/import-assets.py`가 체크섬을 확인하며 받는다.

## icons/lucide/

Lucide 아이콘(ISC). Hallym MIPS v2.3.0 `electron/src/renderer/assets/icons/lucide/`의 15개와 `LICENSE.txt`는 그 파일 그대로, Hallym MIPS에 없는 자리(도구 모음, 시작 카드 교과목, 패널 단추)는 같은 Lucide의 `lucide-static` 1.48.0에서 이름 그대로 가져와 Hallym MIPS 파일과 같은 꼴로 둔다(맨 앞 라이선스 주석과 class 속성만 뺌, 그림은 같다). 새로 그리지 않는다. 앱은 FlatSVGIcon으로 그린다.

## hallym/logo/

Hallym MIPS `QtSpim/edu/theme/brand/`의 파일을 이름만 바꿔 그대로 가져왔다(D-008). SVG는 Hallym MIPS `assets/ci/marks/`와 같은 파일이고(학교 배포 `.ai` → PDF → SVG, 마크별로 자르고 치수선 제거), PNG는 그 SVG를 렌더한 것이다. 두 제품의 로고가 픽셀 단위로 같다.

| 파일 | 쓰는 곳(Hallym MIPS 선례) |
| --- | --- |
| `app-16.png` … `app-256.png`, `app.ico` | 창·작업 표시줄 아이콘. 흰 둥근 타일에 심볼 기본형 |
| `symbol-basic*.{svg,png}` | 심볼마크 기본형 |
| `emblem-a-navy*.{svg,png}` | 정보 창, 112px |
| `logotype-ko-en*.{svg,png}` | 정보 창, 220px |
| `signature-h-ko-en*.{svg,png}` | 시작 화면, 260px / 320px |

## hallym/character/

`resources/hallym/character/character.zip` 안의 PNG를 영문 이름으로 바꿔 그대로 넣었다. 기본형 3종과 응용동작 20종이다. 가이드라인 PDF는 외부 노출을 금하는 문서라 넣지 않는다.

| 파일 | 원래 이름 |
| --- | --- |
| `haram-hari.png`, `haram.png`, `hari.png` | 캐릭터 기본형(조합), (하람), (하리) |
| `haram-hari-<동작>.png` | 응용동작_<동작>: greeting 인사, best 최고, ok OK, guide 안내, go GO, talk 소통, selfie 셀카, meal 식사(먹방), congrats 축하, notice 공지, no 금지, education 교육, curious 궁금해, love 사랑해, thanks 감사, moved 감동, sign 팻말, holiday 명절, graduation 입학(졸업), exercise 운동 |

## 사용 규칙

학교 식별요소는 한림대학교 소유이고 상업적 사용이 금지된다. 이 제품의 GPL 라이선스 대상이 아니다(NOTICE).

**로고(학교 UI 규정).** 원형 그대로 쓰고 크기와 여백만 조절한다. 다시 그리기, 색 변경, 비율 변경, 회전, 요소 분리·추가를 하지 않는다. 규정 원문과 출처는 Hallym MIPS `assets/ci/manual/README.md`에 있다(https://www.hallym.ac.kr/hallym/965/subview.do, 966).

**캐릭터(하람&하리 가이드라인 요약).**

- 요소 추가, 선 변경·삭제, 비율·형태 변경, 색 변경을 하지 않는다. 흐리거나 깨진 원고를 쓰지 않는다.
- 캐릭터와 비슷한 색이나 복잡한 배경 위에 두지 않는다. 흰색이나 옅은 단색 배경을 쓴다.
- 최소 여백: 캐릭터 높이를 15a라 할 때 사방으로 2a 이상 비운다.
- 크기는 늘리거나 줄이기만 한다. 가로세로 비율을 유지한다.

**쓰는 곳(CLAUDE.md 8절).** 첫 실행 튜토리얼, 정보 창, 빈 화면 안내, 프로그램 정상 종료(`exit`) 안내 정도로 아낀다. 오류 진단 메시지에는 쓰지 않는다.
