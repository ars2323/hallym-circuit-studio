# Hallym University assets

These files belong to Hallym University. They are not covered by this
project's license (LICENSE) or by any license in NOTICE, and they may not be
taken from here and used elsewhere; anyone who forks or redistributes this
project must remove them unless Hallym University has given its own
permission. They appear in this software only to identify it as a teaching
tool for Hallym University's courses. Commercial use is prohibited. This
software is not an official product of Hallym University.

## Rules of use (the university's design guidelines)

- Do not change colours, lines, proportions or elements.
- Minimum size 20 mm (about 76 px).
- Clear space around the character on every side.
- Do not place a character on a colour close to its own or on a busy background.
- Do not use low-resolution or degraded artwork.
- The first screen's video runs behind the card, blurred and under navy;
  the character stands on the card's opaque white, never on the video.

## How this program keeps them

- The files are as received, in the repository's `assets/hallym/`
  (`assets/MANIFEST.sha256` holds their SHA-256, checked in CI). The
  character PNGs are the original bytes; only their file names were changed
  to ASCII. They are not recompressed, resized or recoloured. Where the
  screen shows them smaller, only CSS scales them. They are the same files,
  byte for byte, that Hallym MIPS Simulator uses.
- Characters appear only where there is nothing else to show: the first
  screen, the empty Canvas, the window's own questions. Never next to an
  error, never on the toolbar, panel heads, status bar or menus.
- The first screen's video and its still are Hallym MIPS Simulator's files
  (v2.5.0), byte for byte (`electron/tools/import-hmips.ts` checks them):
  `electron/src/renderer/assets/hallym/start/`. The blur and the navy over
  them are the screen's CSS, not the file's.

## Files

- `character/` — 1417×1417 PNG (1417×1418 for the basic poses), transparent
  background: `haram-hari.png`, `haram.png`, `hari.png` (basic poses) and
  `haram-hari-<pose>.png` (posed variants: greeting, best, ok, guide, go,
  talk, selfie, meal, congrats, notice, no, education, curious, love,
  thanks, moved, sign, holiday, graduation, exercise).
- `logo/` — the symbol (basic form, `symbol-basic.svg`: the logo on the
  window's top bar), the logotype, the emblem and the signature as SVG (and
  PNG renderings), and the application icon (`app-*.png`, `app.ico`).
- `start/` (in `electron/src/renderer/assets/hallym/`) — the first screen's
  background. `start.webm`: the opening aerial shot of the university's
  promotional video, "[Official Video] 한림대학교 홍보영상｜The New Hallym
  대학의 내일을 열다" (official YouTube channel @HALLYMNEWS): 0:00.1–0:02.6,
  slowed to a third, VP9, 960×540, 6.7 s, no sound track; it loops without
  a seam. The one shot of the video with nothing written in it, no graphics
  over it and no cut in it. `start.jpg`: its first frame, shown at once and
  instead of the video when the PC's animation effects are off
  (prefers-reduced-motion). Made by `electron/tools/start-video.ts`.
- The Windows installer's side band (`electron/packaging/installerSidebar.bmp`,
  `uninstallerSidebar.bmp`): the symbol (`logo/symbol-basic.svg`) unaltered
  on a white plate over the app's navy, made by
  `electron/tools/installer-art.py`.

(From Hallym MIPS v2.5.0 `electron/src/renderer/assets/hallym/README.md`,
rewritten for this repository's file names.)
