/* Load Program…, its summary and dialogs, the status bar's program facts,
   the band while a reload has failed, the Console tab (N-16, D-147), with
   the fake engine: its mips.* answers are the real engine's for the same
   files (tests/fixtures/programs.json, engine/ ProgramFixtureTest), so the
   words here are the ones the student reads.  The real engine end to end:
   real-engine.e2e.ts. */

import { expect, test, type ElectronApplication } from '@playwright/test';
import { copyFileSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { launch, openFile, repo, sample, statusText, visibleCharacters, type Running } from './harness.ts';

const REF = 'tests/mips/ref-mips.circ';
const GOLDEN = 'tests/hmx/hallym-mips-v2.4.0';

// The next open dialogs answer `file`, and the options they were opened with are kept.
async function answerProgram(app: ElectronApplication, file: string): Promise<void> {
  await app.evaluate(({ dialog }, f) => {
    const g = globalThis as unknown as { __dialogs: unknown[] };
    g.__dialogs = [];
    dialog.showOpenDialog = (async (_w: unknown, o: unknown) => { g.__dialogs.push(o); return { canceled: false, filePaths: [f] }; }) as typeof dialog.showOpenDialog;
  }, file);
}
const dialogsShown = (app: ElectronApplication) =>
  app.evaluate(() => (globalThis as unknown as { __dialogs: { filters: unknown; defaultPath?: string; title?: string }[] }).__dialogs);

// ref-mips and a program beside it (its .s too: the source check).
function workspace(r: Running, program = 'data'): { circ: string; hmx: string } {
  const circ = sample(r.dir, REF);
  sample(r.dir, `${GOLDEN}/${program}.s`);
  return { circ, hmx: sample(r.dir, `${GOLDEN}/${program}.hmx`) };
}

async function loadProgram(r: Running, hmx: string): Promise<void> {
  await answerProgram(r.app, hmx);
  await r.page.getByRole('button', { name: /Load Program/ }).click();
}

test('Load Program…: executable images only, the summary, then Program in the status bar', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const w = workspace(r);
    await openFile(r, w.circ);
    await loadProgram(r, w.hmx);
    const d = page.locator('dialog.loadsummary');
    await expect(d.locator('h2')).toHaveText('실행 이미지를 불러왔습니다');
    await expect(d.locator('.askfile')).toHaveText('File: data.hmx');
    const rows = d.locator('table.summary tr');
    await expect(rows.locator('th')).toHaveText(['Entry', '.text', '.data', 'reg', 'Source', 'Made by', 'Instructions']);
    await expect(rows.nth(0).locator('td')).toHaveText('0x00400024 (main)');
    await expect(rows.nth(1).locator('td')).toContainText('27 words (0x00400000–0x00400068)main › Instruction Memory (00400000-004fffff)');
    await expect(rows.nth(2).locator('td')).toContainText('28 bytes = 7 words (0x10010000–0x1001001b)');
    await expect(rows.nth(3).locator('td')).toContainText('$sp 0x7fffffe4 · $gp 0x10008000');
    await expect(rows.nth(4).locator('td')).toHaveText('원본 파일 data.s: 내보낸 때와 같음.');
    await expect(d.locator('img.char')).toBeVisible();   // a summary, not an error
    const shown = await dialogsShown(r.app);
    expect(shown).toHaveLength(1);
    expect(shown[0].filters).toEqual([{ name: 'Executable image (*.hmx)', extensions: ['hmx'] }]);
    expect(shown[0].defaultPath).toBe(r.dir);
    await d.getByRole('button', { name: 'OK' }).click();
    await expect(page.locator('dialog[open]')).toHaveCount(0);
    await expect(page.locator('.status .progfact').first()).toHaveText('Program data.hmx');
    expect(await statusText(page)).toContain('불러왔습니다 · data.hmx');
    await expect(page.locator('.progband')).toBeHidden();
  } finally {
    await r.close();
  }
});

