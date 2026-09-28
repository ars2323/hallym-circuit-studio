/* Circuits, appearances and libraries with the real engine (N-11, D-153):
   Logisim's own code under the Circuits panel, Port Order, the appearance
   editor, Import Subcircuits and Load / Unload Library -- the main flow end
   to end, and the .circ saved after it is Logisim's (a custom <appear>, the
   imported circuit).  Opt-in, as it needs the engine built:

     ./gradlew :engine:stage
     HCS_E2E_REAL_ENGINE=1 xvfb-run -a npx playwright test real-engine-circuits */

import { expect, type Page, test } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { answerOpen, answerSave, launch, type LaunchOptions, newCircuit, openFile, repo, sample } from './harness.ts';

const JAR = path.join(repo, 'engine/build/stage/hcs-engine.jar');
const real: LaunchOptions['env'] = { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: JAR };

test.skip(process.env.HCS_E2E_REAL_ENGINE !== '1', 'opt-in: HCS_E2E_REAL_ENGINE=1 with engine/build/stage built');

async function circuitsTab(page: Page): Promise<void> {
  await page.locator('.panel.upper .ptab', { hasText: 'Circuits' }).click();
  await page.locator('.circlist').waitFor();
}
// The file's libraries as the engine has them (model.libraries).
const loaded = (page: Page) => page.evaluate(async () => {
  const a = (window as unknown as { app: { call<T>(m: string, p: unknown): Promise<T> } }).app;
  return (await a.call<{ loaded: { name: string }[] }>('model.libraries', { fileId: 'f1' })).loaded.map((l) => l.name);
});
const circuitNames = (page: Page) => page.locator('.circlist li button .mono').allInnerTexts();
async function circuitMenu(page: Page, name: string, item: string): Promise<void> {
  await page.locator('.circlist li button', { hasText: name }).first().click({ button: 'right' });
  await page.locator('.ovmenu button', { hasText: item }).first().click();
}

test('the Circuits panel, Port Order (the question, then Apply), the appearance editor and Undo, with Logisim\'s own code; the saved file has the custom appearance', async () => {
  expect(existsSync(JAR), `${JAR}: ./gradlew :engine:stage`).toBe(true);
  const r = await launch(undefined, { env: real });
  const { page, app } = r;
  try {
    await openFile(r, sample(r.dir, 'tests/circ/subcircuit.circ'));
    await circuitsTab(page);
    await expect.poll(() => circuitNames(page)).toEqual(['main', 'half_adder']);
    // Add Circuit: Logisim's name check, then the new circuit
    await page.locator('.circbar button', { hasText: 'Add Circuit' }).click();
    await page.locator('dialog.namedialog input').fill('half_adder');
    await page.locator('dialog.namedialog input').press('Enter');
    await expect(page.locator('dialog.namedialog .hint.err')).toBeVisible();
    await page.locator('dialog.namedialog input').fill('extra');
    await page.locator('dialog.namedialog input').press('Enter');
    await expect.poll(() => circuitNames(page)).toEqual(['main', 'half_adder', 'extra']);
    // Remove Circuit: half_adder is used by main -- refused; extra goes
    await circuitMenu(page, 'half_adder', 'Remove Circuit');
    await expect(page.locator('.status')).toContainText('다른 회로가 half_adder 회로를 서브회로로 쓰고 있어 지우지 않았습니다');
    await circuitMenu(page, 'extra', 'Remove Circuit');
    await expect.poll(() => circuitNames(page)).toEqual(['main', 'half_adder']);
    // Port Order: main's two instances would lose connections -- the question, then Apply
    await circuitMenu(page, 'half_adder', 'Port Order…');
    const dlg = page.locator('dialog.portorder');
    const west = dlg.locator('.portcol').first().locator('.portrow');
    await west.first().getByRole('button', { name: /down/ }).click();
    await dlg.getByRole('button', { name: 'Apply' }).click();
    const q = page.locator('dialog.impact');
    await expect(q).toContainText('인스턴스 2개에서');
    await q.getByRole('button', { name: 'Apply' }).click();
    await expect(q).toHaveCount(0);
    // the appearance editor: a rectangle drawn, then Ctrl+Z takes it back
    await circuitMenu(page, 'half_adder', 'Edit Circuit Appearance');
    const shapes = page.locator('.appshapes .appshape');
    await shapes.first().waitFor();
    const n = await shapes.count();
    await expect(page.locator('.appfacts')).toContainText('Custom appearance');
    await page.locator('.apptools').getByRole('radio', { name: 'Rectangle', exact: true }).click();
    const box = (await page.locator('.appsvg').boundingBox())!;
    await page.mouse.move(box.x + 40, box.y + box.height - 120);
    await page.mouse.down();
    await page.mouse.move(box.x + 120, box.y + box.height - 60, { steps: 4 });
    await page.mouse.up();
    await expect(shapes).toHaveCount(n + 1);
    await page.keyboard.press('Control+z');
    await expect(shapes).toHaveCount(n);
    // saved: Logisim writes the circuit's own appearance
    const file = path.join(r.dir, 'saved.circ');
    await answerSave(app, file);
    await page.keyboard.press('Control+Shift+s');
    await expect(page.locator('.status .ok')).toContainText('saved.circ');
    const text = readFileSync(file, 'utf8');
    expect(text).toMatch(/<circuit name="half_adder">[\s\S]*<appear>[\s\S]*<circ-port /);
  } finally {
    await r.close();
  }
});

