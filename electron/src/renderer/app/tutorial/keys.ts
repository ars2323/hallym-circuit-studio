/* The tutorial sees every key press before the window does (N-18, D-161):
   imported by app.ts right after keymap.ts, so that this capture listener
   on the window is the first of its kind -- a key a step does not ask for
   stops here and never reaches the window's own keys (Delete, R, Ctrl+S…),
   and → ← Esc move the tutorial.  With no tutorial on, it does nothing. */

let handler: ((e: KeyboardEvent) => boolean) | null = null;

// The running tutorial's handleKey (null: none running).
export function tutorialKeys(h: ((e: KeyboardEvent) => boolean) | null): void {
  handler = h;
}

window.addEventListener('keydown', (e) => { handler?.(e); }, true);
