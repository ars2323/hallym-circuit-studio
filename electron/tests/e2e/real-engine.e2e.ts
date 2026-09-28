/* The window with the real engine (engine/, N-03) instead of the fake one:
   the same flows end to end -- hello, a new circuit, a .circ with the MIPS
   library, the clock, saving, a crash and the unsaved edits replayed
   (N-04), no orphan java, quitting.  Opt-in, as it needs the engine built
   and a Java 21:

     ./gradlew :engine:stage :engine:runtime    (engine/build/stage: the jars; engine/build/runtime: the bundled JRE)
     HCS_E2E_REAL_ENGINE=1 HCS_JAVA=$PWD/../engine/build/runtime/bin/java xvfb-run -a npx playwright test real-engine

   CI's runtime job runs it with the bundled runtime (its AppCDS archive too). */

import { expect, test } from '@playwright/test';
import { copyFileSync, existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import type { Snapshot } from '../../src/main/protocol.ts';
import { answerOpen, answerSave, canvasSettled, DATAPATH, INSIDE_PIN_VALUES, launch, newCircuit, openFile, PARENT_PORT_VALUES, repo, sample, type LaunchOptions } from './harness.ts';
import { alive, call, circuitsOf, enginePid, fileModel, journalLength, killEngine, killMainAndSeeEngineEnd, openFileIds } from './model.ts';
import { click, menu, opened, partMiddle, rightClick, shown, wireAtPort } from './overlay-helpers.ts';
import { changeEverySetting, settingsNow } from './settings.ts';

const JAR = path.join(repo, 'engine/build/stage/hcs-engine.jar');
const real: LaunchOptions['env'] = { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: JAR };

test.skip(process.env.HCS_E2E_REAL_ENGINE !== '1', 'opt-in: HCS_E2E_REAL_ENGINE=1 with engine/build/stage built');

test('the real engine: hello, a new circuit, a .circ with the MIPS library, the clock, saving', async () => {
  expect(existsSync(JAR), `${JAR}: ./gradlew :engine:stage`).toBe(true);
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await expect(page.locator('.status .engine')).toContainText('Logisim 2.7.1 · Java 21');
    await newCircuit(r);
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ']);
    await expect(page.locator('.canvas h3')).toHaveText('빈 회로입니다');
    await expect(page.locator('.upper .libgroup summary').first()).toContainText('untitled.circ');
    await openFile(r, sample(r.dir, DATAPATH));
    await expect(page.locator('.canvas .canvas-view canvas')).toBeVisible(); // drawn (N-05)
    await expect(page.locator('.status')).toContainText('35 components');
    await page.getByRole('tab', { name: 'Circuits' }).click();
    await expect(page.locator('.upper .pbody:visible .list > li')).toHaveText(['main', 'regfile', 'alu']);
    await expect(page.locator('.lower .list li').first()).toBeVisible();   // its tunnels
    // The clock (docs/engine-api.md sim.*): the engine's sim.state in the status bar.
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 1');
    await page.keyboard.press('F5');
    await expect(page.locator('.status .run')).toHaveText('Running (1 Hz)');
    await page.keyboard.press('F5');
    await expect(page.locator('.status .run')).toHaveCount(0);
    await page.getByRole('button', { name: /Reset/ }).click();
    await expect(page.locator('.status')).toContainText('Cycle 0');
    // Save the new circuit: Logisim's own writer, through the engine.
    await page.locator('.filebar .ptab', { hasText: 'untitled.circ' }).click();
    const target = path.join(r.dir, 'saved.circ');
    await answerSave(r.app, target);
    await page.keyboard.press('Control+s');
    await expect(page.locator('.status .ok')).toContainText('저장했습니다 · saved.circ');
    expect(statSync(target).size).toBeGreaterThan(100);
  } finally {
    await r.close();
  }
});

test('the real engine: a broken circuit\'s Messages (N-13), the same words as the fake engine\'s fixture, and the one the clock finds', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    const fixture = JSON.parse(readFileSync(path.join(repo, 'electron/tests/fixtures/messages.json'), 'utf8')) as
      Record<string, { static: { text: { ko: string } }[]; afterCycles: { text: { ko: string } }[] }>;
    await openFile(r, sample(r.dir, 'electron/tests/fixtures/broken-datapath.circ'));
    await expect(page.locator('.msg .say')).toHaveText(fixture['broken-datapath.circ'].static.map((m) => m.text.ko));
    await expect(page.locator('.status .msgcount')).toHaveText('2 messages');
    await page.keyboard.press('F10'); // the fixture's one cycle
    await expect(page.locator('.status')).toContainText('Cycle 1');
    await expect(page.locator('.msg .say')).toHaveText(fixture['broken-datapath.circ'].afterCycles.map((m) => m.text.ko));
    await expect(page.locator('.msg[data-kind="dynamic"] .where')).toHaveText('main · Cycle 0');
    await page.locator('.msg').first().click();
    await expect(page.locator('.msg.on')).toHaveCount(1);
    // the one the clock found: the Cycle View at cycle 0 with the cause pinned (N-14, V-03)
    await page.locator('.msg[data-kind="dynamic"]').click();
    await expect(page.getByRole('tab', { name: 'Cycle View' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('.cbar .cpos')).toHaveText('Cycle 0 / 1');
    const temp = page.locator('.ctable tr.crow.temp');
    await expect(temp.first().locator('.rname')).toHaveText('MemWrite');
    await expect(temp.first().locator('td.pin')).toHaveAttribute('data-cycle', '0');
    await page.getByRole('button', { name: /Reset/ }).first().click();
    await expect(page.locator('.msg')).toHaveCount(2);
    await expect(temp).toHaveCount(0);
  } finally {
    await r.close();
  }
});

