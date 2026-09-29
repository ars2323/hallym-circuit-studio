# 명령어 필드 색: Hallym MIPS v2.6.0 Inspector와 대조

**결론(2026-09-29, D-167):** Cycle View의 Instruction 탭과 캔버스의 필드 색 덧그림은 Hallym MIPS v2.6.0 Inspector와 **같은 규칙**이다. 필드 경계, 필드 이름, 필드마다의 바탕색과 글자색, 그 색이 쓰는 토큰이 모두 같다. 고칠 곳은 없었다. 다음부터 어긋나면 시험이 필드 이름을 대며 실패한다.

- 비교한 upstream: `ars2323/hallym-mips-simulator` 태그 `v2.6.0`(커밋 `d8f0c97`). 아래 행 번호는 그 태그의 파일이다.
- 이 저장소: 기준 main `27f566d` 위의 이 PR.

## 1. 형식과 필드 경계

| 형식(워드의 opcode) | 필드(높은 비트부터) | Hallym MIPS | 여기 |
| --- | --- | --- | --- |
| R (0x00, 0x1c) | opcode 31–26, rs 25–21, rt 20–16, rd 15–11, shamt 10–6, funct 5–0 | `electron/src/core/decoder.ts` 235–237행(opcode는 모든 형식이 232행) | `engine/.../record/InstructionFields.java` 84–88행(opcode는 81행) |
| I (나머지) | opcode 31–26, rs 25–21, rt 20–16, immediate 15–0 | 239–241행 | 121–123행 |
| J (0x02, 0x03) | opcode 31–26, target 25–0 | 243–244행 | 90–91행 |
| CP0 (0x10), CO = 1 | opcode, CO 25, code 24–6, funct 5–0 | 246–249행 | 93–97행 |
| CP0 (0x10), CO = 0 | opcode, rs, rt, rd, 0 10–3, sel 2–0 | 250–253행 | 98–103행 |
| FR (0x11, fmt ≠ 8) | opcode, fmt 25–21, ft 20–16, fs 15–11, fd 10–6, funct 5–0 | 256–259행 | 106–111행 |
| FI (0x11, fmt = 8) | opcode, fmt, cc 20–18, nd 17, tf 16, immediate 15–0 | 261–263행 | 113–118행 |

형식 규칙은 `decoder.ts` 273–288행 `formatOf`와 `InstructionFields.format`(61행)이 같다. 형식 배지 글자는 upstream이 `formatName`(291–292행)으로 `Cp0`을 `CP0`으로 바꿔 쓰고, 여기는 엔진이 처음부터 `CP0`을 보낸다. 배지 색 규칙 `.b-R`·`.b-I`·`.b-J`·`.b-CP0`·`.b-FR, .b-FI`는 upstream `app.css` 294–298행과 여기 `shared/panels.css` 18–22행이 같다.

## 2. 필드마다의 색

upstream `electron/src/renderer/app/app.css` 421–427행과 여기 `electron/src/renderer/shared/panels.css` 133–139행. 토큰 값은 upstream `app.css` 20–25행, 여기 `shared/shared.css` 23–27행과 `panels.css` 11행(`--cp0-*`).

| 필드 | 바탕 | 글자(= 캔버스 띠 색) |
| --- | --- | --- |
| opcode, fmt | `#dfe5ef` | `--navy` `#00205b` |
| rs | `--blue-tint` `#e8f0f9` | `--blue` `#0055a5` |
| rt, ft | `--teal-tint` `#e6f6f5` | `--teal-text` `#00736f` |
| rd, fs | `--amber-tint` `#fdf3e1` | `--amber-text` `#8a5a00` |
| shamt, fd | `#efe9f6` | `--purple` `#6b4c9a` |
| funct | `--cp0-tint` `#eef0f2` | `--cp0-text` `#4a5560` |
| immediate, target, offset | `#e3eef0` | `#1d5c63` |
| CO, code, 0, sel, cc, nd, tf | 규칙 없음(패널 색) | 규칙 없음 → 캔버스 띠는 funct의 회색 `#4a5560` |

- **Instruction 탭.** `shared/inspector.ts` 50·59행이 필드 이름으로 `f-<이름>` 클래스를 붙인다(upstream `panels/inspector.ts` 62·65·74행과 같은 방식). 32칸 격자·필드 상자·비트 칸 규칙(upstream `app.css` 413–420행)도 `panels.css` 125–132행에 그대로 있다.
- **캔버스 필드 색 덧그림.** upstream에는 캔버스가 없다. 여기는 각 필드의 글자색(표의 오른쪽 열)을 `canvas/overlays/logic.ts` 19–28행 `FIELD_COLORS`에 두고, `canvas/overlays/bands.ts` 70행이 그 색을 불투명도 0.55로 선 둘레에 그린다. 규칙이 없는 필드는 funct의 회색이다.
- **밝은·어두운 테마.** upstream `app.css`에는 `prefers-color-scheme`도 테마 바꾸기도 없고 이 앱도 없다. 필드 색은 밝은 테마 한 벌뿐이다.

## 3. 시험

- `electron/tests/unit/field-colors.test.ts`: 위 표를 필드마다 옮겨 적고(upstream 행 번호를 주석에), `panels.css`의 `.f-*` 규칙과 토큰을 읽어 필드마다 바탕·글자가 같은지, 규칙이 있는 필드 목록이 같은지, `FIELD_COLORS`가 글자색과 같은지, 규칙이 없는 필드가 회색인지, 두 쪽 모두 어두운 테마가 없는지 본다. 돌연변이 두 개(shamt 바탕 한 단계, rd 띠를 rt 색으로)가 잡힌다(`tools/mutants.ts` "field colours").
- `engine/.../record/InstructionFieldsTest.fieldBoundariesAreHallymMips260`: 형식마다 필드 이름과 경계(`opcode:31-26 rs:25-21 …`)가 위 1절의 upstream과 같다.
- 기존 `electron/tests/unit/overlays.test.ts`의 "the colours" 시험은 캔버스 색이 `panels.css`와 같은지를 본다(이 PR 전부터).