test('a file that cannot be loaded: its line, what is wrong and what to do; no character; nothing changed', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const w = workspace(r);
    await openFile(r, w.circ);
    await loadProgram(r, sample(r.dir, 'tests/hmx/truncated.hmx'));
    const d = page.locator('dialog.loaderror');
    await expect(d.locator('h2')).toHaveText('실행 이미지를 불러오지 못했습니다');
    await expect(d.locator('.askfile')).toHaveText('File: truncated.hmx');
    await expect(d.locator('p').nth(1)).toHaveText('아무것도 바꾸지 않았습니다.');
    await expect(d.locator('.problems li')).toHaveText(['8번째 줄: .text 줄에는 워드 14개라고 적혀 있지만 실제로는 13개입니다. 파일이 잘렸을 수 있습니다. Hallym MIPS에서 다시 내보내세요.']);
    expect(await visibleCharacters(page)).toBe(0);
    await d.getByRole('button', { name: 'Close' }).click();
    expect(await statusText(page)).toContain('불러오지 못했습니다 · truncated.hmx');
    await expect(page.locator('.status .progfact')).toHaveCount(0);
    // A .s: the fact and what to do (D-141)
    await loadProgram(r, sample(r.dir, `${GOLDEN}/data.s`, 'lab.s'));
    await expect(page.locator('dialog.loaderror .problems li')).toHaveText(['이 파일은 .s 파일을 가리킵니다. Hallym MIPS에서 Export executable image (.hmx) 단추로 내보낸 파일을 불러오세요.']);
  } finally {
    await r.close();
  }
});

test('several Instruction Memories hold the program: the window asks which, then loads the same file', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, 'electron/tests/fixtures/two-imems.circ'));
    await loadProgram(r, sample(r.dir, `${GOLDEN}/data.hmx`));
    const d = page.locator('dialog.loadchoose');
    await expect(d.locator('h2')).toHaveText('어느 메모리에 넣을까요?');
    await expect(d.locator('p').nth(1)).toHaveText('.text 0x00400000–0x00400068 구간을 담을 수 있는 Instruction Memory 부품이 여럿입니다. 넣을 부품을 고르세요.');
    await expect(d.locator('.choice')).toHaveText(['main › IM1 (00400000-004fffff)', 'main › IM2 (00400000-004fffff)']);
    await expect(d.locator('input[type=radio]').first()).toBeChecked();
    await d.locator('.choice', { hasText: 'IM2' }).click();
    await d.getByRole('button', { name: 'Load' }).click();
    await expect(page.locator('dialog.loadsummary')).toBeVisible();
    expect(await dialogsShown(r.app)).toHaveLength(1);   // the same file again, no second dialog
    await page.locator('dialog.loadsummary').getByRole('button', { name: 'OK' }).click();
    // Cancel: nothing loaded
    await loadProgram(r, sample(r.dir, `${GOLDEN}/pseudo.hmx`));
    await page.locator('dialog.loadchoose').getByRole('button', { name: 'Cancel' }).click();
    await expect(page.locator('dialog[open]')).toHaveCount(0);
    await expect(page.locator('.status .progfact').first()).toHaveText('Program data.hmx');
  } finally {
    await r.close();
  }
});

test('a reload that fails keeps the program on show: the band with the last load\'s time; fixed, it goes', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const w = workspace(r);
    await openFile(r, w.circ);
    await loadProgram(r, w.hmx);
    await page.locator('dialog.loadsummary').getByRole('button', { name: 'OK' }).click();
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 1');
    // Exported again, cut off
    writeFileSync(w.hmx, readFileSync(path.join(repo, 'tests/hmx/truncated.hmx')));
    const band = page.locator('.progband');
    await expect(band).toHaveText(/^지금 올라가 있는 것은 마지막으로 불러온 실행 이미지입니다 \(\d\d:\d\d:\d\d\) · 다시 불러오지 못했습니다: data\.hmx — 8번째 줄: \.text 줄에는 워드 14개라고/);
    await expect(band).toHaveAttribute('title', /\n8번째 줄: /);
    expect(await visibleCharacters(page)).toBe(0);            // the band says something went wrong
    await expect(page.locator('.status')).toContainText('Cycle 1');   // the simulation goes on as it was
    await expect(page.locator('.status .progfact').first()).toHaveText('Program data.hmx');
    // The band is a band, over the work (not an overlay): the work area starts under it
    const [b, work] = await Promise.all([band.boundingBox(), page.locator('.work').boundingBox()]);
    expect(b!.y + b!.height).toBeLessThanOrEqual(work!.y + 1);
    // Exported again, right: loaded again from the start, the band goes
    copyFileSync(path.join(repo, `${GOLDEN}/pseudo.hmx`), w.hmx);
    await expect(band).toBeHidden();
    await expect(page.locator('.status')).toContainText('Cycle 0');
    expect(await statusText(page)).toContain('다시 불러왔습니다 · data.hmx');
  } finally {
    await r.close();
  }
});