// A Hallym MIPS v2.4.0 golden (tests/hmx/hallym-mips-v2.4.0/data.hmx) in the reference CPU, the way Load
// Program puts it (N-16 is the command): .text into the Instruction Memory, .data (bytes, little-endian) into the
// Data Memory, the image's name in both parts' source (the labels).
function hcsWords(text: string, section: '.text' | '.data'): string {
  const out = ['hcs-words 1'];
  const lines = text.split(/\r?\n/);
  const at = lines.findIndex((l) => l.startsWith(section));
  let addr = parseInt(lines[at].split(/\s+/)[1], 16);
  const bytes: number[] = [];
  for (const l of lines.slice(at + 1)) {
    if (l.startsWith('.') || l.trim() === '') break;
    if (section === '.text') { out.push(`${addr.toString(16).padStart(8, '0')} ${l.trim()}`); addr += 4; } else bytes.push(...l.trim().split(/\s+/).map((b) => parseInt(b, 16)));
  }
  for (let i = 0; i < bytes.length; i += 4) {
    const w = (bytes[i] | (bytes[i + 1] ?? 0) << 8 | (bytes[i + 2] ?? 0) << 16 | (bytes[i + 3] ?? 0) << 24) >>> 0;
    out.push(`${(addr + i).toString(16).padStart(8, '0')} ${w.toString(16).padStart(8, '0')}`);
  }
  return `${out.join('\n')}\n`;
}

test('the real engine: ref-mips with data.hmx, run to exit -- the Registers and Memory panels show the oracle\'s values', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    const golden = path.join(repo, 'tests/hmx/hallym-mips-v2.4.0');
    const circ = path.join(r.dir, 'ref-mips.circ');
    copyFileSync(path.join(repo, 'tests/mips/ref-mips.circ'), circ);
    copyFileSync(path.join(golden, 'data.hmx'), path.join(r.dir, 'data.hmx'));
    await openFile(r, circ);
    const hmx = readFileSync(path.join(golden, 'data.hmx'), 'utf8');
    await page.evaluate(async ({ text, data }) => {
      const app = (window as unknown as { app: { call(m: string, p: unknown): Promise<unknown> } }).app;
      const s = await app.call('model.circuit', { fileId: 'f1', circuitId: 'c1' }) as { components: { id: string; name: string }[] };
      const id = (n: string) => s.components.find((c) => c.name === n)!.id;
      const set = (n: string, attr: string, value: string) => app.call('edit.setAttr', { fileId: 'f1', circuitId: 'c1', ids: [id(n)], attr, value });
      await set('Instruction Memory', 'contents', text);
      await set('Data Memory', 'contents', data);
      await set('Instruction Memory', 'source', 'data.hmx');
      await set('Data Memory', 'source', 'data.hmx');
      await app.call('sim.reset', { fileId: 'f1' });
    }, { text: hcsWords(hmx, '.text'), data: hcsWords(hmx, '.data') });
    await page.locator('section.bottom').getByRole('tab', { name: 'Cycle View' }).click();
    // Run Until: Halt or Exit (the Console's exit, cycle 33 -- the engine's RecordTest has the same)
    await page.getByRole('button', { name: 'Run Until…' }).click();
    await page.locator('dialog.ask').getByLabel('Condition').selectOption({ label: 'Halt or Exit' });
    await page.locator('dialog.ask').getByRole('button', { name: 'Run' }).click();
    await expect(page.locator('.status .ok')).toHaveText('사이클 33에서 멈췄습니다: halt 또는 exit', { timeout: 30_000 });
    await expect(page.locator('.cbar .cpos')).toHaveText('Cycle 33');
    // Registers = tests/hmx/hallym-mips-v2.4.0/data.regs for what the program writes
    await page.locator('.cside').getByRole('tab', { name: 'Registers' }).click();
    const reg = (name: string) => page.locator(`.cside .rrow[data-reg="${name}"] .hex`);
    await expect(reg('$t0')).toHaveText('0x0000000e');
    await expect(reg('$t1')).toHaveText('0xffffffff');
    await expect(reg('$s0')).toHaveText('0x10010018');
    await expect(reg('$a0')).toHaveText('0x0000000e');
    await expect(reg('$v0')).toHaveText('0x0000000a');
    await expect(reg('$at')).toHaveText('0x10010000');
    await expect(page.locator('.cside .rrow[data-reg="$t1"] .dec')).toHaveText('-1');
    // Memory: .data from 0x10010000 with the image's labels; the rest of the region as one row
    await page.locator('.cside').getByRole('tab', { name: 'Memory' }).click();
    const first = page.locator('.cside .data .drow').first();
    await expect(first.locator('.daddr')).toHaveText('0x10010000');
    await expect(first.locator('.dval')).toHaveText(['206d7573', '0000203d', '00000003', '00000005']);
    await expect(first.locator('.dascii')).toContainText('sum =');
    await expect(page.locator('.cside .data .dtags').first()).toContainText('msg');
    await expect(page.locator('.cside .data .dtags').first()).toContainText('nums');
    await expect(page.locator('.cside .data .dzero').first()).toContainText('all 0');
    // Instruction: cycle 32 was the exit's syscall
    await page.locator('.cbar').getByRole('button', { name: 'Previous Cycle' }).click();
    await page.locator('.cside').getByRole('tab', { name: 'Instruction' }).click();
    await expect(page.locator('.cside .insp .ihead .dis')).toHaveText('syscall');
    await expect(page.locator('.cside .insp .fbox .fname')).toHaveText(['opcode', 'rs', 'rt', 'rd', 'shamt', 'funct']);
    await expect(page.locator('.status')).toContainText('Cycle 32 / 33');
  } finally {
    await r.close();
  }
});

