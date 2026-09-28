/* The simulation with the real engine (N-07, D-145): a counter circuit
   (tests/circ/register.circ: q goes up by 3 every cycle) -- 1 Cycle, N
   Cycles 100 exactly, Run and Stop, Reset -- the Poke tool on
   demo-datapath with Logisim's own pokers (a pin's bit under the pointer, a
   register's hex digits), and N Cycles 1000 on ref-mips timed from the
   window (v1 took 25 s at 1 Hz, 10 s at its fastest).  Opt-in, as
   real-engine.e2e.ts:

     ./gradlew :engine:stage
     HCS_E2E_REAL_ENGINE=1 xvfb-run -a npx playwright test real-engine-sim */

import { expect, test } from '@playwright/test';
import { existsSync } from 'node:fs';
import path from 'node:path';

import { centerOn, clickAt, partBy, portValue } from './canvas-points.ts';
import { DATAPATH, launch, type LaunchOptions, openFile, repo, type Running, sample } from './harness.ts';

const JAR = path.join(repo, 'engine/build/stage/hcs-engine.jar');
const real: LaunchOptions['env'] = { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: JAR };

test.skip(process.env.HCS_E2E_REAL_ENGINE !== '1', 'opt-in: HCS_E2E_REAL_ENGINE=1 with engine/build/stage built');

async function drawn(r: Running): Promise<void> {
  await r.page.locator('.canvas-view canvas').waitFor();
  await r.page.waitForFunction(() => {
    const c = (window as unknown as { __hcsCanvas?: { scene: { values: Map<string, string> } | null } }).__hcsCanvas;
    return !!c?.scene && c.scene.values.size > 0;
  });
}

const bits8 = (n: number) => (n & 0xff).toString(2).padStart(8, '0');
const cycleNow = async (r: Running) => Number(/Cycle ([\d,]+)/.exec(await r.page.locator('.status').innerText())![1].replace(/,/g, ''));

test('the real engine: a counter -- 1 Cycle, N Cycles 100 exactly, Run and Stop, Reset', async () => {
  expect(existsSync(JAR), `${JAR}: ./gradlew :engine:stage`).toBe(true);
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, 'tests/circ/register.circ'));
    await drawn(r);
    const q = (await partBy(page, 'q', 'Pin'))!;
    await expect.poll(() => portValue(page, q.id)).toBe(bits8(0));
    await expect(page.locator('.status')).toContainText('Simulation On');
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 1');
    await expect.poll(() => portValue(page, q.id)).toBe(bits8(3));
    await page.getByRole('button', { name: /N Cycles/ }).click();
    await page.locator('dialog.cycles').getByRole('textbox').fill('100');
    await page.keyboard.press('Enter');
    await expect(page.locator('.status')).toContainText('Cycle 101');
    await expect.poll(() => portValue(page, q.id)).toBe(bits8(3 * 101));
    await page.keyboard.press('F5');
    await expect(page.locator('.status .run')).toHaveText('Running (1 Hz)');
    await expect.poll(() => cycleNow(r), { timeout: 5000 }).toBeGreaterThan(101);
    await page.getByRole('button', { name: /^Stop/ }).click();
    await expect(page.locator('.status .run')).toHaveCount(0);
    const stopped = await cycleNow(r);
    // Stop may land between a cycle's rising edge and its falling one (the clock left high): the register has
    // then taken the next value while the cycle is still counted as the last whole one (CI saw 103 × 3 at Cycle 102).
    await expect.poll(() => portValue(page, q.id)).toMatch(new RegExp(`^(${bits8(3 * stopped)}|${bits8(3 * (stopped + 1))})$`));
    await page.getByRole('button', { name: /Reset/ }).click();
    await expect(page.locator('.status')).toContainText('Cycle 0');
    await expect.poll(() => portValue(page, q.id)).toBe(bits8(0));
  } finally {
    await r.close();
  }
});

test('the real engine: Poke with Logisim\'s pokers -- the pin\'s bit under the pointer, a register\'s hex digits', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(r);
    await page.getByRole('radio', { name: 'Poke' }).click();
    const aluOp = (await partBy(page, 'ALUOp', 'Pin'))!;
    await centerOn(page, [aluOp.bounds[0], aluOp.bounds[1]], 2);
    const v0 = (await portValue(page, aluOp.id))!;
    await clickAt(page, [aluOp.bounds[0] + 5, aluOp.bounds[1] + 10]);    // the left half: bit 1
    await expect.poll(() => portValue(page, aluOp.id)).toBe(`${v0[0] === '1' ? '0' : '1'}${v0[1]}`);
    await clickAt(page, [aluOp.bounds[0] + 15, aluOp.bounds[1] + 10]);   // the right half: bit 0
    await expect.poll(() => portValue(page, aluOp.id)).toBe(`${v0[0] === '1' ? '0' : '1'}${v0[1] === '1' ? '0' : '1'}`);
    const pc = (await partBy(page, 'PC', 'Register'))!;
    await centerOn(page, [pc.bounds[0], pc.bounds[1]], 2);
    await clickAt(page, [pc.bounds[0] + 15, pc.bounds[1] + 20]);
    await page.keyboard.type('1f');
    await expect.poll(() => portValue(page, pc.id)).toBe('00000000000000000000000000011111');
  } finally {
    await r.close();
  }
});

test('the real engine: N Cycles 1000 on ref-mips from the window, faster than v1 (D-145)', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, 'tests/mips/ref-mips.circ'));
    await drawn(r);
    await page.getByRole('button', { name: /N Cycles/ }).click();
    await page.locator('dialog.cycles').getByRole('textbox').fill('1000');
    const t0 = Date.now();
    await page.keyboard.press('Enter');
    await expect(page.locator('.status')).toContainText('Cycle 1,000', { timeout: 30_000 });
    const ms = Date.now() - t0;
    console.log(`[measure] N Cycles 1000 on ref-mips from the window: ${ms} ms`);
    expect(ms).toBeLessThan(10_000);   // v1: 25 s at 1 Hz, 10 s at 1 kHz and faster (D-145)
  } finally {
    await r.close();
  }
});
