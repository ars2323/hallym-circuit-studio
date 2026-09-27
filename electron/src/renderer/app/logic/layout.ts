/* Where the panels go at a given window size (logic only; app.ts applies it).

     wide     Components/Circuits over Tunnels/Minimap | the files, circuits
              and Canvas over Messages/Cycle View/Console | Attributes
     narrow   (under NARROW_PX CSS px: half a 1920 screen, 1366 at 125 %)
              no right column: Attributes becomes a tab of the left panel,
              next to Components and Circuits

   Widths and heights have defaults that follow the window (a share of it,
   within limits) until the student drags a splitter; a dragged size is
   kept for this run only (nothing is kept on disk: the lab-PC rule), and
   always leaves the Canvas at least CANVAS_LEAST px wide. */

export const NARROW_PX = 1100;
export const CANVAS_LEAST = 360;
export const SPLITTER = 8;          // a splitter's width (app.css grid)
export const HEAD = 34;             // a panel head's height (shared.css --head)
export const PAD = 8;               // the work area's padding (app.css .shell)

export interface Dragged {
  left: number | null;
  right: number | null;
  bottom: number | null;
  lower: number | null;
}
export const nothingDragged = (): Dragged => ({ left: null, right: null, bottom: null, lower: null });

export interface Arrangement {
  narrow: boolean;          // Attributes in the left panel's tabs, no right column
  left: number;             // px, the left column
  right: number;            // px, the right column (0 when narrow)
  bottom: number;           // px, the bottom panel (its head only while collapsed)
  lower: number;            // px, the left column's lower panel (Tunnels, Minimap)
}

const clamp = (v: number, lo: number, hi: number) => Math.round(Math.max(lo, Math.min(Math.max(lo, hi), v)));

// width, height: the work area's (the window without its title bar, tool row and status bar).
export function arrange(width: number, height: number, dragged: Dragged, bottomCollapsed: boolean): Arrangement {
  const narrow = width < NARROW_PX;
  let left = clamp(dragged.left ?? width * 0.15, 180, width * 0.4);
  let right = narrow ? 0 : clamp(dragged.right ?? width * 0.16, 200, width * 0.4);
  // Narrow, the left panel holds three tabs (Components, Circuits, Attributes): 280 px at least.
  if (dragged.left === null) left = narrow ? clamp(width * 0.3, 280, 320) : clamp(left, 220, 300);
  if (dragged.right === null && !narrow) right = clamp(right, 240, 320);
  // The Canvas keeps its least: the right column gives way first, then the left.
  const room = width - 2 * PAD - SPLITTER * (narrow ? 1 : 2) - CANVAS_LEAST;
  if (left + right > room) {
    if (!narrow) right = Math.max(200, room - left);
    if (left + right > room) left = Math.max(180, room - right);
  }
  const inner = height - 2 * PAD;
  const bottom = bottomCollapsed ? HEAD + 2 : clamp(dragged.bottom ?? inner * 0.26, HEAD + 60, inner - HEAD * 2 - 120);
  const lower = clamp(dragged.lower ?? inner * 0.32, HEAD + 60, inner - SPLITTER - HEAD - 120);
  return { narrow, left, right, bottom, lower };
}