test('the real engine: Load Program puts data.hmx into ref-mips; N Cycles to the exit; the Console says what SPIM said; after a crash the program is back (N-16)', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    const golden = 'tests/hmx/hallym-mips-v2.4.0';
    const circ = sample(r.dir, 'tests/mips/ref-mips.circ');
    sample(r.dir, `${golden}/data.s`);
    const hmx = sample(r.dir, `${golden}/data.hmx`);
    await openFile(r, circ);
    await answerOpen(r.app, hmx);
    await page.getByRole('button', { name: /Load Program/ }).click();
    const d = page.locator('dialog.loadsummary');
    await expect(d.locator('table.summary tr').first().locator('td')).toHaveText('0x00400024 (main)');
    await expect(d.locator('table.summary tr', { hasText: 'Source' }).locator('td')).toHaveText('원본 파일 data.s: 내보낸 때와 같음.');
    await d.getByRole('button', { name: 'OK' }).click();
    await expect(page.locator('.status .progfact').first()).toHaveText('Program data.hmx');
    // N Cycles (the toolbar's count is N-07's): the engine's sim.cycles for the one open file
    await r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { call(m: string, p: unknown): Promise<unknown> } } })
      .__hcs.engine.call('sim.cycles', { fileId: 'f1', n: 80 }));
    await expect(page.locator('.status')).toContainText('Cycle 80', { timeout: 30_000 });
    await page.getByRole('tab', { name: 'Console' }).click();
    // The oracle: SPIM's console for the same program (tests/hmx/hallym-mips-v2.4.0/data.regs)
    const oracle = /^console "(.*)"$/m.exec(readFileSync(path.join(repo, golden, 'data.regs'), 'utf8'))![1];
    await expect(page.locator('pre.consoletext')).toHaveText(`${oracle}\n-- exit --\n`);
    // The engine ends: the file comes back with the program (the journal replays mips.load, D-142), from Reset
    await r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { kill(): void } } }).__hcs.engine.kill());
    await page.locator('dialog.ask').getByRole('button', { name: 'Close' }).click();
    await expect(page.locator('.status .progfact').first()).toHaveText('Program data.hmx');
    const facts = await r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { call(m: string, p: unknown): Promise<unknown> } } })
      .__hcs.engine.call('mips.facts', { fileId: 'f1' })) as { program: { memories: { kind: string; text: string }[] } };
    expect(facts.program.memories.find((m) => m.kind === 'text')?.text).toBe('27 words (0x00400000–0x00400068), entry 0x00400024');
    await expect(page.locator('.pbody.bottom:visible .notice h3')).toHaveText('아직 출력이 없습니다');
  } finally {
    await r.close();
  }
});

