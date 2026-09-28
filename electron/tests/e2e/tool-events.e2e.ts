/* The Canvas's typed events for the other panels (app/tool-events.ts,
   N-12's contract): hcs:selection when what is selected changes -- the
   file, the circuit, the instance path, the ids of the engine's selection
   (edit.selection, N-08). */

import { expect, test } from '@playwright/test';

import { centerOn, clickAt, emptyPoint, partBy } from './canvas-points.ts';
import { DATAPATH, launch, openFile, sample } from './harness.ts';

test('hcs:tool: taking a tool from the toolbar says its name (N-15 listens)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await page.locator('.canvas-view canvas').waitFor();
    await page.evaluate(() => {
      const w = window as unknown as { tools: string[] };
      w.tools = [];
      window.addEventListener('hcs:tool', (e) => w.tools.push((e as CustomEvent<{ tool: string }>).detail.tool));
    });
    await page.getByRole('radio', { name: 'Poke', exact: true }).click();
    await page.getByRole('radio', { name: 'Poke', exact: true }).click();   // the same tool again: nothing new
    await page.getByRole('radio', { name: 'Edit', exact: true }).click();
    expect(await page.evaluate(() => (window as unknown as { tools: string[] }).tools)).toEqual(['Poke', 'Edit']);
  } finally {
    await r.close();
  }
});

test('hcs:selection: a click on a part says its id, the file and the circuit; a click on nothing says none', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await page.locator('.canvas-view canvas').waitFor();
    await page.evaluate(() => {
      const w = window as unknown as { selections: unknown[] };
      w.selections = [];
      window.addEventListener('hcs:selection', (e) => w.selections.push((e as CustomEvent).detail));
    });
    const pc = (await partBy(page, 'PC', 'Register'))!;
    await centerOn(page, [pc.bounds[0], pc.bounds[1]], 1);
    await clickAt(page, [pc.bounds[0] + 15, pc.bounds[1] + 20]);
    await clickAt(page, await emptyPoint(page, [pc.bounds[0] - 60, pc.bounds[1] - 60]));
    await expect.poll(() => page.evaluate(() => (window as unknown as { selections: unknown[] }).selections.length)).toBe(2);
    const got = await page.evaluate(() => (window as unknown as { selections: { fileId: string; circuitId: string; path: string[]; ids: string[] }[] }).selections);
    expect(got[0]).toMatchObject({ ids: [pc.id], path: [] });
    expect(got[0].fileId).toMatch(/^f\d+$/);
    expect(got[0].circuitId).toMatch(/^c\d+$/);
    expect(got[1].ids).toEqual([]);
  } finally {
    await r.close();
  }
});
