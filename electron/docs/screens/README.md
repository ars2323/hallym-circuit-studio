# Screens — the fixed set

Every round, **all of them are retaken and overwritten** under the same names; no subfolders. All are taken by `tools/capture-screens.ts` (none by hand), with the fake engine (`tests/fake-engine/fake-engine.ts`), whose answers are fixed: the same code gives the same pixels. The files and steps are written inside the tool.

- To retake: in `electron/`, `xvfb-run -a -s '-screen 0 2400x1400x24' npm run screens` (Linux, Xvfb software rendering).
- Default: the whole window at **1920×1032** CSS px at 100 % — a 1920×1080 lab PC with the window maximised over a 48 px taskbar. No mouse cursor, tooltip or hover (the pointer is moved out of the window and `:hover` is checked to be 0); focus is cleared; every image has loaded.
- Size: 1.5 MB or less each (they are 50–160 KB). Metadata (ancillary PNG chunks) is stripped; lossless. Over the limit, the tool stops.
- The window buttons (minimise, maximise, close) are drawn by the system on the title bar's right end (`titleBarOverlay`), so a page capture has none: their place is empty.
- The Canvas is not drawn yet (item N-05): it says what the circuit holds.

| File | What to look at | How it is shot |
|---|---|---|
| `start.png` | The first screen: Haram & Hari (greeting), 안녕하세요!, the lead in two lines, 튜토리얼 보기 / 바로 시작; no toolbar; New, Open, About at the bar's right end; the status bar: Ready and the engine's versions | As started |
| `start-tutorial.png` | Step 2 of the same card: 논리설계 및 실험 / 컴퓨터구조, "← 처음으로"; the card's box and everything but the choices exactly as in `start.png` (the e2e test compares the pixels) | 튜토리얼 보기 |
| `start-2.png` | Step 2: 새 회로 / 파일 열기 (Ctrl+O) | ← 처음으로, 바로 시작 |
| `new-circuit.png` | A new circuit: the toolbar in the bar (Save Undo Redo · the Canvas tools, off until there is a Canvas · Run, 1 Cycle, N Cycles, Reset, speed · Load Program…), the file and circuit tabs, Components (the file's circuits first, then the engine's library), the empty Canvas's word with Haram & Hari (guiding), Attributes, Tunnels, Messages: every empty panel says what fills it, the words alone; the status bar's facts in English (`main · 0 components · 0 wires`) | 새 회로 |
| `open-file.png` | Two files open (tabs), `demo-datapath.circ` on show: the Canvas says what the circuit holds (35 components, 41 wires), Tunnels by label with counts, the status bar's facts | Ctrl+O → `tests/circ/demo-datapath.circ` |
| `circuit-tabs.png` | Circuits: the file's three, the main one marked with a house icon ("Main circuit"), `regfile` opened as a second circuit tab; 1 Cycle twice: the engine's `sim.state` in the status bar (Cycle 2); Attributes still in its column | Circuits → regfile, 1 Cycle ×2 |
| `about.png` | About: the version; Based on Logisim 2.7.1 by Carl Burch (GNU GPL, version 2 or later); Hallym MIPS Simulator (BSD 3-Clause); the marks are Hallym University's, commercial use prohibited, not an official product; the engine, Electron, Chromium, Node.js | About (ⓘ) |
| `about-licenses.png` | Licenses: every notice, Hallym MIPS's BSD 3-Clause text open | Licenses, the third entry |
| `dialog-error.png` | The window's own dialog for an error, in the window's words (not the engine's): a sentence title, `File: lab3.circ` (the name only), what happened and what to do, one button (Close); **no character anywhere** while it is up (the Canvas's guide is hidden too); the backdrop darkens the window | Ctrl+O → a file that is not there |
| `engine-restarted.png` | The engine ended (a crash) and started again: the band ("엔진이 멈춰서 다시 시작했습니다 · 열려 있던 파일 2개를 닫았습니다"), the first screen with its character hidden while the band says something went wrong | the engine killed from the test |
| `engine-failed.png` | No engine: "엔진을 시작하지 못했습니다" with what was tried — "엔진 파일이 없습니다: hcs-engine.jar" and where (`/opt/hcs/hcs-engine.jar`), Close / Try Again, no character anywhere; the red band behind; the status bar | `HCS_ENGINE_JAR` pointing at nothing |
| `lab-125.png` | A 1920×1080 lab PC at 125 %: CSS 1536×816 drawn at 1.25; three columns | `--force-device-scale-factor=1.25`, the file open |
| `lab-150.png` | At 150 %: CSS 1280×672 drawn at 1.5; the toolbar in its own row under the bar (the bar gives way in steps: key hints, the tools' names; the row takes them again); English names in Korean sentences whole on one line (Attributes: `Data Bits`) | `--force-device-scale-factor=1.5`, the file open |
| `narrow.png` | Half a 1920 screen (960×1032): no right column, Attributes a tab of the left panel (on show); the toolbar in its own row | 960×1032, Attributes tab |

Links to these screenshots (in a report, a PR) have the form
`https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/<commit SHA>/electron/docs/screens/<name>.png`,
pinned to a commit SHA, never to a branch.
