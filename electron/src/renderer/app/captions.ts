/* The caption buttons' patch (derived from Hallym MIPS v2.5.0
   electron/src/renderer/app/app.ts: its overlay colour).  The system draws
   the minimise / maximise / close buttons on a patch at the title bar's
   right end that the page cannot paint (titleBarOverlay).  While something
   darkens the page, the patch takes the colour white has under the same
   layers (../shared/overlay.ts), so it does not stand out as a bright
   square: a dialog's backdrop, and the tutorial's dimming.

   The tutorial (N-18) asks for its dimming with shadeCaptions(true) (and
   false when it ends): the page's root gets TUTORIAL_SHADE, and the patch
   follows -- as it follows a dialog opening, both at once when both are. */

import { overlayColor } from '../shared/overlay.ts';

export const TUTORIAL_SHADE = 'data-tutorial-shade';

export function shadeCaptions(on: boolean): void {
  document.documentElement.toggleAttribute(TUTORIAL_SHADE, on);
}

// The patch's colour now: white, or white under what covers the page.
export const patchColor = (): string =>
  overlayColor(document.documentElement.hasAttribute(TUTORIAL_SHADE), document.querySelector('dialog[open]') !== null);

// Keeps the patch's colour with the page (set: the main process's win:overlay; null is white).
export function captionPatch(set: (color: string | null) => void): void {
  let now = '#ffffff';
  const update = () => {
    const c = patchColor();
    if (c === now) return;
    now = c;
    set(c === '#ffffff' ? null : c);
  };
  const watch = new MutationObserver(update);
  watch.observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'open'] });
  watch.observe(document.documentElement, { attributes: true, attributeFilter: [TUTORIAL_SHADE] });
}
