/* The engine dies (N-04, D-142): it starts again, and every open file
   comes back with its unsaved edits -- the same tabs, the circuit on show,
   the same model -- a dialog says what happened (facts, no character), a
   band stays.  When the edits cannot be replayed, or the engine dies again
   while they are, the files open as last saved and the band lists them.
   Quitting or killing the window's process ends the engine: no orphan.
   With the fake engine (tests/fake-engine); real-engine.e2e.ts does the
   same with the Java engine. */

import { expect, test } from '@playwright/test';
import { existsSync, readdirSync, rmSync, unlinkSync } from 'node:fs';

import { DATAPATH, launch, openFile, sample, visibleCharacters, type Running } from './harness.ts';
import { alive, call, circuitsOf, enginePid, fileModel, journalLength, killEngine, openFileIds } from './model.ts';

const TITLE = '엔진이 멈췄다가 다시 시작했습니다';

// demo-datapath.circ with its circuit alu on show, and a new file: edits in both.
async function twoFilesWithEdits(r: Running): Promise<{ datapath: string; untitled: string }> {
  const { page } = r;
  await openFile(r, sample(r.dir, DATAPATH));
  await page.getByRole('tab', { name: 'Circuits' }).click();
  await page.locator('.upper .pbody:visible .list > li', { hasText: 'alu' }).getByRole('button').click();
  await page.getByTitle('New circuit (Ctrl+N)').click();
  await expect(page.locator('.filebar .ptab')).toHaveCount(2);
  const [datapath, untitled] = await openFileIds(r.app);
  const dc = await circuitsOf(page, datapath);
  const uc = await circuitsOf(page, untitled);
  // demo-datapath: a part in alu, one of main's parts moved, a wire drawn and taken back, then again.
  const and = await call<{ id: string }>(page, 'edit.addComponent', { fileId: datapath, circuitId: dc.alu, lib: 'Gates', name: 'AND Gate', loc: [500, 500] });
  await call(page, 'edit.setAttr', { fileId: datapath, circuitId: dc.alu, ids: [and.id], attr: 'inputs', value: '3' });
  const main = await call<{ components: { id: string }[] }>(page, 'model.circuit', { fileId: datapath, circuitId: dc.main });
  await call(page, 'edit.move', { fileId: datapath, circuitId: dc.main, ids: [main.components[0].id], dx: 10, dy: 0 });
  await call(page, 'edit.addWire', { fileId: datapath, circuitId: dc.main, points: [[900, 900], [950, 900]] });
  await call(page, 'edit.undo', { fileId: datapath, circuitId: dc.main });
  await call(page, 'edit.redo', { fileId: datapath, circuitId: dc.main });
  // the new file: two parts, one deleted again.
  const pin = await call<{ id: string }>(page, 'edit.addComponent', { fileId: untitled, circuitId: uc.main, lib: 'Wiring', name: 'Pin', loc: [100, 100] });
  await call(page, 'edit.addComponent', { fileId: untitled, circuitId: uc.main, lib: 'Wiring', name: 'Pin', loc: [100, 200] });
  await call(page, 'edit.delete', { fileId: untitled, circuitId: uc.main, ids: [pin.id] });
  return { datapath, untitled };
}

