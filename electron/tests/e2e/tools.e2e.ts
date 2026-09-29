/* The rest of v1's commands on screen (N-21, D-162), with the fake engine
   (its analyses and counts are the real engine's: tests/fixtures/project-tools.json):

     Undo History     the window over the Canvas, its rows, a click goes there
     Analyze Circuit  the circuit menu's item, the original's tabs, its stops
     Statistics       the original's table and totals
     Submission       saved first, v1's checks, the zip where the dialog says
     Export Image     SVG with its fonts, PNG at 2x, PDF; Selection Only
     Print            the chosen circuits, one page each (HCS_PRINT_TO_PDF)

   The real engine's flow is in real-engine-tools.e2e.ts. */

import { expect, test, type Page } from '@playwright/test';
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { answerSave, canvasSettled, DATAPATH, launch, openFile, recordCalls, sample, sentCalls } from './harness.ts';
import { decodePng } from './png.ts';

const SUB = 'tests/circ/subcircuit.circ';
const GATES = 'tests/circ/gates.circ';

type CanvasApi = { scene: { components: Map<string, { id: string; name: string; bounds: [number, number, number, number] }> } | null; view: { x: number; y: number; zoom: number }; canvas: HTMLCanvasElement };
const parts = (page: Page) => page.evaluate(() => {
  const c = (window as unknown as { __hcsCanvas: CanvasApi }).__hcsCanvas;
  return c.scene ? [...c.scene.components.values()].map((x) => ({ id: x.id, name: x.name, bounds: x.bounds })) : [];
});
async function drawn(page: Page): Promise<void> {
  await page.waitForFunction(() => !!(window as unknown as { __hcsCanvas?: CanvasApi }).__hcsCanvas?.scene);
  await canvasSettled(page);
}
async function command(page: Page, text: string, name: string, keepSelection = false): Promise<void> {
  if (!keepSelection) {
    await page.locator('.canvas .canvas-view canvas').click({ position: { x: 5, y: 5 } });
    await page.keyboard.press('Escape');
  }
  await page.keyboard.press('Control+k');
  const pal = page.getByRole('dialog', { name: 'Search' });
  await page.keyboard.type(text);
  await expect(pal.locator('.palrow').first()).toContainText(name);
  await page.keyboard.press('Enter');
}
async function circuitMenu(page: Page, name: string, item: string): Promise<void> {
  await page.locator('.panel.upper .ptab', { hasText: 'Circuits' }).click();
  await page.locator('.circlist li button', { hasText: name }).first().click({ button: 'right' });
  await page.locator('.ovmenu button', { hasText: item }).first().click();
}
const place = (page: Page, name: string, at: [number, number]) => page.evaluate(async ({ n, a }) => {
  const c = (window as unknown as { __hcsCanvas: { scene: { fileId: string; circuitId: string } } }).__hcsCanvas.scene;
  await (window as unknown as { app: { call(m: string, p: unknown): Promise<unknown> } }).app.call('edit.addComponent', { fileId: c.fileId, circuitId: c.circuitId, lib: 'Gates', name: n, loc: a });
}, { n: name, a: at });