test('the real engine: its file errors in the window\'s words (a file that is not there, a file Logisim cannot read)', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    const dialog = page.locator('dialog.ask');
    await answerOpen(r.app, path.join(r.dir, 'lab3.circ'));
    await page.keyboard.press('Control+o');
    await expect(dialog.locator('.askfile')).toHaveText('File: lab3.circ');
    await expect(dialog).toContainText('그 자리에 파일이 없습니다.');
    expect(await dialog.innerText()).not.toContain(r.dir);
    await dialog.getByRole('button', { name: 'Close' }).click();
    const notCirc = path.join(r.dir, 'notes.circ');
    writeFileSync(notCirc, 'not a circuit\n');
    await answerOpen(r.app, notCirc);
    await page.keyboard.press('Control+o');
    await expect(dialog.locator('.askfile')).toHaveText('File: notes.circ');
    await expect(dialog).toContainText('Logisim이 이 파일을 회로로 읽지 못했습니다.');
  } finally {
    await r.close();
  }
});

test('the real engine ended by a crash: started again, the file back in its tab, the dialog and the band', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    const file = sample(r.dir, DATAPATH);
    await openFile(r, file);
    await killEngine(r.app);
    await expect(page.locator('dialog.ask h2')).toHaveText('엔진이 멈췄다가 다시 시작했습니다');
    await expect(page.locator('dialog.ask .askdetail')).toContainText('다시 엶: demo-datapath.circ');
    await page.locator('dialog.ask').getByRole('button', { name: 'Close' }).click();
    await expect(page.locator('.band')).toHaveText('엔진이 멈춰서 다시 시작했습니다 · 파일 1개를 되살렸습니다 · 시뮬레이션은 Reset 상태입니다');
    await expect(page.locator('.filebar .ptab')).toHaveText(['demo-datapath.circ']);
    await expect(page.locator('.canvas .canvas-view canvas')).toBeVisible(); // drawn again with the new engine's ids (N-05)
    await page.waitForFunction(() => ((window as unknown as { __hcsCanvas: { scene: { values: Map<string, string> } | null } }).__hcsCanvas.scene?.values.size ?? 0) > 0);
    await expect(page.locator('.status .engine')).toContainText('Logisim 2.7.1 · Java 21');
  } finally {
    await r.close();
  }
});

test('the real engine killed after edits: the replayed model equals the one before, in every circuit of both files', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await page.getByTitle('New circuit (Ctrl+N)').click();
    await expect(page.locator('.filebar .ptab')).toHaveCount(2);
    const [datapath, untitled] = await openFileIds(r.app);
    const dc = await circuitsOf(page, datapath);
    const uc = await circuitsOf(page, untitled);
    // Logisim's own editing: a part placed and changed, a part moved with its wires, a wire drawn,
    // taken back and done again, a part deleted; in the new file, two pins and the wire that joins them.
    const and = await call<{ id: string }>(page, 'edit.addComponent', { fileId: datapath, circuitId: dc.alu, lib: 'Gates', name: 'AND Gate', loc: [600, 600] });
    await call(page, 'edit.setAttr', { fileId: datapath, circuitId: dc.alu, ids: [and.id], attr: 'inputs', value: '3' });
    const main = await call<Snapshot>(page, 'model.circuit', { fileId: datapath, circuitId: dc.main });
    const tunnel = main.components.find((c) => c.name === 'Tunnel')!;
    await call(page, 'edit.move', { fileId: datapath, circuitId: dc.main, ids: [tunnel.id], dx: 0, dy: 10 });
    await call(page, 'edit.addWire', { fileId: datapath, circuitId: dc.main, points: [[1000, 1000], [1100, 1000]] });
    await call(page, 'edit.undo', { fileId: datapath, circuitId: dc.main });
    await call(page, 'edit.redo', { fileId: datapath, circuitId: dc.main });
    const alu = await call<Snapshot>(page, 'model.circuit', { fileId: datapath, circuitId: dc.alu });
    const victim = alu.components.find((c) => c.name !== 'AND Gate' && c.name !== 'Pin')!;
    await call(page, 'edit.delete', { fileId: datapath, circuitId: dc.alu, ids: [victim.id] });
    await call(page, 'edit.addComponent', { fileId: untitled, circuitId: uc.main, lib: 'Wiring', name: 'Pin', loc: [100, 100] });
    await call(page, 'edit.addComponent', { fileId: untitled, circuitId: uc.main, lib: 'Wiring', name: 'Pin', loc: [300, 100], attrs: { facing: 'west', output: 'true' } });
    await call(page, 'edit.addWire', { fileId: untitled, circuitId: uc.main, points: [[100, 100], [300, 100]] });
    expect(await journalLength(r.app, datapath)).toBe(7);
    expect(await journalLength(r.app, untitled)).toBe(3);
    const before = { [datapath]: await fileModel(page, datapath), [untitled]: await fileModel(page, untitled) };
    // The edits took: the gate with three inputs, the new file's two pins joined by one wire.
    expect(JSON.stringify(before[datapath])).toContain('[\\"inputs\\",\\"3\\"]');
    expect((before[untitled].main as { comps: string[]; wires: string[] }).comps).toHaveLength(2);
    expect((before[untitled].main as { comps: string[]; wires: string[] }).wires).toEqual(['100,100-300,100']);

    const pid = await killEngine(r.app);
    await expect(page.locator('dialog.ask')).toContainText('열려 있던 파일 2개를 다시 열고 저장하지 않은 편집 10개를 다시 적용했습니다.');
    await page.locator('dialog.ask').getByRole('button', { name: 'Close' }).click();
    expect(alive(pid)).toBe(false);
    expect(await openFileIds(r.app)).toEqual([datapath, untitled]);
    expect({ [datapath]: await fileModel(page, datapath), [untitled]: await fileModel(page, untitled) }).toEqual(before);
    expect((await call<{ dirty: boolean }>(page, 'file.dirty', { fileId: datapath })).dirty).toBe(true);
    await expect(page.locator('.filebar .ptab')).toHaveText(['demo-datapath.circ•', 'untitled.circ•']);
    // Undo still walks back through the replayed edits (Logisim's own history, built again).
    const undone = await call<{ changed: boolean }>(page, 'edit.undo', { fileId: untitled, circuitId: uc.main });
    expect(undone.changed).toBe(true);
  } finally {
    await r.close();
  }
});

