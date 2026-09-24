# 결정 기록

작업 중 내린 결정을 한 항목씩 남긴다. 형식: 날짜, 결정, 이유, 대안. 새 항목은 아래에 붙인다.

## D-001 머지 전 compat-reviewer 검토

- **날짜:** 2026-09-24
- **결정:** 모든 PR은 `gh pr merge` 전에 `.claude/agents/compat-reviewer.md` 서브에이전트로 diff를 검토한다. 위반이 있으면 고친 뒤 다시 돌린다. 에이전트의 Bash는 PreToolUse 훅(`.claude/hooks/readonly-git-guard.py`)으로 git 읽기 명령만 허용한다.
- **이유:** CLAUDE.md 2절 규칙은 한 번 어기면 채점 연속성과 라이선스 분리가 깨진다. PR을 만든 쪽과 독립된 검토가 필요하다. 서브에이전트 `tools` 필드는 `Bash(git diff:*)` 같은 패턴을 받지 않아 훅으로 제한했다.
- **대안:** Bash를 아예 빼는 것(`git diff`를 볼 수 없어 검토 불가), `permissionMode: plan`(Bash 명령 단위로 좁힐 수 없음).
- **참고:** 에이전트 정의는 세션 시작 때 로드된다. 정의를 추가한 첫 세션에서는 general-purpose 에이전트에 같은 정의 파일을 읽혀 대신 실행했다.

## D-002 저장소와 라이선스

- **날짜:** 2026-09-24
- **결정:** GitHub `ars2323/hallym-circuit-studio`를 private으로 만든다. 라이선스는 GNU GPL 버전 2 이상이고, LICENSE는 2.7.1 jar의 `COPYING.TXT`를 그대로 쓴다.
- **이유:** Logisim 2.7.1 소스 헤더가 "GPL version 2, or (at your option) any later version"이다. 포크는 원본 조건을 따른다. public 전환은 되돌릴 수 없는 사용자 결정이라 `needs-human`에 남긴다.
- **대안:** GPL-3.0-only로 올리기(가능하지만 원본보다 좁힐 이유가 없음).

## D-003 vendor 원본 무결성 검증

- **날짜:** 2026-09-24
- **결정:** `vendor/` 원본의 SHA-256을 `docs/vendor-checksums.sha256`에 기록하고, `tools/verify-vendor.sh`로 CI에서 매번 확인한다. 체크섬 파일은 `vendor/` 밖에 둔다.
- **이유:** 규칙 2.2(vendor 수정 금지)를 사람 눈이 아니라 기계로 지킨다. `vendor/` 안에 파일을 더하는 것 자체가 원본 변경이므로 체크섬은 밖에 둔다.
- **대안:** git 이력만으로 확인(원본이 처음 커밋될 때 이미 바뀌었는지는 알 수 없음).
- **기록:** `logisim-generic-2.7.1.jar` SHA-256 `362a78c12ad18c203fed868872c4a01cd9c12141379d92e892bbe2c37e627bc2`. SPIM 9.1.24는 파일 268개.

## D-004 Actions가 막힌 동안의 머지 기준

- **날짜:** 2026-09-24
- **결정:** GitHub Actions가 계정 결제 문제로 시작되지 않는 동안에는, CI와 같은 스크립트 `tools/ci-local.sh`를 로컬에서 돌려 결과를 PR 본문에 붙이고 compat-reviewer 통과 후 머지한다. 워크플로도 같은 스크립트를 호출해 두 결과가 같게 한다.
- **이유:** "CI 통과 후 머지"의 목적은 같은 검사를 매번 기계로 돌리는 것이다. 결제는 사람 일이라(`needs-human` #54) 기다리면 모든 작업이 멈춘다. Actions가 돌아오면 추가 조치 없이 원래 절차로 돌아간다.
- **대안:** 결제 해결까지 머지 중단(작업 전체 정지), 저장소 공개로 무료 분량 사용(사용자 결정).
