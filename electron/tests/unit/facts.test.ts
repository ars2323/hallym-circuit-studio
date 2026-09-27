/* src/renderer/app/logic/facts.ts: what the window says about a circuit
   and about the engine. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { Component, EngineStatus, Snapshot } from '../../src/main/protocol.ts';
import { circuitFacts, count, counted, engineFact, engineVersion } from '../../src/renderer/app/logic/facts.ts';

const comp = (name: string, attrs: Record<string, string> = {}): Component =>
  ({ id: 'k', lib: 'Wiring', name, loc: [0, 0], bounds: [0, 0, 0, 0], facing: 'east', attrs, ports: [] });

test('circuitFacts: components, wires, and the tunnels by label (numbers in order, unlabelled last)', () => {
  const s: Snapshot = {
    circuitId: 'c1', name: 'main', nets: [], junctions: [],
    wires: [{ id: 'w1', a: [0, 0], b: [10, 0] }],
    components: [comp('Tunnel', { label: 'clk' }), comp('AND Gate'), comp('Tunnel', { label: 'r10' }), comp('Tunnel', { label: 'r2' }),
      comp('Tunnel', { label: 'clk' }), comp('Tunnel')],
  };
  const f = circuitFacts(s);
  assert.deepEqual([f.components, f.wires], [6, 1]);
  assert.deepEqual(f.tunnels, [{ label: 'clk', count: 2 }, { label: 'r2', count: 1 }, { label: 'r10', count: 1 }, { label: '', count: 1 }]);
});

test('engineFact: says nothing while it is ready; why, when it is not', () => {
  const s = (state: EngineStatus['state'], error: string | null = null): EngineStatus => ({ state, generation: 1, hello: null, error, detail: null });
  assert.equal(engineFact(s('ready')), null);
  assert.equal(engineFact(s('starting'))?.text, 'Engine starting');
  assert.equal(engineFact(s('restarting'))?.cls, 'warn');
  assert.deepEqual(engineFact(s('failed', '엔진이 멈췄습니다')), { cls: 'err', text: '엔진이 멈췄습니다' });
  assert.equal(engineVersion(s('ready')), '');
  assert.equal(engineVersion({ ...s('ready'), hello: { engine: 'hcs-engine', version: '2', logisim: '2.7.1', java: '21.0.5' } }), 'Logisim 2.7.1 · Java 21.0.5');
  assert.equal(count(12345), '12,345');
  assert.deepEqual([counted(0, 'wire'), counted(1, 'wire'), counted(1234, 'component')], ['0 wires', '1 wire', '1,234 components']);
});