test('the real engine and the app killed (N-19): the recovery file beside the saved file only, none for the new one; opened again, Recover is the model before in every circuit, unsaved; Ctrl+S removes it', async () => {
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const file = sample(work, DATAPATH);
  const saved = readFileSync(file);
  // No writes after an idle moment: the recovery file there is the one the engine writes as it ends.
  const r = await launch(undefined, { env: { ...real, HCS_RECOVERY_IDLE_MS: '600000', HCS_RECOVERY_MAX_MS: '600000' } });
  const { page } = r;
  let before: Record<string, unknown>;
  try {
    await openFile(r, file);
    await page.getByTitle('New circuit (Ctrl+N)').click();
    await expect(page.locator('.filebar .ptab')).toHaveCount(2);
    const [datapath, untitled] = await openFileIds(r.app);
    const dc = await circuitsOf(page, datapath);
    const and = await call<{ id: string }>(page, 'edit.addComponent', { fileId: datapath, circuitId: dc.alu, lib: 'Gates', name: 'AND Gate', loc: [600, 600] });
    await call(page, 'edit.setAttr', { fileId: datapath, circuitId: dc.alu, ids: [and.id], attr: 'inputs', value: '3' });
    const main = await call<Snapshot>(page, 'model.circuit', { fileId: datapath, circuitId: dc.main });
    await call(page, 'edit.move', { fileId: datapath, circuitId: dc.main, ids: [main.components.find((c) => c.name === 'Tunnel')!.id], dx: 0, dy: 10 });
    await call(page, 'edit.addWire', { fileId: datapath, circuitId: dc.main, points: [[1000, 1000], [1100, 1000]] });
    await call(page, 'edit.undo', { fileId: datapath, circuitId: dc.main });
    await call(page, 'edit.redo', { fileId: datapath, circuitId: dc.main });
    await call(page, 'edit.addComponent', { fileId: untitled, circuitId: (await circuitsOf(page, untitled)).main, lib: 'Wiring', name: 'Pin', loc: [100, 100] });
    before = await fileModel(page, datapath, true);   // subcircuits by name: the next start's engine has its own ids
    expect(JSON.stringify(before)).toContain('[\\"inputs\\",\\"3\\"]');
    expect(await killMainAndSeeEngineEnd(r.app, (await enginePid(r.app))!, 20_000)).toBe('both ended');
    await Promise.race([r.app.close().catch(() => {}), new Promise((done) => setTimeout(done, 5_000))]);
  } finally {
    // (Windows: the killed app's Chromium children may still hold its run folder a moment)
    try { rmSync(r.dir, { recursive: true, force: true, maxRetries: 10, retryDelay: 500 }); } catch { /* a scratch folder */ }
  }
  expect(readdirSync(work).sort()).toEqual(['demo-datapath.circ', 'demo-datapath.circ.hcs-recover']);
  expect(readFileSync(file)).toEqual(saved);

  const next = await launch(undefined, { env: real });
  try {
    await answerOpen(next.app, file);
    await next.page.keyboard.press('Control+o');
    const dialog = next.page.locator('dialog.ask');
    await expect(dialog.locator('h2')).toHaveText('저장하지 않은 편집이 있습니다');
    await expect(dialog.locator('.askdetail')).toHaveText('복구 파일: demo-datapath.circ.hcs-recover');
    await dialog.getByRole('button', { name: 'Recover' }).click();
    await expect(next.page.locator('.filebar .ptab')).toHaveText(['demo-datapath.circ•']);
    const [fileId] = await openFileIds(next.app);
    expect(await fileModel(next.page, fileId, true)).toEqual(before);
    expect((await call<{ dirty: boolean }>(next.page, 'file.dirty', { fileId })).dirty).toBe(true);
    await next.page.keyboard.press('Control+s');
    await expect(next.page.locator('.status .ok')).toContainText('저장했습니다 · demo-datapath.circ');
    expect(readdirSync(work).sort()).toEqual(['demo-datapath.circ']);
    // (what was saved, opened again, is the same model to the nets: engine RecoveryFileTest)
  } finally {
    await next.close();
    rmSync(work, { recursive: true, force: true });
  }
});

