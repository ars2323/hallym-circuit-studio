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

## Files

- `character/` — 1417×1417 PNG (1417×1418 for the basic poses), transparent
  background: `haram-hari.png`, `haram.png`, `hari.png` (basic poses) and
  `haram-hari-<pose>.png` (posed variants: greeting, best, ok, guide, go,
  talk, selfie, meal, congrats, notice, no, education, curious, love,
  thanks, moved, sign, holiday, graduation, exercise).
- `logo/` — the symbol (basic form, `symbol-basic.svg`: the logo on the
  window's top bar), the logotype, the emblem and the signature as SVG (and
  PNG renderings), and the application icon (`app-*.png`, `app.ico`).

(From Hallym MIPS v2.3.0 `electron/src/renderer/assets/hallym/README.md`,
rewritten for this repository's file names.)
