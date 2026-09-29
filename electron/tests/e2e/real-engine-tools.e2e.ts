/* The rest of v1's commands with the real engine (N-21, D-162): Undo
   History over Logisim's own undo log and the fork's redo stack (the file
   all undone saves as it was), Analyze Circuit and Get Circuit Statistics
   from the original's classes, Create Submission's zip written by the
   engine, and Export Image from the Canvas's shapes.  Opt-in, as it needs
   the engine built:

     ./gradlew :engine:stage
     HCS_E2E_REAL_ENGINE=1 xvfb-run -a npx playwright test real-engine-tools */

import { expect, type Page, test } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { inflateRawSync } from 'node:zlib';

import { answerSave, canvasSettled, launch, type LaunchOptions, openFile, repo, sample } from './harness.ts';

const JAR = path.join(repo, 'engine/build/stage/hcs-engine.jar');
const real: LaunchOptions['env'] = { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: JAR };

test.skip(process.env.HCS_E2E_REAL_ENGINE !== '1', 'opt-in: HCS_E2E_REAL_ENGINE=1 with engine/build/stage built');

type CanvasApi = { scene: { fileId: string; circuitId: string; components: Map<string, unknown> } | null };
const count = (page: Page) => page.evaluate(() => (window as unknown as { __hcsCanvas: CanvasApi }).__hcsCanvas.scene?.components.size ?? 0);
async function command(page: Page, text: string, name: string): Promise<void> {
  await page.locator('.canvas .canvas-view canvas').click({ position: { x: 5, y: 5 } });
  await page.keyboard.press('Escape');
  await page.keyboard.press('Control+k');
  const pal = page.getByRole('dialog', { name: 'Search' });
  await page.keyboard.type(text);
  await expect(pal.locator('.palrow').first()).toContainText(name);
  await page.keyboard.press('Enter');
}
const place = (page: Page, name: string, at: [number, number]) => page.evaluate(async ({ n, a }) => {
  const c = (window as unknown as { __hcsCanvas: CanvasApi }).__hcsCanvas.scene!;
  await (window as unknown as { app: { call(m: string, p: unknown): Promise<unknown> } }).app.call('edit.addComponent', { fileId: c.fileId, circuitId: c.circuitId, lib: 'Gates', name: n, loc: a });
}, { n: name, a: at });

// The names in a zip (its central directory) and each entry's bytes.
function unzip(b: Buffer): Map<string, Buffer> {
  const out = new Map<string, Buffer>();
  const end = b.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  let at = b.readUInt32LE(end + 16);
  for (let i = 0; i < b.readUInt16LE(end + 10); i++) {
    const method = b.readUInt16LE(at + 10), size = b.readUInt32LE(at + 20), n = b.readUInt16LE(at + 28), extra = b.readUInt16LE(at + 30), note = b.readUInt16LE(at + 32);
    const local = b.readUInt32LE(at + 42);
    const name = b.toString('utf8', at + 46, at + 46 + n);
    const start = local + 30 + b.readUInt16LE(local + 26) + b.readUInt16LE(local + 28);
    const body = b.subarray(start, start + size);
    out.set(name, method === 8 ? inflateRawSync(body) : body);
    at += 46 + n + extra + note;
  }
  return out;
}