test('the real engine and the settings (N-19): every setting changed, quit, started again -- each is its default', async () => {
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const file = sample(work, DATAPATH);
  const r = await launch(undefined, { env: real });
  let defaults: Awaited<ReturnType<typeof settingsNow>>;
  try {
    await openFile(r, file);
    await expect(r.page.locator('.canvas .canvas-view canvas')).toBeVisible();
    defaults = await settingsNow(r.page);
    await changeEverySetting(r.page, 'alu');
    expect(await settingsNow(r.page)).not.toEqual(defaults);
  } finally {
    await r.close();
  }
  const next = await launch(undefined, { env: real });
  try {
    await openFile(next, file);
    await expect(next.page.locator('.canvas .canvas-view canvas')).toBeVisible();
    expect(await settingsNow(next.page)).toEqual(defaults);
  } finally {
    await next.close();
    rmSync(work, { recursive: true, force: true });
  }
});

test('the real engine and no orphan java: killing the window\'s process ends the engine (its stdin closes, or it sees its parent end)', async () => {
  const r = await launch(undefined, { env: real });
  const pid = (await enginePid(r.app))!;
  await openFile(r, sample(r.dir, DATAPATH));
  expect(alive(pid)).toBe(true);
  expect(await killMainAndSeeEngineEnd(r.app, pid, 20_000)).toBe('both ended');
  await Promise.race([r.app.close().catch(() => {}), new Promise((done) => setTimeout(done, 5_000))]);
  rmSync(r.dir, { recursive: true, force: true });
});

test('the real engine and the lab-PC rule: after quit nothing in HOME but the JDK\'s font list cache (Linux)', async () => {
  const runs = mkdtempSync(path.join(tmpdir(), 'hcs-runs-'));
  const r = await launch(undefined, { env: real, userData: runs });
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await r.page.keyboard.press('F10');
    await expect(r.page.locator('.status')).toContainText('Cycle 1');
  } finally {
    await r.app.close();
  }
  await expect.poll(() => (existsSync(runs) ? readdirSync(runs) : []), { timeout: 20_000 }).toEqual([]);
  // Linux only: the JDK's fontconfig list (D-134) and the libraries' caches under Chromium (labpc.e2e.ts).
  const left = readdirSync(r.home, { recursive: true }).map(String).filter((f) => statSync(path.join(r.home, f)).isFile())
    .filter((f) => !/^\.java\/fonts\//.test(f) && !/^\.cache\/(fontconfig|mesa_shader_cache|nvidia)\//.test(f));
  expect(left).toEqual([]);
  rmSync(r.dir, { recursive: true, force: true });
  rmSync(runs, { recursive: true, force: true });
});

// The Canvas with the real engine (N-05): the snapshot, its values and bodies (sim.values), ticks, and a
// subcircuit instance's own values (sim.watch with a path).
type C = { scene: { name: string; values: Map<string, string>; bodies: Map<string, { lines?: string[] }>; components: Map<string, { id: string; name: string; bounds: number[] }> } | null; canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number } };

test('the real engine and the Canvas: ref-mips drawn with its values; demo-datapath\'s register file from inside', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, 'tests/mips/ref-mips.circ'));
    await expect(page.locator('.canvas .canvas-view canvas')).toBeVisible();
    await page.waitForFunction(() => ((window as unknown as { __hcsCanvas: C }).__hcsCanvas.scene?.values.size ?? 0) > 100);
    const im = () => page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: C }).__hcsCanvas;
      const k = [...c.scene!.components.values()].find((x) => x.name === 'Instruction Memory')!;
      return c.scene!.bodies.get(k.id)?.lines?.[2];
    });
    await expect.poll(im).toBe('00400024: 00000000');
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 1');
    await expect.poll(im).toBe('00400028: 00000000');
    // demo-datapath: into the register file, its own values
    await openFile(r, sample(r.dir, DATAPATH));
    // (ref-mips's circuit is called main too: wait for demo-datapath's, the one with the register file)
    await page.waitForFunction(() => { const c = (window as unknown as { __hcsCanvas: C }).__hcsCanvas; return !!c.scene && [...c.scene.components.values()].some((x) => x.name === 'regfile') && c.scene.values.size > 0; });
    // drawn at its own size and first view: the Canvas was off the page while this scene loaded, and until its
    // size is known it still has ref-mips's view (a point from that view misses the register file)
    await canvasSettled(page);
    const at = await page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: C }).__hcsCanvas;
      const k = [...c.scene!.components.values()].find((x) => x.name === 'regfile')!;
      const rr = c.canvas.getBoundingClientRect();
      return { x: rr.left + (k.bounds[0] + k.bounds[2] / 2 - c.view.x) * c.view.zoom, y: rr.top + (k.bounds[1] + k.bounds[3] / 2 - c.view.y) * c.view.zoom };
    });
    const parent = await page.evaluate(PARENT_PORT_VALUES, 'regfile');
    await page.mouse.dblclick(at.x, at.y);
    await expect(page.locator('.canvas-crumbs .here')).toHaveText('regfile');
    await page.waitForFunction(() => { const c = (window as unknown as { __hcsCanvas: C }).__hcsCanvas; return c.scene?.name === 'regfile' && c.scene.values.size > 0; });
    // the instance's own values: each pin inside carries what the parent's wire carries at its port
    await expect.poll(() => page.evaluate(INSIDE_PIN_VALUES)).toMatchObject(Object.fromEntries(['RR1', 'RR2', 'WR', 'WD', 'RegWrite', 'clk', 'RD1', 'RD2'].map((n) => [n, parent[n]])));
    expect(parent.RR1).toMatch(/^[01]+$/);
  } finally {
    await r.close();
  }
});

