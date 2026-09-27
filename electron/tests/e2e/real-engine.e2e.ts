/* The window with the real engine (engine/, N-03) instead of the fake one:
   the same flows end to end -- hello, a new circuit, a .circ with the MIPS
   library, the clock, saving, a crash and the unsaved edits replayed
   (N-04), no orphan java, quitting.  Opt-in, as it needs the engine built
   and a Java 21:

     ./gradlew :engine:stage :engine:runtime    (engine/build/stage: the jars; engine/build/runtime: the bundled JRE)
     HCS_E2E_REAL_ENGINE=1 HCS_JAVA=$PWD/../engine/build/runtime/bin/java xvfb-run -a npx playwright test real-engine

   CI's runtime job runs it with the bundled runtime (its AppCDS archive too). */

import { expect, test } from '@playwright/test';
import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import type { Snapshot } from '../../src/main/protocol.ts';
import { answerOpen, answerSave, DATAPATH, launch, newCircuit, openFile, repo, sample, type LaunchOptions } from './harness.ts';
import { alive, call, circuitsOf, enginePid, fileModel, journalLength, killEngine, killMainAndSeeEngineEnd, openFileIds } from './model.ts';

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
    await expect(page.locator('.canvas h3')).toContainText('이 회로에는 부품');
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
    await page.getByRole('button', { name: /Reset/ }).first().click();
    await expect(page.locator('.msg')).toHaveCount(2);
  } finally {
    await r.close();
  }
});

test('the real engine: Load Program puts data.hmx into ref-mips; N Cycles to the exit; the Console says what SPIM said (N-16)', async () => {
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
    await expect(page.locator('.canvas h3')).toContainText('이 회로에는 부품');
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