test('a crash: the files come back with their unsaved edits, the same tabs and circuit on show; a dialog of facts, a band', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const { datapath, untitled } = await twoFilesWithEdits(r);
    expect(await journalLength(r.app, datapath)).toBe(6);
    expect(await journalLength(r.app, untitled)).toBe(3);
    const before = { [datapath]: await fileModel(page, datapath), [untitled]: await fileModel(page, untitled) };
    // The first file is on show again, with its circuit alu.
    await page.locator('.filebar .ptab', { hasText: 'demo-datapath.circ' }).click();
    await expect(page.locator('.circuitbar .ptab.on')).toHaveText('alu');

    const pid = await killEngine(r.app);
    const dialog = page.locator('dialog.ask');
    await expect(dialog.locator('h2')).toHaveText(TITLE);
    await expect(dialog).toContainText('열려 있던 파일 2개를 다시 열고 저장하지 않은 편집 9개를 다시 적용했습니다.');
    await expect(dialog).toContainText('시뮬레이션은 Reset 상태로 돌아갔습니다.');
    await expect(dialog.locator('.askdetail')).toContainText('다시 엶: demo-datapath.circ · 편집 6개 다시 적용\n다시 엶: untitled.circ · 편집 3개 다시 적용\n엔진: signal SIGKILL');
    await expect(dialog.getByRole('button')).toHaveText(['Close']);
    expect(await visibleCharacters(page)).toBe(0);
    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect(page.locator('.band')).toHaveText('엔진이 멈춰서 다시 시작했습니다 · 파일 2개를 되살렸습니다 · 시뮬레이션은 Reset 상태입니다');
    expect(await visibleCharacters(page)).toBe(0);

    // The same tabs, the same file and circuit on show, still unsaved.
    await expect(page.locator('.filebar .ptab')).toHaveText(['demo-datapath.circ•', 'untitled.circ•']);
    await expect(page.locator('.filebar .ptab.on')).toContainText('demo-datapath.circ');
    await expect(page.locator('.circuitbar .ptab')).toHaveText(['main', 'alu']);
    await expect(page.locator('.circuitbar .ptab.on')).toHaveText('alu');
    await expect(page.locator('.status')).toContainText('alu');
    expect(await enginePid(r.app)).not.toBe(pid);
    expect(alive(pid)).toBe(false);
    // The same model, in a new engine, under the same file and circuit ids.
    expect(await openFileIds(r.app)).toEqual([datapath, untitled]);
    expect({ [datapath]: await fileModel(page, datapath), [untitled]: await fileModel(page, untitled) }).toEqual(before);
    // The journal still holds them (not saved yet): a second crash brings them back again.
    expect(await journalLength(r.app, datapath)).toBe(6);
    await killEngine(r.app);
    await expect(dialog.locator('h2')).toHaveText(TITLE);
    await dialog.getByRole('button', { name: 'Close' }).click();
    expect({ [datapath]: await fileModel(page, datapath), [untitled]: await fileModel(page, untitled) }).toEqual(before);
    // Editing goes on in the new engine.
    const dc = await circuitsOf(page, datapath);
    await call(page, 'edit.addComponent', { fileId: datapath, circuitId: dc.regfile, lib: 'Gates', name: 'OR Gate', loc: [700, 700] });
    expect(await journalLength(r.app, datapath)).toBe(7);
  } finally {
    await r.close();
  }
});

test('saving starts the journal over: after a crash only the edits since the save are replayed', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const { datapath } = await twoFilesWithEdits(r);
    await page.locator('.filebar .ptab', { hasText: 'demo-datapath.circ' }).click();
    await page.keyboard.press('Control+s');
    await expect(page.locator('.status .ok')).toContainText('저장했습니다 · demo-datapath.circ');
    expect(await journalLength(r.app, datapath)).toBe(0);
    const dc = await circuitsOf(page, datapath);
    await call(page, 'edit.addComponent', { fileId: datapath, circuitId: dc.main, lib: 'Gates', name: 'NOT Gate', loc: [300, 800] });
    await killEngine(r.app);
    const dialog = page.locator('dialog.ask');
    await expect(dialog).toContainText('저장하지 않은 편집 4개를 다시 적용했습니다.');
    await expect(dialog.locator('.askdetail')).toContainText('다시 엶: demo-datapath.circ · 편집 1개 다시 적용');
  } finally {
    await r.close();
  }
});

test('the edits cannot be replayed: the files open as last saved, the band lists them; the tabs stay', async () => {
  const r = await launch(undefined, { env: { FAKE_ENGINE_FAIL_AFTER_RESTART: 'edit.addComponent' } });
  const { page } = r;
  try {
    const { datapath } = await twoFilesWithEdits(r);
    await killEngine(r.app);
    const dialog = page.locator('dialog.ask');
    await expect(dialog.locator('h2')).toHaveText(TITLE);
    await expect(dialog).toContainText('파일 2개는 저장하지 않은 편집을 되살리지 못해 마지막으로 저장한 상태로 열었습니다.');
    await expect(dialog.locator('.askdetail')).toContainText('저장한 상태로 엶: demo-datapath.circ · 편집을 다시 적용하지 못함 · 편집 6개');
    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect(page.locator('.band')).toHaveText('저장하지 않은 편집을 되살리지 못했습니다: demo-datapath.circ, untitled.circ · 마지막으로 저장한 상태로 열었습니다 · 시뮬레이션은 Reset 상태입니다');
    await expect(page.locator('.filebar .ptab')).toHaveText(['demo-datapath.circ', 'untitled.circ']);   // not unsaved: as on disk
    // demo-datapath as on disk: alu without the AND gate, main as it was.
    const dc = await circuitsOf(page, datapath);
    const alu = await call<{ components: { name: string }[] }>(page, 'model.circuit', { fileId: datapath, circuitId: dc.alu });
    expect(alu.components.some((c) => c.name === 'AND Gate' && JSON.stringify(c).includes('500,500'))).toBe(false);
    expect(await journalLength(r.app, datapath)).toBe(0);
  } finally {
    await r.close();
  }
});

