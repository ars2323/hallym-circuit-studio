# vendor/spim-9.1.24 — 출처

**이 폴더의 원본은 고치지 않는다(CLAUDE.md 규칙 2.2).** 이 파일만 우리가 더한 것이다.

- **무엇:** SPIM/QtSpim 9.1.24 소스 트리(James R. Larus, BSD 3-Clause, `README`).
- **어디서:** 사용자가 저장소를 시작할 때 둔 폴더다. SourceForge `spimsimulator` Subversion의 r764이고, Hallym MIPS 저장소(ars2323/hallym-mips-simulator)의 태그 `vanilla-9.1.24`(커밋 21ee1b6, "vanilla SPIM/QtSpim 9.1.24 (SVN r764)")와 모든 파일이 같다(`diff -rq`, 2026-09-27 확인).
  https://sourceforge.net/projects/spimsimulator/
- **언제:** 2026-09-24, 커밋 5c2336f에서 `vendor/`로 옮겼다.
- **체크섬:** 파일마다 SHA-256을 `docs/vendor-checksums.sha256`에 적었다(268개).
- **쓰는 곳:** 명령줄 어셈블러 `native/hcs-asm`(BSD 3-Clause)이 `CPU/`를 수정 없이 링크한다. SPIM 코드는 GPL 코드와 섞지 않는다(규칙 2.5). 실행 이미지(.hmx) 전환이 끝나면 이 폴더를 지운다(#373).

원본 그대로인지 기계가 확인하는 곳: `tools/verify-vendor.sh`(체크섬, 원본에 없던 파일이 이 `ORIGIN.md` 말고는 없는지).