test('Undo History (E-05, I-193): Start of History, the actions, Now, what can be done again; a row goes there, one intent', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(page);
    const before = (await parts(page)).length;
    await place(page, 'AND Gate', [900, 900]);
    await place(page, 'OR Gate', [1000, 900]);
    await expect.poll(async () => (await parts(page)).length).toBe(before + 2);
    await command(page, 'undo history', 'Undo History…');
    const win = page.getByRole('dialog', { name: 'Undo History' });
    await expect(win).toBeVisible();
    const rows = win.locator('.histrow');
    await expect(rows).toHaveText(['Start of History', 'Add AND Gate', 'Add OR Gate', '▶Now']);
    await expect(win.locator('.histrow.now')).toHaveAttribute('aria-selected', 'true');
    await recordCalls(r.app);
    // the first action's row: back to just after it
    await rows.nth(1).click();
    await expect(rows).toHaveText(['Start of History', 'Add AND Gate', '▶Now', 'Add OR Gate']);
    await expect.poll(async () => (await parts(page)).length).toBe(before + 1);
    expect((await sentCalls(r.app, 'edit.history')).map((c) => c.params.moves)).toEqual([-1]);
    // Start of History: all undone; the last redo row: all done again
    await rows.first().click();
    await expect(rows).toHaveText(['Start of History', '▶Now', 'Add AND Gate', 'Add OR Gate']);
    await expect.poll(async () => (await parts(page)).length).toBe(before);
    await rows.last().click();
    await expect(rows).toHaveText(['Start of History', 'Add AND Gate', 'Add OR Gate', '▶Now']);
    await expect.poll(async () => (await parts(page)).length).toBe(before + 2);
    // it follows Ctrl+Z from anywhere
    await page.locator('.canvas .canvas-view canvas').click({ position: { x: 5, y: 5 } });
    await page.keyboard.press('Control+z');
    await expect(rows).toHaveText(['Start of History', 'Add AND Gate', '▶Now', 'Add OR Gate']);
    // Esc closes it, the Canvas keeps working
    await win.locator('.histlist').focus();
    await page.keyboard.press('Escape');
    await expect(win).toBeHidden();
  } finally {
    await r.close();
  }
});

test('Analyze Circuit (the original\'s, read only): the circuit menu, the simulated table and the expressions from it; a multi-bit pin stops it', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, SUB));
    await drawn(page);
    await circuitMenu(page, 'half_adder', 'Analyze Circuit');
    const d = page.getByRole('dialog', { name: 'Combinational Analysis: half_adder' });
    await expect(d).toBeVisible();
    await expect(d.locator('.toolnote')).toContainText('시뮬레이션해 진리표를 만들었습니다');
    await expect(d.locator('.tooltab[aria-selected="true"]')).toHaveText('Table');
    await expect(d.locator('table.truth thead th')).toHaveText(['a', 'b', 's', 'c']);
    await expect(d.locator('table.truth tbody tr')).toHaveCount(4);
    await expect(d.locator('table.truth tbody tr').nth(3).locator('td')).toHaveText(['1', '1', '0', '1']);
    await d.locator('.tooltab', { hasText: 'Minimized' }).click();
    await expect(d.locator('.exprs dt').first()).toHaveText('s');
    await expect(d.locator('.expr .not').first()).toHaveText('a');
    await d.locator('.tooltab', { hasText: 'Inputs' }).click();
    await expect(d.locator('.varlist li')).toHaveText(['a', 'b']);
    await d.getByRole('button', { name: 'Close' }).click();
    await expect(d).toBeHidden();
    // main has a 2-bit output: the original's stop, as a fact and what to do
    await circuitMenu(page, 'main', 'Analyze Circuit');
    const m = page.getByRole('dialog', { name: 'Combinational Analysis: main' });
    await expect(m.locator('.toolnote')).toContainText('여러 비트 출력 핀이 있어 분석하지 않았습니다');
    await expect(m.locator('.tooltab')).toHaveText(['Inputs', 'Outputs']);
    await expect(m.locator('.tooltab[aria-selected="true"]')).toHaveText('Outputs');
    await page.keyboard.press('Escape');
    await expect(m).toBeHidden();
  } finally {
    await r.close();
  }
});

test('Get Circuit Statistics: the original\'s table (Simple, Unique, Recursive) and its two totals', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(page);
    await circuitMenu(page, 'main', 'Get Circuit Statistics');
    const d = page.getByRole('dialog', { name: 'main Statistics' });
    await expect(d).toBeVisible();
    await expect(d.locator('thead th')).toHaveText(['Component', 'Library', 'Simple', 'Unique', 'Recursive']);
    const want = JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/project-tools.json'), 'utf8'))['demo-datapath.circ'].main.statistics;
    await expect(d.locator('tbody tr')).toHaveCount(want.rows.length + 2);
    await expect(d.locator('tbody tr').first().locator('td')).toHaveText([want.rows[0].component, want.rows[0].library, String(want.rows[0].simple), String(want.rows[0].unique), String(want.rows[0].recursive)]);
    await expect(d.locator('tbody tr.total').last().locator('td').first()).toHaveText('TOTAL (with subcircuits)');
    await expect(d.locator('tbody tr.total').last().locator('td').nth(4)).toHaveText(String(want.with.recursive));
    await d.getByRole('button', { name: 'Close' }).click();
  } finally {
    await r.close();
  }
});

