/* The courses' tutorials with the real engine (N-18, D-161): every practice
   step done the student's way -- the search palette, a click on the Canvas,
   wires dragged with the Wire tool, pokes, Quick Attributes, Ctrl+=, a part
   from the Components list, a double click, a message, Signal Flow, F10,
   Previous Cycle, the Attributes panel, Load Program…'s dialog, F5 -- and the
   tutorial going on because the engine's facts say it is done (a wire at
   the gate's input, Y = 1, no message, the program loaded, the Console's
   exit), each result beat waiting for [다음].  Opt-in, as the other
   real-engine tests:

     ./gradlew :engine:stage
     HCS_E2E_REAL_ENGINE=1 HCS_JAVA=<a Java 21>/bin/java xvfb-run -a npx playwright test real-engine-tutorial */

import { expect, test, type Page } from '@playwright/test';
import { existsSync, readdirSync } from 'node:fs';
import path from 'node:path';

import { answerOpen, canvasSettled, launch, repo, type LaunchOptions, type Running } from './harness.ts';

const JAR = path.join(repo, 'engine/build/stage/hcs-engine.jar');
const real: LaunchOptions['env'] = { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: JAR };

test.skip(process.env.HCS_E2E_REAL_ENGINE !== '1', 'opt-in: HCS_E2E_REAL_ENGINE=1 with engine/build/stage built');

type Track = 'logic' | 'architecture';
const shown = (page: Page, track: Track) => page.evaluate((k) => (window as unknown as { __tutorials: Record<string, { shown: { id: string; result: boolean } }> }).__tutorials[k].shown, track);
async function at(page: Page, track: Track, id: string, result = false): Promise<void> {
  await expect.poll(async () => { const s = await shown(page, track); return `${s.id}${s.result ? ' result' : ''}`; }, { timeout: 30_000 }).toBe(`${id}${result ? ' result' : ''}`);
  await page.waitForTimeout(300);   // the view's animation
  await canvasSettled(page);
}
const next = (page: Page) => page.locator('.tut-card .tut-next').click();
// A result beat waits: still there after a while, then [다음].
async function resultWaits(page: Page, track: Track, id: string): Promise<void> {
  await at(page, track, id, true);
  await page.waitForTimeout(700);
  expect((await shown(page, track)).result, `${id}: the result waits for [다음]`).toBe(true);
  await next(page);
}
// A circuit point on the screen (the Canvas's view now).
async function screen(page: Page, x: number, y: number): Promise<[number, number]> {
  return page.evaluate(([cx, cy]) => {
    const c = (window as unknown as { __hcsCanvas: { view: { x: number; y: number; zoom: number }; canvas: HTMLCanvasElement } }).__hcsCanvas;
    const r = c.canvas.getBoundingClientRect();
    return [r.left + (cx - c.view.x) * c.view.zoom, r.top + (cy - c.view.y) * c.view.zoom] as [number, number];
  }, [x, y]);
}
async function click(page: Page, x: number, y: number, count = 1): Promise<void> {
  const [sx, sy] = await screen(page, x, y);
  await page.mouse.click(sx, sy, { clickCount: count });
}
async function drag(page: Page, from: [number, number], to: [number, number]): Promise<void> {
  const [ax, ay] = await screen(page, ...from);
  const [bx, by] = await screen(page, ...to);
  await page.mouse.move(ax, ay);
  await page.mouse.down();
  await page.mouse.move((ax + bx) / 2, ay, { steps: 4 });
  await page.mouse.move(bx, by, { steps: 8 });
  await page.mouse.up();
}
// The middle of the step's i-th box (a wire's box: the wire itself).
async function clickTarget(page: Page, track: Track, i: number): Promise<void> {
  const [x, y] = await page.evaluate(([k, n]) => {
    const t = (window as unknown as { __tutorials: Record<string, { shown: { targets: { left: number; top: number; right: number; bottom: number }[] } }> }).__tutorials[k as string].shown.targets[n as number];
    return [(t.left + t.right) / 2, (t.top + t.bottom) / 2];
  }, [track, i]);
  await page.mouse.click(x, y);
}
// A toolbar tool picked, as a student does (a moment before the first press on the Canvas).
async function tool(page: Page, name: string): Promise<void> {
  await page.locator(`.toolbar [data-unit="${name}"]`).click();
  await page.waitForTimeout(300);
}
async function begin(r: Running, track: Track): Promise<void> {
  await r.page.getByRole('button', { name: track === 'logic' ? /논리설계/ : /컴퓨터구조/ }).click();
  await r.page.getByRole('button', { name: /튜토리얼 보기/ }).click();
  await expect(r.page.locator('.titlebar .coursechip')).toHaveText(track === 'logic' ? '논리설계 및 실험' : '컴퓨터구조');
}

