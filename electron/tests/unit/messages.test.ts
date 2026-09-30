/* src/renderer/app/logic/messages.ts: the Messages panel's model -- the
   groups in the engine's order with their counts, the count in words, the
   place line, "show this place", the chosen message kept by id -- and the
   fake engine's diag.* against the real engine's words
   (tests/fixtures/messages.json, tools/diag-fixture.ts). */

import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { copyFileSync, mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test } from 'node:test';

import { EngineClient } from '../../src/main/engine.ts';
import type { DiagCode, DiagList, DiagMessage, OpenResult, Snapshot } from '../../src/main/protocol.ts';
import { GROUPS, groupMessages, keepChosen, messageCount, oscillating, placeLine, revealOf, sentence } from '../../src/renderer/app/logic/messages.ts';

const FAKE = path.join(import.meta.dirname, '../fake-engine/fake-engine.ts');

const msg = (id: string, code: DiagCode, over: Partial<DiagMessage> = {}): DiagMessage => ({
  id, code, kind: 'static', severity: 'error', text: { ko: `${id} 한국어`, en: `${id} English` },
  location: { circuitId: 'c1', root: 'c1', path: [], components: ['k1'], wires: ['w1'], nets: ['n1'], at: [10, 20] },
  ...over,
});

test('groupMessages: by kind in the engine\'s order, the messages in theirs, an unknown kind last as Other', () => {
  const list = [
    msg('d1', 'TUNNEL_UNPAIRED'), msg('d2', 'CLOCK_UNCONNECTED'), msg('d3', 'TUNNEL_UNPAIRED'),
    msg('d4', 'X_WRITE_DATA', { kind: 'dynamic' }), msg('d5', 'SOMETHING_NEW' as DiagCode),
  ];
  const g = groupMessages(list);
  assert.deepEqual(g.map((x) => [x.code, x.name, x.messages.map((m) => m.id)]), [
    ['CLOCK_UNCONNECTED', 'Clock not connected', ['d2']],
    ['TUNNEL_UNPAIRED', 'Tunnel without a pair', ['d1', 'd3']],
    ['X_WRITE_DATA', 'Undefined value written', ['d4']],
    ['OTHER', 'Other', ['d5']],
  ]);
  assert.deepEqual(groupMessages([]), []);
  // Every kind the engine has (docs/engine-api.md) has a name, and every name is English.
  assert.equal(new Set(GROUPS.map((x) => x.code)).size, 14);
  for (const x of GROUPS) assert.match(x.name, /^[A-Za-z][A-Za-z ]+$/);
});

test('messageCount: No messages, 1 message, N messages (English facts)', () => {
  assert.deepEqual([messageCount(0), messageCount(1), messageCount(2), messageCount(1234)],
    ['No messages', '1 message', '2 messages', '1,234 messages']);
});

test('sentence and placeLine: the engine\'s Korean; the circuit, and the cycle for what the simulation found', () => {
  const names = (id: string) => ({ c1: 'main', c2: 'regfile' }[id] ?? id);
  assert.equal(sentence(msg('d1', 'SHORT')), 'd1 한국어');
  assert.equal(placeLine(msg('d1', 'SHORT'), names), 'main');
  const dyn = msg('d2', 'E_APPEARED', { kind: 'dynamic', location: { ...msg('x', 'SHORT').location, circuitId: 'c2', cycle: 1203 } });
  assert.equal(placeLine(dyn, names), 'regfile · Cycle 1,203');
});

test('revealOf: the place to show, a copy (the panel keeps its list), the cycle only for dynamic messages', () => {
  const m = msg('d7', 'X_WRITE_CONTROL', {
    kind: 'dynamic', location: { circuitId: 'c2', root: 'c1', path: ['k9'], components: ['k3', 'k4'], wires: [], nets: ['n2'], at: [100, 200], cycle: 3 },
  });
  const r = revealOf('f1', m);
  assert.deepEqual(r, { fileId: 'f1', messageId: 'd7', circuitId: 'c2', root: 'c1', path: ['k9'], components: ['k3', 'k4'], wires: [], nets: ['n2'], at: [100, 200], cycle: 3 });
  r.components.push('k5');
  assert.deepEqual(m.location.components, ['k3', 'k4']);
  assert.equal(revealOf('f1', msg('d1', 'SHORT', { location: { ...m.location, cycle: 5 } })).cycle, null, 'static: no cycle');
  assert.equal(revealOf('f1', msg('d1', 'SHORT', { location: { ...m.location, at: null } })).at, null);
});