test('the real engine: Undo History jumps (all undone saves the original bytes), Analyze Circuit, Statistics, Create Submission, Export Image', async () => {
  expect(existsSync(JAR), `${JAR}: ./gradlew :engine:stage`).toBe(true);
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    const file = sample(r.dir, 'tests/circ/subcircuit.circ');
    await openFile(r, file);
    await page.waitForFunction(() => !!(window as unknown as { __hcsCanvas?: CanvasApi }).__hcsCanvas?.scene);
    await canvasSettled(page);
    // the engine's own save of the file as opened: what all undone must save again
    const start = path.join(r.dir, 'start.circ');
    await answerSave(r.app, start);
    await page.keyboard.press('Control+Shift+s');
    await expect(page.locator('.status')).toContainText('저장했습니다 · start.circ');
    const original = readFileSync(start);
    const before = await count(page);
    await place(page, 'AND Gate', [600, 600]);
    await place(page, 'OR Gate', [700, 600]);
    await expect.poll(() => count(page)).toBe(before + 2);

    // Undo History: Logisim's own action names
    await command(page, 'undo history', 'Undo History…');
    const win = page.getByRole('dialog', { name: 'Undo History' });
    const rows = win.locator('.histrow');
    await expect(rows.filter({ hasText: 'Add AND Gate' })).toHaveCount(1);
    await expect(rows.filter({ hasText: 'Add OR Gate' })).toHaveCount(1);
    await rows.first().click();
    await expect(win.locator('.histrow.now')).toHaveText('▶Now');
    await expect(rows.nth(1)).toHaveText('▶Now');
    await expect.poll(() => count(page)).toBe(before);
    await expect(rows.last()).toHaveText('Add OR Gate');
    // all undone: the file is as it was, and saves as it was
    const saved = path.join(r.dir, 'undone.circ');
    await answerSave(r.app, saved);
    await page.keyboard.press('Control+Shift+s');
    await expect(page.locator('.status')).toContainText('저장했습니다 · undone.circ');
    expect(readFileSync(saved).equals(original), 'the original bytes').toBe(true);
    await rows.last().click();
    await expect.poll(() => count(page)).toBe(before + 2);
    await win.locator('.histlist').focus();
    await page.keyboard.press('Escape');

    // Analyze Circuit: the original's classes
    await page.locator('.panel.upper .ptab', { hasText: 'Circuits' }).click();
    await page.locator('.circlist li button', { hasText: 'half_adder' }).first().click({ button: 'right' });
    await page.locator('.ovmenu button', { hasText: 'Analyze Circuit' }).click();
    const a = page.getByRole('dialog', { name: 'Combinational Analysis: half_adder' });
    await expect(a.locator('table.truth tbody tr')).toHaveCount(4);
    await a.locator('.tooltab', { hasText: 'Minimized' }).click();
    await expect(a.locator('.exprs dd').first()).toContainText('a b + a b');   // ~a b + a ~b, the NOT as an overline
    await a.getByRole('button', { name: 'Close' }).click();
    await page.locator('.circlist li button', { hasText: 'main' }).first().click({ button: 'right' });
    await page.locator('.ovmenu button', { hasText: 'Get Circuit Statistics' }).click();
    const s = page.getByRole('dialog', { name: 'main Statistics' });
    await expect(s.locator('tbody tr', { hasText: 'half_adder' })).toHaveCount(1);
    await expect(s.locator('tbody tr td:first-child', { hasText: /^OR Gate$/ })).toHaveCount(1);   // the one placed above
    await s.getByRole('button', { name: 'Close' }).click();

    // Create Submission: saved first (the placed gates), the engine's zip
    await command(page, '제출', 'Create Submission…');
    const d = page.getByRole('dialog', { name: 'Create Submission' });
    await expect(d.locator('.subfiles li').first()).toHaveText('undone.circ');
    const zip = path.join(r.dir, 'hand-in.zip');
    await answerSave(r.app, zip);
    await d.getByRole('button', { name: 'Create…' }).click();
    await expect(page.locator('.status')).toContainText('제출 파일을 만들었습니다 · hand-in.zip');
    const entries = unzip(readFileSync(zip));
    expect([...entries.keys()]).toEqual(['undone.circ']);
    expect(entries.get('undone.circ')!.equals(readFileSync(saved)), 'the saved file as on disk').toBe(true);

    // Export Image: SVG from the Canvas's shapes, its fonts inside
    const svg = path.join(r.dir, 'pic.svg');
    await answerSave(r.app, svg);
    await command(page, 'export', 'Export Image…');
    const e = page.getByRole('dialog', { name: 'Export Image' });
    await e.getByLabel('Format').selectOption('svg');
    await e.getByRole('button', { name: 'Export…' }).click();
    await expect(page.locator('.status')).toContainText('내보냈습니다 · pic.svg');
    const text = readFileSync(svg, 'utf8');
    expect(text.match(/data-part="/g)?.length).toBe(await count(page));
    expect(text).toContain('@font-face{font-family:Pretendard');
  } finally {
    await r.close();
  }
});