test('논리설계 및 실험 with the real engine: every practice step done the student\'s way goes on by the engine\'s facts', async () => {
  test.setTimeout(240_000);
  expect(existsSync(JAR), `${JAR}: ./gradlew :engine:stage`).toBe(true);
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await begin(r, 'logic');
    await at(page, 'logic', 'L1');
    await next(page);
    // L2: the Components list's search, and, the AND Gate into hand
    await at(page, 'logic', 'L2');
    await page.locator('.upper .compsearch-input').fill('and');
    await page.locator('.upper .compresults button.tool', { hasText: /^AND Gate/ }).first().click();
    // L3: one click in the AND Gate place (the gate's output where actions.json has it)
    await at(page, 'logic', 'L3');
    await click(page, 320, 140);
    // L4: three wires dragged with the Wire tool
    await at(page, 'logic', 'L4');
    await tool(page, 'Wire');
    await drag(page, [120, 120], [270, 120]);
    await drag(page, [120, 200], [270, 160]);
    await drag(page, [320, 140], [440, 160]);
    await resultWaits(page, 'logic', 'L4');
    // L5: Poke A and B
    await at(page, 'logic', 'L5');
    await tool(page, 'Poke');
    await click(page, 110, 120);
    await page.waitForTimeout(300);
    await click(page, 110, 200);
    await resultWaits(page, 'logic', 'L5');
    // L6: the gate chosen, Quick Attributes' Label, g1
    await at(page, 'logic', 'L6');
    await click(page, 295, 140);
    await page.locator('.quickbar:not([hidden]) [data-attr="label"]').click();
    await page.keyboard.type('g1');
    await page.keyboard.press('Enter');
    await resultWaits(page, 'logic', 'L6');
    // L7: Ctrl+= up to 150 %
    await at(page, 'logic', 'L7');
    for (let i = 0; i < 3; i += 1) await page.keyboard.press('Control+=');
    await resultWaits(page, 'logic', 'L7');
    // L8: half_adder from the Components list, placed where its inputs meet the wires
    await at(page, 'logic', 'L8');
    await page.locator('.upper .comptree button.tool', { hasText: /^half_adder$/ }).click();
    await click(page, 290, 360);
    // L9: the Poke tool's double click goes in (the Edit tool's edits the label)
    await at(page, 'logic', 'L9');
    await tool(page, 'Poke');
    await click(page, 250, 360, 2);
    await resultWaits(page, 'logic', 'L9');
    await at(page, 'logic', 'L10');
    await next(page);
    // L11: the clock's message
    await at(page, 'logic', 'L11');
    await page.locator('.msglist button.msg', { hasText: 'count' }).click();
    // L12: the Clock wired to the count register: no message
    await at(page, 'logic', 'L12');
    await tool(page, 'Wire');
    await drag(page, [700, 380], [740, 320]);
    await resultWaits(page, 'logic', 'L12');
    await expect(page.locator('.status .msgcount')).toHaveText('No messages');
    // L13: Signal Flow on Click, the count register's output wire
    await at(page, 'logic', 'L13');
    // (the step put the Edit tool in hand, Signal Flow on Click on)
    await clickTarget(page, 'logic', 1);   // the output wire the step boxes
    await resultWaits(page, 'logic', 'L13');
    // L14: F10
    await at(page, 'logic', 'L14');
    await page.keyboard.press('F10');
    await resultWaits(page, 'logic', 'L14');
    // L15: Previous Cycle
    await at(page, 'logic', 'L15');
    await page.locator('.cycleview .cbar button', { hasText: 'Previous Cycle' }).click();
    await resultWaits(page, 'logic', 'L15');
    await at(page, 'logic', 'L16');
    await page.locator('.tut-card .tut-finish').click();
    await expect(page.locator('.wcard')).toBeVisible();
  } catch (e) {
    // what the window showed when a step did not go on (CI keeps test-results/)
    await page.screenshot({ path: test.info().outputPath('failed.png') }).catch(() => {});
    throw e;
  } finally {
    await r.close();
  }
});