// N-12 (D-150): the parts of the Canvas and the scene the tests read.
type P = { id: string; name: string; lib: string | null; loc: [number, number]; bounds: number[]; attrs: Record<string, string>; ext?: { color?: string; arms?: string[] } };
const partsNow = (page: import('@playwright/test').Page) => page.evaluate(() => {
  const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, P> } | null } }).__hcsCanvas;
  return c.scene ? [...c.scene.components.values()].map((x) => ({ id: x.id, name: x.name, lib: x.lib, loc: x.loc, bounds: x.bounds, attrs: x.attrs, ext: x.ext })) : [];
});

test('the real engine and finding and placing (N-12): a part dragged in and one from the palette, Find into the register file, Tunnel Color and the Splitter editor saved in the .circ', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    // a new file: a Hallym MIPS part dragged onto the empty Canvas puts the library in the file (V-01)
    await newCircuit(r);
    await expect(page.locator('.upper .libgroup.pending summary')).toContainText('Hallym MIPS');
    await page.locator('.upper .libgroup.pending .list li', { hasText: 'Console' }).getByRole('button').dragTo(page.locator('.pbody.canvas'), { targetPosition: { x: 300, y: 200 } });
    await expect(page.locator('.status')).toContainText('1 component');
    await expect(page.locator('.upper .libgroup.pending')).toHaveCount(0);
    expect((await partsNow(page))[0]).toMatchObject({ name: 'Console', loc: [300, 200] });
    // demo-datapath: the palette places an AND gate of three inputs where the pointer is
    await openFile(r, sample(r.dir, DATAPATH));
    await page.waitForFunction(() => { const c = (window as unknown as { __hcsCanvas: C }).__hcsCanvas; return !!c.scene && [...c.scene.components.values()].some((x) => x.name === 'regfile'); });
    const at = await page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: C }).__hcsCanvas;
      const rr = c.canvas.getBoundingClientRect();
      return { x: rr.left + (300 - c.view.x) * c.view.zoom, y: rr.top + (650 - c.view.y) * c.view.zoom };
    });
    await page.mouse.move(at.x, at.y);
    await page.keyboard.press('Control+k');
    await page.keyboard.type('and 3');
    await page.keyboard.press('Enter');
    await expect.poll(async () => (await partsNow(page)).find((c) => c.name === 'AND Gate')?.attrs.inputs).toBe('3');
    expect((await partsNow(page)).find((c) => c.name === 'AND Gate')!.loc).toEqual([300, 650]);
    // Find: a pin inside the register file, and the Canvas goes into that instance
    await page.keyboard.press('Control+f');
    await page.keyboard.type('RR1');
    await expect(page.locator('.findrow').first()).toContainText('main › regfile #1 › RR1');
    await page.keyboard.press('Enter');
    await expect(page.locator('.canvas-crumbs .here')).toHaveText('regfile');
    await page.keyboard.press('Escape');
    await page.locator('.canvas-crumbs button').first().click();
    // Tunnel Color: every MemRead tunnel
    const memread = page.locator('.lower .list.tunnels li', { hasText: 'MemRead' });
    await memread.locator('.tswatch').click();
    await page.getByRole('menu', { name: 'Tunnel Color' }).getByRole('menuitemradio', { name: 'Vermillion' }).click();
    await expect(memread.locator('.tswatch')).toHaveClass(/chosen/);
    await expect.poll(async () => (await partsNow(page)).filter((c) => c.name === 'Tunnel' && c.attrs.label === 'MemRead').map((c) => c.ext?.color)).toEqual(['#D55E00', '#D55E00']);
    // the Splitter editor: an arm renamed
    const sp = (await partsNow(page)).find((c) => c.name === 'Splitter')!;
    await page.evaluate((id) => {
      const c = (window as unknown as { __hcsCanvas: { scene: { fileId: string; circuitId: string } } }).__hcsCanvas;
      window.dispatchEvent(new CustomEvent('hcs:edit-splitter', { detail: { fileId: c.scene.fileId, circuitId: c.scene.circuitId, componentId: id } }));
    }, sp.id);
    const dlg = page.getByRole('dialog', { name: 'Edit Splitter' });
    await dlg.getByRole('textbox', { name: 'Arm 5 name' }).fill('fn');
    await dlg.getByRole('button', { name: 'Apply' }).click();
    await expect.poll(async () => (await partsNow(page)).find((c) => c.name === 'Splitter')?.ext?.arms).toEqual(['op', 'rs', 'rt', 'rd', 'shamt', 'fn']);
    // saved: the student's colour and names in hcs:ext, the original's attributes as they were
    const out = path.join(r.dir, 'saved.circ');
    await answerSave(r.app, out);
    await page.keyboard.press('Control+Shift+s');
    // the file appears before the engine has written it: wait for its end
    await expect.poll(() => existsSync(out) && readFileSync(out, 'utf8').includes('</project>')).toBe(true);
    const saved = readFileSync(out, 'utf8');
    expect(saved).toContain('<hcs:tunnel label="MemRead" color="#D55E00"/>');
    expect(saved).toContain('<hcs:splitter x="620" y="200" arm0="op" arm1="rs" arm2="rt" arm3="rd" arm4="shamt" arm5="fn"/>');
    expect(saved).toContain('<a name="fanout" val="6"/>');
  } finally {
    await r.close();
  }
});

