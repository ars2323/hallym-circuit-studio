/* The caption buttons' patch (derived from Hallym MIPS v2.6.0
   electron/src/renderer/app/app.ts: updateOverlay).  The system draws the
   minimise / maximise / close buttons on a patch at the title bar's right
   end that the page cannot paint (titleBarOverlay).  On the first screen
   (body.first-screen) the bar is dark glass over the photo: the patch is
   transparent and the symbols white, so the buttons sit on the glass with
   no white square (D-169).  Elsewhere, while something darkens the page,
   the patch takes the colour white has under the same layers
   (../shared/overlay.ts): a dialog's backdrop, and the tutorial's dimming.

   The tutorial (N-18) asks for its dimming with shadeCaptions(true) (and
   false when it ends): the page's root gets TUTORIAL_SHADE, and the patch
   follows -- as it follows a dialog opening, both at once when both are. */

import { type CaptionPatch, captionPatch as patchFor, WHITE_PATCH } from '../shared/overlay.ts';

export const TUTORIAL_SHADE = 'data-tutorial-shade';

export function shadeCaptions(on: boolean): void {
  document.documentElement.toggleAttribute(TUTORIAL_SHADE, on);
}

// The patch now: transparent with white symbols on the first screen; else white, or white under what covers the page.
export const patchNow = (): CaptionPatch =>
  patchFor(document.body.classList.contains('first-screen'), document.documentElement.hasAttribute(TUTORIAL_SHADE), document.querySelector('dialog[open]') !== null);

// Keeps the patch with the page (set: the main process's win:overlay).
export function captionPatch(set: (patch: CaptionPatch) => void): void {
  let now = `${WHITE_PATCH.color} ${WHITE_PATCH.symbolColor}`;   // the window's own at its start (src/main/main.ts)
  const update = () => {
    const p = patchNow();
    if (`${p.color} ${p.symbolColor}` === now) return;
    now = `${p.color} ${p.symbolColor}`;
    set(p);
  };
  update();   // the first screen is up before anything is watched
  const watch = new MutationObserver(update);
  watch.observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'open'] });
  watch.observe(document.documentElement, { attributes: true, attributeFilter: [TUTORIAL_SHADE] });
}