test('the engine dies again while the edits are replayed: the next start opens the files as last saved', async () => {
  const r = await launch(undefined, { env: { FAKE_ENGINE_CRASH_AFTER_RESTART: 'edit.addComponent' } });
  const { page } = r;
  try {
    await twoFilesWithEdits(r);
    await killEngine(r.app);
    const dialog = page.locator('dialog.ask');
    await expect(dialog.locator('h2')).toHaveText(TITLE);
    await expect(dialog.locator('.askdetail')).toContainText('저장한 상태로 엶: demo-datapath.circ · 되살리는 중에 엔진이 다시 멈춤 · 편집 6개');
    await expect(dialog.locator('.askdetail')).toContainText('엔진: exit code 70');
    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect(page.locator('.band')).toHaveText('저장하지 않은 편집을 되살리지 못했습니다: demo-datapath.circ, untitled.circ · 마지막으로 저장한 상태로 열었습니다 · 시뮬레이션은 Reset 상태입니다');
    await expect(page.locator('.filebar .ptab')).toHaveCount(2);
    // The engine works: a new edit is answered and recorded.
    const [datapath] = await openFileIds(r.app);
    const dc = await circuitsOf(page, datapath);
    await call(page, 'edit.delete', { fileId: datapath, circuitId: dc.main, ids: [] });
  } finally {
    await r.close();
  }
});

test('a file gone from disk cannot be opened again: its tab closes, the band says so', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const gone = sample(r.dir, 'tests/circ/gates.circ', 'gone.circ');
    await openFile(r, gone);
    await openFile(r, sample(r.dir, DATAPATH));
    unlinkSync(gone);
    await killEngine(r.app);
    const dialog = page.locator('dialog.ask');
    await expect(dialog).toContainText('파일 1개는 다시 열지 못해 닫았습니다.');
    await expect(dialog.locator('.askdetail')).toContainText('닫음: gone.circ · 그 자리에 파일이 없음');
    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect(page.locator('.band')).toHaveText('엔진이 멈춰서 다시 시작했습니다 · 파일 1개를 되살렸습니다 · 다시 열지 못해 닫았습니다: gone.circ · 시뮬레이션은 Reset 상태입니다');
    await expect(page.locator('.filebar .ptab')).toHaveText(['demo-datapath.circ']);
  } finally {
    await r.close();
  }
});

test('no orphan engine: quitting ends it; so does killing the window\'s process (its stdin closes)', async () => {
  const quit = await launch();
  const pid1 = (await enginePid(quit.app))!;
  expect(alive(pid1)).toBe(true);
  await quit.close();
  await expect.poll(() => alive(pid1), { timeout: 10_000 }).toBe(false);

  const killed = await launch();
  const pid2 = (await enginePid(killed.app))!;
  expect(alive(pid2)).toBe(true);
  killed.app.process().kill('SIGKILL');
  await expect.poll(() => alive(pid2), { timeout: 10_000 }).toBe(false);
  await Promise.race([killed.app.close().catch(() => {}), new Promise((done) => setTimeout(done, 5_000))]);
  rmSync(killed.dir, { recursive: true, force: true });
});

test('the engine\'s log stays in memory: shown in the dialog after a crash; the run folder is gone after quit', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await killEngine(r.app);
    await expect(page.locator('dialog.ask .askdetail')).toContainText('fake engine ready');   // its stderr
  } finally {
    await r.app.close();
  }
  await expect.poll(() => (existsSync(r.userData) ? readdirSync(r.userData) : []), { timeout: 20_000 }).toEqual([]);
  rmSync(r.dir, { recursive: true, force: true });
});
