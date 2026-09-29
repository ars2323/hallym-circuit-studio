/* The changed keys at work (logic/keys.ts, D-158): installed before any
   other key listener of the window (app.ts imports it first), it sees every
   key press first.  A command's new key becomes a press of the command's
   own default key on the same element (each handler -- the Canvas's, the
   editing keys, the overlays', the window's -- keeps reading its own keys);
   a command's old key does nothing.  Nothing is touched while a text field
   has a plain key, nor while no key has been changed. */

import { pressStroke, route, type Stroke } from './logic/keys.ts';

const ours = new WeakSet<Event>();

function typing(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null;
  return !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable);
}

// The press made in the command's place: its default key, Shift for the other way.
export function pressFor(to: Stroke): KeyboardEventInit {
  const letter = to.key.length === 1 && /[a-z]/.test(to.key);
  return {
    key: to.shift && letter ? to.key.toUpperCase() : to.key, code: to.code,
    ctrlKey: to.ctrl === true, shiftKey: to.shift === true, altKey: to.alt === true, metaKey: false,
    bubbles: true, cancelable: true, composed: true,
  };
}

window.addEventListener('keydown', (e) => {
  if (ours.has(e) || e.isComposing || document.body.dataset.keyCapture) return;   // Preferences waits for a new key
  const p = pressStroke(e);
  if (!p) return;
  if (typing(e.target) && !p.ctrl && !p.alt) return;
  const r = route(p);
  if (r.kind === 'pass') return;
  e.preventDefault();
  e.stopImmediatePropagation();
  if (r.kind === 'block') return;
  const again = new KeyboardEvent('keydown', pressFor(r.to));
  ours.add(again);
  (e.target ?? window).dispatchEvent(again);
}, true);
