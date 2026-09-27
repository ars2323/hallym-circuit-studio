/* What the window says about a circuit and about the engine: facts only,
   counted from what the engine sent (logic only). */

import type { EngineStatus, Snapshot } from '../../../main/protocol.ts';

export interface CircuitFacts {
  components: number;
  wires: number;
  tunnels: { label: string; count: number }[];   // by label, sorted; unlabelled ones as ''
}

export function circuitFacts(s: Snapshot): CircuitFacts {
  const tunnels = new Map<string, number>();
  for (const c of s.components) {
    if (c.name !== 'Tunnel') continue;
    const label = c.attrs.label ?? '';
    tunnels.set(label, (tunnels.get(label) ?? 0) + 1);
  }
  return {
    components: s.components.length,
    wires: s.wires.length,
    tunnels: [...tunnels].map(([label, count]) => ({ label, count }))
      .sort((a, b) => (a.label === '' ? 1 : b.label === '' ? -1 : a.label.localeCompare(b.label, 'en', { numeric: true }))),
  };
}

// The engine's state in the status bar (null: nothing to say).
export function engineFact(s: EngineStatus): { cls: '' | 'err' | 'warn'; text: string } | null {
  switch (s.state) {
    case 'starting': return { cls: '', text: '엔진 시작 중' };
    case 'restarting': return { cls: 'warn', text: '엔진 다시 시작 중' };
    case 'failed': return { cls: 'err', text: s.error ?? '엔진을 시작하지 못했습니다' };
    case 'stopped': return { cls: 'err', text: '엔진이 꺼져 있습니다' };
    default: return null;
  }
}

// The engine's own versions, at the status bar's right end.
export const engineVersion = (s: EngineStatus): string =>
  (s.hello ? `Logisim ${s.hello.logisim} · Java ${s.hello.java}` : '');

// "1,234": counts in the status bar and the empty states.
export const count = (n: number): string => n.toLocaleString('en-US');
