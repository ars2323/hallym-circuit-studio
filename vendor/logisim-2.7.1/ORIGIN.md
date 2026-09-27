# vendor/logisim-2.7.1 — 출처

**이 폴더의 원본은 고치지 않는다(CLAUDE.md 규칙 2.2).** 이 파일만 우리가 더한 것이다.

- **무엇:** Logisim 2.7.1 배포 jar `logisim-generic-2.7.1.jar`(Carl Burch, GNU GPL 2 이상). jar 안에 소스(`src/`), 리소스(`resources/`), 도움말(`doc/`), 라이선스(`COPYING.TXT`)가 들어 있다.
- **어디서:** 사용자가 저장소를 시작할 때 둔 파일이다. SourceForge `circuit` 프로젝트의 배포 파일과 바이트까지 같다(2026-09-27 확인):
  https://downloads.sourceforge.net/project/circuit/2.7.x/2.7.1/logisim-generic-2.7.1.jar
- **언제:** 2026-09-24, 커밋 5c2336f("Set up repository layout, license and vendor checks")에서 `vendor/`로 옮겼다.
- **체크섬(SHA-256):** `362a78c12ad18c203fed868872c4a01cd9c12141379d92e892bbe2c37e627bc2`
- **포크의 시작점:** jar 안의 `src/`·`resources/`·`doc/`를 그대로 `app/`에 들인 커밋 d483ff8에 태그 `upstream/logisim-2.7.1`을 달았다. 포크가 원본과 다른 곳은 `git diff upstream/logisim-2.7.1 -- app/src`로 본다.

원본 그대로인지 기계가 확인하는 곳:

- `tools/verify-vendor.sh`: 이 폴더의 체크섬(`docs/vendor-checksums.sha256`)과, 원본에 없던 파일이 이 `ORIGIN.md` 말고는 없는지.
- `tools/check-engine-unchanged.sh`: 엔진 패키지(`circuit`, `comp`, `data`, `instance`, `std`, `file`)와 `Main.java`가 jar 소스와 같은지. 달라도 되는 파일은 `docs/engine-patches.txt`(D-번호).
- `tools/check-upstream-markers.sh`: `app/`의 원본 파일 가운데 jar와 다른 `.java`마다 `// HCS:` 주석이 있는지, 원본 트리에 파일이 새로 생기거나 빠지지 않았는지, 다른 리소스는 `docs/upstream-resources.txt`에 있는지.
