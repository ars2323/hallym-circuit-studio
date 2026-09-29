/* Where the panels go at a given window size (logic only; app.ts applies it).

     wide     Components/Circuits over Tunnels/Minimap | the files, circuits
              and Canvas over Messages/Cycle View/Console | Attributes
     narrow   (under NARROW_PX CSS px: half a 1920 screen, 1366 at 125 %)
              no right column: Attributes becomes a tab of the left panel,
              next to Components and Circuits
     tight    (under TIGHT_PX: half a 1366 screen, 683 px, and a 1920 screen
              at 250 %) one side at a time, as Hallym MIPS's narrow window
              shows its Editor or its Run side: the title bar's Canvas /
              Panels switch (app.ts) shows the Canvas with its bottom panel,
              or the left column the whole width

   Widths (v1 X-03, D-107): the Canvas keeps half the work area, and never
   less than CANVAS_LEAST; the right column gives way first, then the left.
   Heights (v1 Y-01, D-112): the Canvas keeps half the height too.  The
   bottom panel (Messages, Cycle View, Console) is the student's height or
   its default share, cut down to leave the Canvas its half; if that leaves
   it under BOTTOM_FOLD it folds to its head by itself (a tab pressed opens
   it again, and it stays open until the window's size changes: `opened`).
   The left column's lower panel (Tunnels, Minimap) does the same under the
   upper one's half (LOWER_FOLD).  A fold the window made is not the
   student's Collapse: the window unfolds it again when there is room.

   Sizes follow the window (a share of it, within limits) until the student
   drags a splitter; a dragged size is kept for this run only (nothing is
   kept on disk: the lab-PC rule). */

export const NARROW_PX = 1100;
export const TIGHT_PX = 800;
export const CANVAS_LEAST = 360;
export const SPLITTER = 8;          // a splitter's width (app.css grid)
export const HEAD = 34;             // a panel head's height (shared.css --head)
export const PAD = 8;               // the work area's padding (app.css .shell)
export const BOTTOM_FOLD = 130;     // v1 D-112: the tab row, the Cycle View's bar and three rows
export const LOWER_FOLD = HEAD + 70; // v1 D-112: 70 px of the Tunnels list

export interface Dragged {
  left: number | null;
  right: number | null;
  bottom: number | null;
  lower: number | null;
}
export const nothingDragged = (): Dragged => ({ left: null, right: null, bottom: null, lower: null });

// What the student did to the folds: the bottom panel's Collapse, and a tab pressed on a panel the window folded.
export interface Folds {
  collapsed: boolean;       // the bottom panel's Collapse (the student's)
  bottomOpened: boolean;    // a bottom tab pressed while the window had folded it: open until the size changes
  lowerOpened: boolean;     // the same for Tunnels / Minimap
}
export const noFolds = (): Folds => ({ collapsed: false, bottomOpened: false, lowerOpened: false });

export interface Arrangement {
  narrow: boolean;          // Attributes in the left panel's tabs, no right column
  tight: boolean;           // one side at a time (the Canvas or the panels)
  left: number;             // px, the left column (tight: the whole width)
  right: number;            // px, the right column (0 when narrow)
  bottom: number;           // px, the bottom panel (its head only while folded)
  lower: number;            // px, the left column's lower panel (its head only while folded)
  bottomFolded: boolean;    // the bottom panel is its head only, by the student's Collapse or for the room
  bottomAuto: boolean;      // ... and it was the window's doing (for the room)
  lowerFolded: boolean;     // the lower left panel is its head only (always the window's doing)
}

const clamp = (v: number, lo: number, hi: number) => Math.round(Math.max(lo, Math.min(Math.max(lo, hi), v)));
const FOLDED = HEAD + 2;

// The Canvas's least width at this window width: half of it (v1 X-03), never under CANVAS_LEAST.
export const canvasLeast = (width: number): number => Math.max(CANVAS_LEAST, Math.round(width / 2));

// width, height: the work area's (the window without its title bar, bands and status bar).
export function arrange(width: number, height: number, dragged: Dragged, folds: Folds = noFolds()): Arrangement {
  const tight = width < TIGHT_PX;
  const narrow = width < NARROW_PX;
  let left: number;
  let right = 0;
  if (tight) {
    left = Math.max(0, Math.round(width - 2 * PAD));
  } else {
    left = clamp(dragged.left ?? width * 0.15, 180, width * 0.4);
    right = narrow ? 0 : clamp(dragged.right ?? width * 0.16, 200, width * 0.4);
    // Narrow, the left panel holds three tabs (Components, Circuits, Attributes): 280 px at least.
    if (dragged.left === null) left = narrow ? clamp(width * 0.3, 280, 320) : clamp(left, 220, 300);
    if (dragged.right === null && !narrow) right = clamp(right, 240, 320);
    // The Canvas keeps half the width: the right column gives way first, then the left.
    const room = width - 2 * PAD - SPLITTER * (narrow ? 1 : 2) - canvasLeast(width);
    if (left + right > room) {
      if (!narrow) right = Math.max(200, room - left);
      if (left + right > room) left = Math.max(180, room - right);
    }
  }
  const inner = height - 2 * PAD;
  const half = Math.floor((inner - SPLITTER) / 2);   // the Canvas's (and the upper left panel's) least
  // The bottom panel: the student's or the default, leaving the Canvas half the height; folded when that is too little.
  const wantBottom = dragged.bottom ?? inner * 0.26;
  const bottomMost = inner - SPLITTER - half;
  let bottom = clamp(wantBottom, BOTTOM_FOLD, bottomMost);
  let bottomFolded = folds.collapsed;
  let bottomAuto = false;
  if (!bottomFolded && bottomMost < BOTTOM_FOLD) {
    if (folds.bottomOpened) bottom = clamp(wantBottom, BOTTOM_FOLD, inner - SPLITTER - HEAD - 60);
    else { bottomFolded = true; bottomAuto = true; }
  }
  if (bottomFolded) bottom = FOLDED;
  // The lower left panel: the same under the upper one's half.
  const wantLower = dragged.lower ?? inner * 0.32;
  const lowerMost = inner - SPLITTER - half;
  let lower = clamp(wantLower, LOWER_FOLD, lowerMost);
  let lowerFolded = false;
  if (lowerMost < LOWER_FOLD) {
    if (folds.lowerOpened) lower = clamp(wantLower, LOWER_FOLD, inner - SPLITTER - HEAD - 60);
    else { lowerFolded = true; lower = FOLDED; }
  }
  // whole pixels: a panel edge at a fraction is drawn a shade differently from one run to the next (D-158 16)
  const px = Math.round;
  return { narrow, tight, left: px(left), right: px(right), bottom: px(bottom), lower: px(lower), bottomFolded, bottomAuto, lowerFolded };
}