test('Create Submission (E-06): saved first, v1\'s checks and the files, Create… writes the zip where the dialog says', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const file = sample(r.dir, GATES);
    await openFile(r, file);
    await drawn(page);
    await place(page, 'AND Gate', [900, 900]);   // an edit not saved yet: saved first
    await recordCalls(r.app);
    await command(page, '제출', 'Create Submission…');
    const d = page.getByRole('dialog', { name: 'Create Submission' });
    await expect(d).toBeVisible();
    const calls = (await sentCalls(r.app)).map((c) => c.method).filter((m) => m === 'file.save' || m === 'file.submission');
    expect(calls).toEqual(['file.save', 'file.submission']);
    await expect(d.locator('.checks li')).toHaveCount(4);
    await expect(d.locator('.checks li.ok')).toHaveCount(4);
    await expect(d.locator('.checks li').first()).toContainText('파일을 저장했습니다');
    await expect(d.locator('.subfiles li')).toHaveText(['gates.circ']);
    const zip = path.join(r.dir, 'hand-in.zip');
    await answerSave(r.app, zip);
    await d.getByRole('button', { name: 'Create…' }).click();
    await expect(page.locator('.status')).toContainText('제출 파일을 만들었습니다 · hand-in.zip · 파일 1개');
    const bytes = readFileSync(zip);
    expect(bytes.subarray(0, 2).toString('latin1')).toBe('PK');
    expect(bytes.includes(Buffer.from('gates.circ'))).toBe(true);
    // Cancel: nothing written
    await command(page, 'submission', 'Create Submission…');
    await d.getByRole('button', { name: 'Cancel' }).click();
    await expect(d).toBeHidden();
    expect((await sentCalls(r.app, 'file.submission')).filter((c) => c.params.path).length).toBe(1);
  } finally {
    await r.close();
  }
});