test('컴퓨터구조 with the real engine: the message, the tunnel renamed, Load Program…, a cycle, the Cycle View, Signal Flow, Run to the exit', async () => {
  test.setTimeout(240_000);
  expect(existsSync(JAR), `${JAR}: ./gradlew :engine:stage`).toBe(true);
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await begin(r, 'architecture');
    for (const id of ['C1', 'C2', 'C3']) { await at(page, 'architecture', id); await next(page); }
    // C4: the misspelt tunnel's message
    await at(page, 'architecture', 'C4');
    await page.locator('.msglist button.msg', { hasText: 'RegWirte' }).click();
    // C5: the tunnel chosen, its Label in the Attributes panel
    await at(page, 'architecture', 'C5');
    const tunnel = await page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, { name: string; loc: [number, number]; attrs: Record<string, string> }> } } }).__hcsCanvas;
      return [...c.scene.components.values()].find((k) => k.name === 'Tunnel' && k.attrs.label === 'RegWirte')!.loc;
    });
    await click(page, tunnel[0] - 9, tunnel[1]);
    const label = page.locator('.atable tr[data-attr="label"] input');
    await label.fill('RegWrite');
    await label.press('Enter');
    await resultWaits(page, 'architecture', 'C5');
    await expect(page.locator('.status .msgcount')).toHaveText('No messages');
    // C6: Load Program…, the dialog's tutorial.hmx beside the example's copy
    await at(page, 'architecture', 'C6');
    const runs = readdirSync(r.userData).filter((n) => n.startsWith('run-')).map((n) => path.join(r.userData, n));
    const copy = runs.flatMap((d) => readdirSync(d).filter((n) => n.startsWith('tutorial-')).map((n) => path.join(d, n, 'tutorial.hmx'))).find(existsSync)!;
    await answerOpen(r.app, copy);
    await tool(page, 'Load Program…');
    await page.locator('dialog.loadsummary[open] .btn.primary').click();
    await resultWaits(page, 'architecture', 'C6');
    await at(page, 'architecture', 'C7');
    await next(page);
    // C8: F10: the PC after the entry
    await at(page, 'architecture', 'C8');
    await page.keyboard.press('F10');
    await resultWaits(page, 'architecture', 'C8');
    await expect(page.locator('.status')).toContainText('0x00400004');
    // C9: Previous Cycle
    await at(page, 'architecture', 'C9');
    await page.locator('.cycleview .cbar button', { hasText: 'Previous Cycle' }).click();
    await resultWaits(page, 'architecture', 'C9');
    for (const id of ['C10', 'C11']) { await at(page, 'architecture', id); await next(page); }
    // C12: Signal Flow from the PC's wire
    await at(page, 'architecture', 'C12');
    // (the step put the Edit tool in hand, Signal Flow on Click on)
    await clickTarget(page, 'architecture', 1);   // the PC's wire the step boxes
    await resultWaits(page, 'architecture', 'C12');
    // C13: F5 until the Console's exit: "sum = 14"
    await at(page, 'architecture', 'C13');
    await page.keyboard.press('F5');
    await at(page, 'architecture', 'C13', true);
    await expect(page.locator('.panel.bottom .consoletext')).toContainText('sum = 14');
    await page.keyboard.press('Escape');   // Esc stops the clock first (not the tutorial)
    await expect(page.locator('.status .run')).toHaveCount(0);
    expect((await shown(page, 'architecture')).id).toBe('C13');
    await next(page);
    await at(page, 'architecture', 'C14');
    await page.locator('.tut-card .tut-finish').click();
    await expect(page.locator('.wcard')).toBeVisible();
  } catch (e) {
    // what the window showed when a step did not go on (CI keeps test-results/)
    await page.screenshot({ path: test.info().outputPath('failed.png') }).catch(() => {});
    throw e;
  } finally {
    await r.close();
  }
});
