/* The overlays' words (N-15, D-151): facts are names, in English (the
   status bar: `Influence: Forward · 3 steps`); a sentence to the student is
   Korean, with no particle right after a name (`[ ] 키로 …`, `Esc 키로 …`). */

import type { InfluenceResult } from '../../../main/protocol.ts';

export const MODE_NAMES: Record<InfluenceResult['mode'], string> = { forward: 'Forward', backward: 'Backward', both: 'Both', between: 'Path Between' };

// v1 influence.notice without its sentence: the direction, the steps shown, through registers.
export function influenceStatus(r: Pick<InfluenceResult, 'mode' | 'depth' | 'throughRegisters'>): string {
  const steps = r.depth < 0 ? 'all steps' : `${r.depth} ${r.depth === 1 ? 'step' : 'steps'}`;
  return `Influence: ${MODE_NAMES[r.mode]} · ${steps}${r.throughRegisters ? ' · through registers' : ''}`;
}

// The keys do nothing while the student types (a value, a label, the search).
export function typing(t: EventTarget | null): boolean {
  if (typeof HTMLElement === 'undefined' || !(t instanceof HTMLElement)) return false;
  return t.isContentEditable || t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT';
}