test('the real engine and the overlays (N-15): the PC\'s Signal Flow is v1\'s, I shows the influence, the Cycle View\'s active path and fields; a group and a memo saved in hcs:ext and undone', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await opened(r);
    const pc = await partMiddle(page, 'Register', 'PC');
    await click(page, pc.at);
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { flow: { running: boolean } } } }).__hcsOverlays.shown().flow.running);
    // the engine's flow.path is v1's SignalFlowPath (tests/circ/flow/demo-pc.flow)
    expect((await shown(page)).flow.ends).toEqual(['state Instruction Memory (Addr)', 'unconnected Comparator (gt)', 'unconnected Comparator (lt)', 'output halt', 'state PC (D)']);
    await page.keyboard.press('i');
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { influence: unknown } } }).__hcsOverlays.shown().influence !== null);
    await expect(page.locator('.status')).toContainText('Influence: Forward · all steps');
    await page.keyboard.press('Escape');
    await page.keyboard.press('Escape');
    expect((await shown(page)).influence).toBeNull();
    // with the Cycle View: the MemtoReg MUX's branch and the arms named after the instruction's fields
    await page.getByRole('tab', { name: 'Cycle View' }).click();
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { activePath: number } } }).__hcsOverlays.shown().activePath > 0);
    const s = await shown(page);
    expect(s.activePath).toBe(5);
    expect(s.fields).toEqual(['rs', 'rt', 'rd']);
    await page.getByRole('tab', { name: 'Messages' }).click();
    // a signal group and an area memo: the engine's intents, one undo step each, saved in hcs:ext
    const w = await wireAtPort(page, 'alu', 'Result');
    await rightClick(page, w.at);
    await menu(page, 'Signal Group', 'Data');
    await page.waitForFunction((net) => (window as unknown as { __hcsCanvas: { scene: { groups: Map<string, { group: string }> } } }).__hcsCanvas.scene.groups.get(net)?.group === 'data', w.net);
    await rightClick(page, [700, 480]);
    await menu(page, 'Add Area Memo…');
    const d = page.locator('dialog.ovdialog[open]');
    await d.getByLabel('Text').fill('EX');
    await d.getByRole('button', { name: 'OK' }).click();
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { memos: unknown[] } } }).__hcsOverlays.shown().memos.length === 1);
    const target = path.join(r.dir, 'with-ext.circ');
    await answerSave(r.app, target);
    await page.keyboard.press('Control+Shift+s');
    await expect(page.locator('.status .ok')).toContainText('저장했습니다 · with-ext.circ');
    const text = readFileSync(target, 'utf8');
    expect(text).toContain('<hcs:group');
    expect(text).toContain('group="data"');
    expect(text).toMatch(/<hcs:memo [^>]*text="EX"/);
    await page.keyboard.press('Control+z');
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { memos: unknown[] } } }).__hcsOverlays.shown().memos.length === 0);
    await page.keyboard.press('Control+z');
    await page.waitForFunction((net) => !(window as unknown as { __hcsCanvas: { scene: { groups: Map<string, unknown> } } }).__hcsCanvas.scene.groups.has(net), w.net);
  } finally {
    await r.close();
  }
});
