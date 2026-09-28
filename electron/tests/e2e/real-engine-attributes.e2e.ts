/* The Attributes panel and the right-click menu with the real engine
   (N-10, D-157): the table is Logisim's (model.attributes), a value goes
   through the original's parse (edit.setAttr → SetAttributeAction), a
   refused one comes back as badValue; the menu's facts are v1's
   (model.menu); its items run v1's and the original's actions.  Opt-in, as
   it needs the engine built:

     ./gradlew :engine:stage
     HCS_E2E_REAL_ENGINE=1 xvfb-run -a npx playwright test real-engine-attributes

   That the saved .circ is byte for byte the original tool's (the same
   change made with Logisim's own SetAttributeAction and saved by Logisim)
   is the engine's MenuEditTest.registerDataBitsSaveAsTheOriginalTableDoes;
   here the window's change is that change alone. */

import { expect, type Page, test } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { centerOn, pagePoint, partBy } from './canvas-points.ts';
import { drag, hover, parts, placeTool, selected, tool, where } from './edit-points.ts';
import { answerSave, canvasSettled, DATAPATH, launch, type LaunchOptions, newCircuit, openFile, repo, sample } from './harness.ts';

const JAR = path.join(repo, 'engine/build/stage/hcs-engine.jar');
const real: LaunchOptions['env'] = { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: JAR };

test.skip(process.env.HCS_E2E_REAL_ENGINE !== '1', 'opt-in: HCS_E2E_REAL_ENGINE=1 with engine/build/stage built');

const normalized = (text: string): string[] => text.split('\n').map((l) => l.trim()).filter(Boolean).sort();
const mid = (k: { bounds: number[] }): [number, number] => [k.bounds[0] + k.bounds[2] / 2, k.bounds[1] + k.bounds[3] / 2];
const click = (page: Page, p: [number, number], modifiers: ('Shift' | 'Alt' | 'Control')[] = []) => drag(page, p, [p], { modifiers });

async function saveAs(r: { app: Parameters<typeof answerSave>[0]; page: Page }, file: string): Promise<string> {
  await answerSave(r.app, file);
  await r.page.keyboard.press('Control+Shift+s');   // Save As: a file saved once keeps its place with Ctrl+S
  await expect(r.page.locator('.status .ok')).toContainText(path.basename(file));
  return readFileSync(file, 'utf8');
}

test('Data Bits of a register from the Attributes panel: model.changed, Undo; the saved .circ is the saved one with that attribute alone; a value the original refuses in Korean', async () => {
  expect(existsSync(JAR), `${JAR}: ./gradlew :engine:stage`).toBe(true);
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await newCircuit(r);
    await tool(page, 'Pin').click();
    await page.locator('.canvas-view canvas').waitFor();
    const w = await where(page);
    expect(await placeTool(page, { ...w, lib: 'Memory', name: 'Register', source: 'components' })).toBe(true);
    await hover(page, [300, 200]);
    await page.mouse.down();
    await page.mouse.up();
    await expect.poll(async () => (await parts(page, 'Register')).length).toBe(1);
    const reg = (await parts(page, 'Register'))[0];
    const base = await saveAs(r, path.join(r.dir, 'base.circ'));
    await click(page, mid(reg));
    const panel = page.locator('.pbody.attributes');
    await expect(panel.locator('.aname')).toHaveText('Register');
    // Logisim's own rows, names and choices (the original's Register attribute set)
    await expect(panel.locator('.atable tbody th')).toHaveText(['Data Bits', 'Trigger', 'Label', 'Label Font']);
    await expect(panel.getByLabel('Data Bits')).toHaveValue('8');
    await panel.getByLabel('Data Bits').selectOption('16');
    await expect.poll(async () => (await parts(page, 'Register'))[0]?.attrs.width).toBe('16');
    await expect(panel.getByLabel('Data Bits')).toHaveValue('16');
    const changed = await saveAs(r, path.join(r.dir, 'changed.circ'));
    // Logisim writes a part with no attribute off its default as one tag; with one, the attribute inside
    const before = '<comp lib="4" loc="(300,200)" name="Register"/>';
    expect(base).toContain(before);
    expect(changed).toBe(base.replace(before, '<comp lib="4" loc="(300,200)" name="Register">\n      <a name="width" val="16"/>\n    </comp>'));
    // Undo: one step back to 8
    await page.keyboard.press('Control+z');
    await expect.poll(async () => (await parts(page, 'Register'))[0]?.attrs.width).toBe('8');
    // a label (any text)
    await panel.getByLabel('Label', { exact: true }).fill('R1');
    await panel.getByLabel('Label', { exact: true }).press('Enter');
    await expect.poll(async () => (await parts(page, 'Register'))[0]?.attrs.label).toBe('R1');
    // a Constant's value the original's parse refuses: badValue, the Korean sentence, the value as it was
    expect(await placeTool(page, { ...w, lib: 'Wiring', name: 'Constant', source: 'components' })).toBe(true);
    await hover(page, [500, 200]);
    await page.mouse.down();
    await page.mouse.up();
    await expect.poll(async () => (await parts(page, 'Constant')).length).toBe(1);
    await expect(panel.locator('.aname')).toHaveText('Constant');
    await panel.getByLabel('Value', { exact: true }).fill('0xZZ');
    await panel.getByLabel('Value', { exact: true }).press('Enter');
    await expect(panel.locator('.aerr')).toHaveText('이 값은 Value 속성에 넣을 수 없습니다. 16진수(0x1F), 10진수(31)로 적습니다.');
    await expect.poll(async () => (await parts(page, 'Constant'))[0]?.attrs.value).toBe('0x1');
  } finally {
    await r.close();
  }
});