test('Import Subcircuits and Load / Unload Library with the real engine: the circuits come in (one undo step); a library in use is not offered to unload, another goes and comes back', async () => {
  expect(existsSync(JAR), `${JAR}: ./gradlew :engine:stage`).toBe(true);
  const r = await launch(undefined, { env: real });
  const { page, app } = r;
  try {
    await newCircuit(r);
    await circuitsTab(page);
    await answerOpen(app, sample(r.dir, 'tests/circ/subcircuit.circ'));
    await page.locator('.circbar button', { hasText: 'Import' }).click();
    const dlg = page.locator('dialog.importdialog');
    await expect(dlg).toContainText('subcircuit.circ');
    await dlg.locator('.checkrow', { hasText: /^main/ }).locator('input').uncheck();
    await dlg.getByRole('button', { name: 'Next' }).click();
    await page.locator('dialog.importdialog').getByRole('button', { name: 'Import' }).click();
    await expect.poll(() => circuitNames(page)).toEqual(['main', 'half_adder']);
    await page.keyboard.press('Control+z');
    await expect.poll(() => circuitNames(page)).toEqual(['main']);
    await page.keyboard.press('Control+y');
    await expect.poll(() => circuitNames(page)).toEqual(['main', 'half_adder']);
    // Unload Libraries…: Gates is used by half_adder (not offered, the circuit named); Memory is not -- it goes,
    // and Load Library › Built-in Library… brings it back
    await page.locator('.circbar button', { hasText: 'Libraries' }).click();
    await page.locator('.ovmenu button', { hasText: 'Unload Libraries…' }).click();
    const un = page.locator('dialog.libdialog');
    await expect(un.locator('.checkrow', { hasText: 'Gates' }).locator('input')).toBeDisabled();
    await expect(un.locator('.checkrow', { hasText: 'Gates' })).toContainText('half_adder');
    await un.locator('.checkrow', { hasText: 'Memory' }).locator('input').check();
    await un.getByRole('button', { name: 'Unload' }).click();
    await expect.poll(() => loaded(page)).not.toContain('Memory');
    await page.locator('.circbar button', { hasText: 'Libraries' }).click();
    await page.locator('.ovmenu button', { hasText: 'Load Library' }).hover();
    await page.locator('.ovmenu button', { hasText: 'Built-in Library…' }).click();
    const load = page.locator('dialog.libdialog');
    await load.locator('.checkrow', { hasText: 'Memory' }).locator('input').check();
    await load.getByRole('button', { name: 'Load' }).click();
    await expect.poll(() => loaded(page)).toContain('Memory');
  } finally {
    await r.close();
  }
});