test('Export Image (E-07): SVG with its fonts in it, PNG at 2x, PDF, from the Canvas\'s own shapes; Selection Only', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(page);
    const pick = async (format: string, scale?: string, selection = false) => {
      await command(page, 'export', 'Export Image…', selection);
      const d = page.getByRole('dialog', { name: 'Export Image' });
      await expect(d).toBeVisible();
      await d.getByLabel('Format').selectOption(format);
      if (scale) await d.getByLabel('Scale').selectOption(scale);
      if (format === 'png') await expect(d.getByLabel('Scale')).toBeEnabled();
      else await expect(d.getByLabel('Scale')).toBeDisabled();
      if (selection) await d.getByText('Selection Only').click();
      await d.getByRole('button', { name: 'Export…' }).click();
      await expect(d).toBeHidden();
    };
    const svgFile = path.join(r.dir, 'out.svg');
    await answerSave(r.app, svgFile);
    await pick('svg');
    await expect(page.locator('.status')).toContainText('내보냈습니다 · out.svg');
    const svg = readFileSync(svgFile, 'utf8');
    expect(svg.startsWith('<svg xmlns="http://www.w3.org/2000/svg"')).toBe(true);
    expect(svg).toContain('@font-face{font-family:Pretendard');
    expect(svg).toContain('data:font/woff2;base64,');
    const size = /<svg[^>]* width="([\d.]+)" height="([\d.]+)"/.exec(svg)!;
    const n = (await parts(page)).length;
    expect(svg.match(/data-part="/g)?.length).toBe(n);

    const pngFile = path.join(r.dir, 'out.png');
    await answerSave(r.app, pngFile);
    await pick('png', '2');
    await expect(page.locator('.status')).toContainText('내보냈습니다 · out.png');
    const png = decodePng(readFileSync(pngFile));
    expect(png.width).toBe(Math.round(Number(size[1]) * 2));
    expect(png.height).toBe(Math.round(Number(size[2]) * 2));
    let ink = 0;
    for (let i = 0; i < png.rgba.length; i += 4) if (png.rgba[i] < 128 && png.rgba[i + 1] < 128) ink++;
    expect(ink).toBeGreaterThan(2000);

    const pdfFile = path.join(r.dir, 'out');
    await answerSave(r.app, pdfFile);
    await pick('pdf');
    await expect(page.locator('.status')).toContainText('내보냈습니다 · out.pdf');
    const pdf = readFileSync(`${pdfFile}.pdf`);
    expect(pdf.subarray(0, 5).toString('latin1')).toBe('%PDF-');
    expect(pdf.toString('latin1')).toMatch(/\/FontFile2|\/FontFile3|\/FontFile /);
    expect(pdf.toString('latin1').match(/\/Type\s*\/Page\b/g)?.length).toBe(1);

    // Selection Only: one part picked on the Canvas
    const one = (await parts(page)).find((c) => c.name === 'Clock') ?? (await parts(page))[0];
    const at = await page.evaluate((b) => {
      const c = (window as unknown as { __hcsCanvas: CanvasApi }).__hcsCanvas;
      const rr = c.canvas.getBoundingClientRect();
      return [rr.left + (b[0] + b[2] / 2 - c.view.x) * c.view.zoom, rr.top + (b[1] + b[3] / 2 - c.view.y) * c.view.zoom];
    }, one.bounds);
    await page.mouse.click(at[0], at[1]);
    const selFile = path.join(r.dir, 'sel.svg');
    await answerSave(r.app, selFile);
    await pick('svg', undefined, true);
    await expect(page.locator('.status')).toContainText('내보냈습니다 · sel.svg');
    expect(readFileSync(selFile, 'utf8').match(/data-part="/g)?.length).toBe(1);
  } finally {
    await r.close();
  }
});

test('Print (I-134, Ctrl+P): the chosen circuits, a page each with the original\'s header', async () => {
  const out = mkdtempSync(path.join(tmpdir(), 'hcs-print-'));
  const pdf = path.join(out, 'printed.pdf');
  const r2 = await launch(undefined, { env: { HCS_PRINT_TO_PDF: pdf } });
  const { page } = r2;
  try {
    await openFile(r2, sample(r2.dir, DATAPATH));
    await drawn(page);
    await page.locator('.canvas .canvas-view canvas').click({ position: { x: 5, y: 5 } });
    await page.keyboard.press('Escape');
    await page.keyboard.press('Control+p');
    const d = page.getByRole('dialog', { name: 'Print' });
    await expect(d).toBeVisible();
    await expect(d.locator('.printcircs label')).toHaveText(['main', 'regfile', 'alu']);
    await expect(d.getByLabel('Header')).toHaveValue('%n (%p of %P)');
    // none chosen: it says so and stays
    await d.locator('.printcircs input').first().uncheck();
    await d.getByRole('button', { name: 'Print…' }).click();
    await expect(d.locator('.hint.err')).toContainText('하나 이상');
    await d.locator('.printcircs input').first().check();
    await d.locator('.printcircs input').nth(2).check();
    await d.getByRole('button', { name: 'Print…' }).click();
    await expect(d).toBeHidden();
    await expect(page.locator('.status')).toContainText('인쇄로 보냈습니다 · 2쪽');
    expect(existsSync(pdf)).toBe(true);
    const text = readFileSync(pdf).toString('latin1');
    expect(text.match(/\/Type\s*\/Page\b/g)?.length).toBe(2);
  } finally {
    await r2.close();
    rmSync(out, { recursive: true, force: true });
  }
});