test('the right-click menu from the engine\'s facts: the summary lines, Select Whole Net is the engine\'s net, the P key\'s probe, Quick Attributes\' list', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await canvasSettled(page);
    const pc = (await partBy(page, 'PC'))!;
    await centerOn(page, mid(pc), 1.25);
    await canvasSettled(page);
    const right = async (p: [number, number]) => { const q = await pagePoint(page, p); await page.mouse.click(q.x, q.y, { button: 'right' }); };
    await right(mid(pc));
    await expect(page.locator('.ovmenu .mhead')).toHaveText('PC(Register) · 32 bits');
    await page.keyboard.press('Escape');
    const q = pc.ports.find((x) => x.i === 0)!;
    const onWire: [number, number] = [q.loc[0] + 20, q.loc[1]];
    await right(onWire);
    await expect(page.locator('.ovmenu .mhead')).toHaveText('Net pc · 32 bits');
    await page.locator('.ovmenu button', { hasText: 'Select Whole Net' }).click();
    const net = await page.evaluate((p) => {
      const c = (window as unknown as { __hcsCanvas: { scene: { wireAt?: unknown; wires: Map<string, { a: number[]; b: number[] }>; wireNet(id: string): { wires: string[] } | undefined } } }).__hcsCanvas;
      for (const [id, w] of c.scene.wires) {
        if (w.a[1] === p[1] && w.b[1] === p[1] && Math.min(w.a[0], w.b[0]) <= p[0] && Math.max(w.a[0], w.b[0]) >= p[0]) return c.scene.wireNet(id)?.wires ?? [];
      }
      return [];
    }, onWire);
    expect(net.length).toBeGreaterThan(1);
    await expect.poll(() => selected(page)).toEqual([...net].sort());
    // P over that wire: the engine's quick probe (hex on a bus), one undo step
    const probes = (await parts(page, 'Probe')).length;
    await page.locator('.canvas-view canvas').focus();
    await click(page, [mid(pc)[0] + 300, mid(pc)[1] + 300]);
    await hover(page, onWire);
    await page.keyboard.press('p');
    await expect.poll(async () => (await parts(page, 'Probe')).length).toBe(probes + 1);
    const added = (await parts(page, 'Probe')).find((k) => k.attrs.label === 'pc');
    expect(added?.attrs.radix).toBe('16');
    await page.keyboard.press('Control+z');
    await expect.poll(async () => (await parts(page, 'Probe')).length).toBe(probes);
    // Quick Attributes on the PC: the kind's list from the engine (Register: Data Bits, Trigger, Label)
    await click(page, mid(pc));
    const bar = page.locator('.quickbar');
    await expect(bar).toBeVisible();
    await expect(bar.locator('.qbtn:not(.qlink)')).toHaveText(['Data Bits 32', 'Trigger Rising Edge', 'Label PC']);
    await expect(bar.locator('.qhint')).toContainText('Alt+0–9: Data Bits');
  } finally {
    await r.close();
  }
});