test('the Console tab: the program\'s whole output, -- exit --; Reset empties it', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const w = workspace(r);
    await openFile(r, w.circ);
    await page.getByRole('tab', { name: 'Console' }).click();
    const body = page.locator('.pbody.bottom:visible');
    await expect(body.locator('.notice h3')).toHaveText('아직 출력이 없습니다');
    await loadProgram(r, w.hmx);
    await page.locator('dialog.loadsummary').getByRole('button', { name: 'OK' }).click();
    await page.keyboard.press('F10');
    await expect(body.locator('pre.consoletext')).toHaveText('sum = 14\n-- exit --\n');
    await expect(body.locator('img.char')).toHaveCount(0);
    await page.getByRole('button', { name: /Reset/ }).click();
    await expect(body.locator('.notice h3')).toHaveText('아직 출력이 없습니다');
  } finally {
    await r.close();
  }
});

test('an engine crash after Load Program: the file comes back with its program (the journal replays mips.load), the Console empty', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const w = workspace(r);
    await openFile(r, w.circ);
    await loadProgram(r, w.hmx);
    await page.locator('dialog.loadsummary').getByRole('button', { name: 'OK' }).click();
    await page.getByRole('tab', { name: 'Console' }).click();
    await page.keyboard.press('F10');
    await expect(page.locator('pre.consoletext')).toHaveText('sum = 14\n-- exit --\n');
    await r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { kill(): void } } }).__hcs.engine.kill());
    await expect(page.locator('dialog.ask h2')).toHaveText('엔진이 멈췄다가 다시 시작했습니다');
    await page.locator('dialog.ask').getByRole('button', { name: 'Close' }).click();
    await expect(page.locator('.status .progfact').first()).toHaveText('Program data.hmx');
    await expect(page.locator('.pbody.bottom:visible .notice h3')).toHaveText('아직 출력이 없습니다');
  } finally {
    await r.close();
  }
});

test('PC ≠ entry at cycle 0 is a fact in the status bar, not a message', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const w = workspace(r, 'main-later');
    await openFile(r, w.circ);
    await loadProgram(r, w.hmx);
    await page.locator('dialog.loadsummary').getByRole('button', { name: 'OK' }).click();
    await expect(page.locator('.status .progfact.warn')).toHaveText('PC 0x00400024 · 실행 이미지 진입점 0x0040002c');
    await expect(page.locator('.msg')).toHaveCount(0);
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 1');
    await expect(page.locator('.status .progfact.warn')).toHaveCount(0);
  } finally {
    await r.close();
  }
});

test('a memory that points to a .s: the fact, and Load .hmx… opens next to it with the same name', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const circ = path.join(r.dir, 'old-lab.circ');
    const im = '<comp lib="7" loc="(6400,400)" name="Instruction Memory"/>';
    writeFileSync(circ, readFileSync(path.join(repo, REF), 'utf8').replace(im,
      '<comp lib="7" loc="(6400,400)" name="Instruction Memory">\n      <a name="source" val="prog/sum.s"/>\n    </comp>'));
    await openFile(r, circ);
    const fact = page.locator('.status .progfact.warn');
    await expect(fact).toHaveText('이 파일은 .s 파일을 가리킵니다. Hallym MIPS에서 Export executable image (.hmx) 단추로 내보낸 파일을 불러오세요.');
    await answerProgram(r.app, sample(r.dir, `${GOLDEN}/data.hmx`));
    await page.locator('.status').getByRole('button', { name: 'Load .hmx…' }).click();
    await page.locator('dialog.loadsummary').getByRole('button', { name: 'OK' }).click();
    const shown = await dialogsShown(r.app);
    expect(shown[0].defaultPath).toBe(path.join(r.dir, 'prog', 'sum.hmx'));
    await expect(page.locator('.status .progfact').first()).toHaveText('Program data.hmx');
    await expect(page.locator('.status .factbtn')).toHaveCount(0);
  } finally {
    await r.close();
  }
});