test('keepChosen and oscillating', () => {
  const list = [msg('d1', 'SHORT'), msg('d2', 'OSCILLATION', { kind: 'dynamic' })];
  assert.equal(keepChosen('d2', list), 'd2');
  assert.equal(keepChosen('d9', list), null);
  assert.equal(keepChosen(null, list), null);
  assert.equal(oscillating(list), true);
  assert.equal(oscillating([msg('d1', 'SHORT')]), false);
});

test('the fixture: the real engine\'s messages, well formed, Korean sentences with English names', () => {
  const fx = JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/messages.json'), 'utf8')) as
    Record<string, { static: DiagMessage[]; afterCycles?: DiagMessage[] }>;
  assert.deepEqual(Object.keys(fx).sort(), ['broken-datapath.circ', 'dynamic-oscillation.circ', 'dynamic-x-write-data.circ', 'tutorial-logic.circ', 'tutorial-mips.circ']);
  const all = Object.values(fx).flatMap((e) => [...e.static, ...(e.afterCycles ?? [])]);
  assert.ok(all.length >= 6);
  for (const m of all) {
    assert.ok(GROUPS.some((g) => g.code === m.code), m.code);
    assert.match(m.text.ko, /[가-힣]/, 'a Korean sentence');
    assert.doesNotMatch(m.text.en, /[가-힣]/, 'no Korean in English');
    assert.equal(m.severity, 'error');
    // No Korean particle right after a name the student gave (v2 7: 이름 바로 뒤에 조사를 붙이지 않는다)
    for (const name of ['RegWirte', 'RegWrite', 'PC', 'MemWrite', 'NAND #1', 'next']) {
      assert.doesNotMatch(m.text.ko, new RegExp(`${name.replace('#', '\\#')}(이|가|을|를|은|는|의|에|와|과)`), `${name}: ${m.text.ko}`);
    }
  }
  const near = fx['broken-datapath.circ'].static.find((m) => m.code === 'TUNNEL_UNPAIRED');
  assert.equal(near?.near, 'RegWrite');
  assert.ok(near?.text.ko.endsWith('혹시 RegWrite?'));
});

test('the fake engine\'s diag.*: the fixture\'s list with its own ids, diag.changed after cycles and at Reset, none elsewhere', async () => {
  const engine = new EngineClient({
    client: { client: 'test', version: '0' }, restartDelayMs: 20, helloTimeoutMs: 10_000,
    launch: () => spawn(process.execPath, [FAKE], { stdio: 'pipe' }),
  });
  const changed: DiagList[] = [];
  engine.on('notification', (m, p) => { if (m === 'diag.changed') changed.push(p as DiagList); });
  const waitFor = async (n: number) => { for (let i = 0; i < 200 && changed.length < n; i++) await new Promise((r) => setTimeout(r, 10)); };
  try {
    await engine.start();
    const dir = mkdtempSync(path.join(tmpdir(), 'hcs-diag-'));
    const file = path.join(dir, 'broken-datapath.circ');
    copyFileSync(path.join(import.meta.dirname, '../fixtures/broken-datapath.circ'), file);
    const o = await engine.call<OpenResult>('file.open', { path: file });
    const first = await engine.call<DiagList>('diag.list', { fileId: o.fileId });
    assert.deepEqual(first.messages.map((m) => m.code), ['CLOCK_UNCONNECTED', 'TUNNEL_UNPAIRED']);
    const snap = await engine.call<Snapshot>('model.circuit', { fileId: o.fileId, circuitId: o.main });
    const pc = snap.components.find((c) => c.name === 'Register' && c.attrs.label === 'PC');
    assert.deepEqual(first.messages[0].location.components, [pc?.id]);
    assert.equal(first.messages[0].location.circuitId, o.main);
    await engine.call('sim.cycles', { fileId: o.fileId, n: 2 });
    await waitFor(1);
    assert.deepEqual(changed[0].messages.map((m) => [m.code, m.kind]), [['CLOCK_UNCONNECTED', 'static'], ['TUNNEL_UNPAIRED', 'static'], ['X_WRITE_CONTROL', 'dynamic']]);
    await engine.call('sim.cycles', { fileId: o.fileId, n: 2 });
    await engine.call('sim.reset', { fileId: o.fileId });
    await waitFor(2);
    assert.equal(changed.length, 2, 'once after the clock ran, once at Reset');
    assert.equal(changed[1].messages.length, 2);
    const other = await engine.call<OpenResult>('file.open', { path: path.join(import.meta.dirname, '../../../tests/circ/gates.circ') });
    assert.deepEqual((await engine.call<DiagList>('diag.list', { fileId: other.fileId })).messages, []);
    const t = await engine.call<{ found: boolean }>('trace.origin', { fileId: other.fileId, circuitId: other.main, netId: 'n1' });
    assert.equal(t.found, false);
  } finally {
    await engine.shutdown();
  }
});
