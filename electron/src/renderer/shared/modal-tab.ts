/* Tab stays in a modal dialog (D-164).  showModal() makes the rest of the
   page inert, but Chromium still lets Tab go past a dialog's last control
   (Shift+Tab: before its first): focus leaves the page -- the window loses
   it (blur) -- and comes back to the dialog's first control only a moment
   later, asynchronously.  In a dialog with one button (an error's Close)
   every Tab did that.  So at the two ends Tab goes round itself: past the
   last control to the first, before the first to the last, and with no
   control nowhere; in between Chromium's own order is kept. */

// The control to focus for a Tab (back: Shift+Tab) among `count` controls, focus at `at` (-1: not on one of them);
// null: Chromium's own move (it stays inside).
export function tabWithin(count: number, at: number, back: boolean): number | null {
  if (count === 0) return -1;                      // nothing to go to: stay (the caller keeps the focus where it is)
  if (at < 0) return back ? count - 1 : 0;
  if (!back && at === count - 1) return 0;
  if (back && at === 0) return count - 1;
  return null;
}

const CONTROLS = 'button, [href], input, select, textarea, summary, [tabindex]';

// The controls Tab reaches in `root`, in Tab's order for the usual case (no positive tabindex in dialogs).
export function tabbable(root: Element): HTMLElement[] {
  return [...root.querySelectorAll<HTMLElement>(CONTROLS)].filter((el) =>
    el.tabIndex >= 0 && !(el as HTMLButtonElement).disabled && !el.closest('[inert]') && el.getClientRects().length > 0
    && !(el instanceof HTMLInputElement && el.type === 'hidden'));
}

// The topmost modal dialog (the last one opened is the last in the top layer; dialogs are appended to the body).
function topModal(doc: Document): HTMLDialogElement | null {
  const open = [...doc.querySelectorAll<HTMLDialogElement>('dialog[open]')].filter((d) => d.matches(':modal'));
  return open.at(-1) ?? null;
}

export function keepTabInModal(doc: Document = document): void {
  doc.addEventListener('keydown', (e) => {
    if (e.key !== 'Tab' || e.ctrlKey || e.altKey || e.metaKey) return;
    const dialog = topModal(doc);
    if (!dialog) return;
    const items = tabbable(dialog);
    const next = tabWithin(items.length, items.indexOf(doc.activeElement as HTMLElement), e.shiftKey);
    if (next === null) return;
    e.preventDefault();
    if (next >= 0) items[next].focus();
  }, true);
}
