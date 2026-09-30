/* The courses' tutorials (N-18, D-161; shared/tutorial.ts, app/tutorial/),
   with the fake engine:
   - both courses, every step and every result beat, at the lab PCs'
     1920x1080 at 100, 125 and 150 %: (1) each target's panel lit whole,
     (2) the rest of the window dimmed exactly, (3) a box on each target,
     (4) a lit place off the targets takes no click, (5) the card never
     over a box (and the middle of each target reaches the window) -- walked
     with [다음] and [건너뛰기] alone to the end;
   - a practice step goes on only on the student's own action (no [다음],
     → waits, keys it does not ask for do nothing), and a result beat waits
     for [다음];
   - the examples on disk byte for byte the same after the tutorial; the tab
     on show before is back; the caption buttons darken with the page;
   - Esc asks before stopping; within a run the course offers to go on where
     it stopped; a new start of the program begins at the course choice.
   The real engine's facts (a practice done for real) are in
   real-engine-tutorial.e2e.ts. */

import { expect, test, type Page } from '@playwright/test';
import { createHash } from 'node:crypto';
import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { overlayColor } from '../../src/renderer/shared/overlay.ts';
import { launch, newCircuit, repo, type Running } from './harness.ts';

type Track = 'logic' | 'architecture';
interface Rect { left: number; top: number; right: number; bottom: number }
interface Shown { id: string; step: number; result: boolean; targets: Rect[]; passes: Rect[]; lit: Rect[]; card: Rect | null; hits: boolean[]; did: string[] }

const SCREENS = [
  { scale: 1, size: { width: 1920, height: 1032 } },
  { scale: 1.25, size: { width: 1536, height: 816 } },
  { scale: 1.5, size: { width: 1280, height: 672 } },
];
const COURSE = { logic: /논리설계/, architecture: /컴퓨터구조/ };
const COURSE_NAME = { logic: '논리설계 및 실험', architecture: '컴퓨터구조' };
const EXAMPLES = path.join(repo, 'tests/tutorial');
const hashes = () => Object.fromEntries(readdirSync(EXAMPLES).map((n) => [n, createHash('sha256').update(readFileSync(path.join(EXAMPLES, n))).digest('hex')]));

const shown = (page: Page, track: Track): Promise<Shown> => page.evaluate((k) => (window as unknown as { __tutorials: Record<string, { shown: Shown }> }).__tutorials[k].shown, track);
const active = (page: Page, track: Track): Promise<boolean> => page.evaluate((k) => (window as unknown as { __tutorials: Record<string, { active: boolean }> }).__tutorials[k].active, track);
async function quick(page: Page): Promise<void> {
  await page.evaluate(() => { for (const t of Object.values((window as unknown as { __tutorials: Record<string, { skipAfter: number }> }).__tutorials)) t.skipAfter = 150; });
}
// The first screen's three steps (A-08, D-168): the course, then 튜토리얼 보기 -- straight to that course's step 1,
// the window in that course (its chip).
async function begin(page: Page, track: Track): Promise<void> {
  await page.getByRole('button', { name: COURSE[track] }).click();
  await page.getByRole('button', { name: /튜토리얼 보기/ }).click();
  await expect(page.locator('.titlebar .coursechip')).toHaveText(COURSE_NAME[track]);
}

// At step `id` (its result beat when `result`), laid out and settled (the Canvas's view animates).
async function atStep(page: Page, track: Track, id: string, result = false): Promise<Shown> {
  await expect.poll(async () => { const s = await shown(page, track); return `${s.id}${s.result ? ' result' : ''}`; }, { timeout: 20_000 }).toBe(`${id}${result ? ' result' : ''}`);
  let last = '';
  for (let i = 0; i < 50; i += 1) {
    await page.waitForTimeout(80);
    const s = await shown(page, track);
    const key = JSON.stringify([s.targets, s.lit, s.card]);
    if (key === last && (s.targets.length > 0 || /^(L16|C14)$/.test(id))) break;
    last = key;
  }
  return shown(page, track);
}

// The five checks of a step (v2 brief 9), and the card's own rules.
async function checkStep(page: Page, track: Track, id: string, result = false): Promise<void> {
  const s = await atStep(page, track, id, result);
  const where = `${id}${result ? ' (result)' : ''}`;
  const end = /^(L16|C14)$/.test(id);
  await expect(page.locator('.tut-card .tut-say > p'), where).toHaveCount(1);
  await expect(page.locator('.tut-card .tut-say h3'), where).toHaveCount(1);
  if (!end) expect(s.targets.length, `${where}: points at something`).toBeGreaterThan(0);
  const kind = await page.evaluate(() => [...document.querySelector('.tut-card')!.classList].find((c) => c.startsWith('kind-')));
  if (result) {
    await expect(page.locator('.tut-card.done .tut-next'), where).toBeVisible();
    await expect(page.locator('.tut-card .tut-skip'), where).toHaveCount(0);
  } else if (kind === 'kind-practice') {
    await expect(page.locator('.tut-card .tut-next'), `${where}: a practice step has no [다음]`).toHaveCount(0);
  }
  const light = await page.evaluate(({ targets, lit, passes }) => {
    const dim = document.querySelector('.tut-dim path.dim') as SVGPathElement;
    const dark = (x: number, y: number) => dim.isPointInFill(new DOMPoint(x, y));
    const inside = (r: { left: number; top: number; right: number; bottom: number }, x: number, y: number) => x >= r.left && x <= r.right && y >= r.top && y <= r.bottom;
    // (1) the area each target is in, found under the tutorial's layers, lit whole
    const areas = targets.map((t) => {
      const x = (t.left + t.right) / 2, y = (t.top + t.bottom) / 2;
      const area = document.elementsFromPoint(x, y).map((e) => e.closest('.panel, .titlebar, .status')).find((e) => e !== null);
      return area ? area.getBoundingClientRect().toJSON() as DOMRect : null;
    });
    const areaLit = areas.map((a) => !!a && lit.some((l) => l.left <= a.left + 1 && l.top <= a.top + 1 && l.right >= Math.min(a.right, innerWidth) - 1 && l.bottom >= Math.min(a.bottom, innerHeight) - 1)
      && [[a.left + 3, a.top + 3], [a.right - 3, a.top + 3], [a.left + 3, a.bottom - 3], [a.right - 3, a.bottom - 3], [(a.left + a.right) / 2, (a.top + a.bottom) / 2]]
        .filter(([x, y]) => y < innerHeight && x < innerWidth).every(([x, y]) => !dark(x, y)));
    // (2) every point of a grid: dark exactly where no lit area is
    let wrong = 0;
    const edge = (x: number, y: number) => lit.some((l) => (Math.abs(x - l.left) < 1.5 || Math.abs(x - l.right) < 1.5) && y >= l.top - 1.5 && y <= l.bottom + 1.5
      || (Math.abs(y - l.top) < 1.5 || Math.abs(y - l.bottom) < 1.5) && x >= l.left - 1.5 && x <= l.right + 1.5);
    for (let x = 5; x < innerWidth; x += 37) {
      for (let y = 5; y < innerHeight; y += 29) if (!edge(x, y) && dark(x, y) === lit.some((l) => inside(l, x, y))) wrong += 1;
    }
    // (3) a ring on each target
    const rings = [...document.querySelectorAll('.tut-ring')].map((e) => e.getBoundingClientRect());
    const boxed = targets.map((t) => rings.some((r) => r.left <= t.left + 0.5 && r.top <= t.top + 0.5 && r.right >= t.right - 0.5 && r.bottom >= t.bottom - 0.5));
    // (4) lit is not clickable: spots of each lit area off the targets (and off what the step lets through) take no click
    const near = (x: number, y: number) => [...targets, ...passes].some((t) => x >= t.left - 6 && x <= t.right + 6 && y >= t.top - 6 && y <= t.bottom + 6);
    const refused = lit.map((l) => {
      const spots = [[l.left + 6, l.top + 6], [l.right - 6, l.bottom - 6], [l.right - 6, l.top + 6], [l.left + 6, l.bottom - 6], [(l.left + l.right) / 2, (l.top + l.bottom) / 2],
        [(l.left + l.right) / 2, l.top + 12], [l.left + 12, (l.top + l.bottom) / 2]]
        .filter(([x, y]) => !near(x, y) && x > 0 && y > 0 && x < innerWidth && y < innerHeight);
      return spots.every(([x, y]) => !!document.elementFromPoint(x, y)?.closest('.tut'));
    });
    // the middle of each target reaches the window, not the tutorial's layers
    const reach = targets.map((t) => { const e = document.elementFromPoint((t.left + t.right) / 2, (t.top + t.bottom) / 2); return !!e && !e.closest('.tut'); });
    const chars = [...document.querySelectorAll('img.char')].filter((e) => e.checkVisibility({ visibilityProperty: true }));
    return { areaLit, wrong, boxed, rings: rings.length, refused, reach, chars: chars.length, onCard: chars.every((e) => e.closest('.tut-card') !== null),
      messages: document.body.classList.contains('messages-shown') };
  }, { targets: s.targets, lit: s.lit, passes: s.passes });
  expect(light.areaLit, `${where}: (1) each target's panel lit whole`).toEqual(s.targets.map(() => true));
  expect(light.wrong, `${where}: (2) dark exactly outside the lit panels`).toBe(0);
  expect(light.boxed, `${where}: (3) a box on each target`).toEqual(s.targets.map(() => true));
  expect(light.rings, where).toBe(s.targets.length);
  expect(light.refused, `${where}: (4) a lit panel off the targets takes no click`).toEqual(s.lit.map(() => true));
  expect(light.reach, `${where}: the middle of each target is the window's`).toEqual(s.targets.map(() => true));
  expect(s.hits, `${where}: ${JSON.stringify(s.targets)}`).toEqual(s.targets.map(() => true));
  const w = await page.evaluate(() => [window.innerWidth, window.innerHeight]);
  const c = s.card!;
  expect(c.left >= 0 && c.top >= 0 && c.right <= w[0] && c.bottom <= w[1], `${where}: the card on screen`).toBe(true);
  for (const t of s.targets) {
    expect(t.left >= 0 && t.top >= 0 && t.right <= w[0] && t.bottom <= w[1], `${where}: target on screen ${JSON.stringify(t)}`).toBe(true);
    const apart = c.right <= t.left || t.right <= c.left || c.bottom <= t.top || t.bottom <= c.top;
    expect(apart, `${where}: (5) the card over a target ${JSON.stringify({ t, c })}`).toBe(true);
  }
  // Haram: on the card only, and never while the circuit has messages (they say it cannot work, D-143)
  expect(light.onCard, `${where}: no character but the card's`).toBe(true);
  expect(light.chars, `${where}: Haram ${light.messages ? 'away (messages)' : 'once'}`).toBe(light.messages ? 0 : 1);
  // what the step names, inside one of its boxes (UI review: L8's box held the group's head only, not half_adder)
  const named = NAMED[id];
  if (named && !result) {
    const r = await page.locator(named).first().evaluate((e) => e.getBoundingClientRect().toJSON() as Rect);
    expect(s.targets.some((t) => t.left <= r.left + 0.5 && t.top <= r.top + 0.5 && t.right >= r.right - 0.5 && t.bottom >= r.bottom - 0.5),
      `${where}: ${named} inside a box ${JSON.stringify({ r, targets: s.targets })}`).toBe(true);
  }
  if (s.did.length) console.log(`[${w[0]}] ${where}: ${s.did.join(', ')}`);
}
// The row a step tells the student to pick, which its box must hold.
const NAMED: Record<string, string> = { L8: '.upper .comptree [data-tool="/half_adder"]' };

const next = (page: Page) => page.locator('.tut-card .tut-next').click();
const skip = (page: Page) => page.locator('.tut-card .tut-skip').click({ timeout: 10_000 });

// Every step, [다음] and [건너뛰기] only; at each, the five checks; a result beat waits.
async function walk(page: Page, track: Track): Promise<string[]> {
  const seen: string[] = [];
  await expect.poll(async () => (await shown(page, track)).id, { timeout: 20_000 }).not.toBe('');
  for (let i = 0; i < 40; i += 1) {
    const s = await shown(page, track);
    const id = s.id;
    await checkStep(page, track, id, s.result);
    seen.push(`${id}${s.result ? 'r' : ''}`);
    if (await page.locator('.tut-card .tut-finish').count()) {
      await page.locator('.tut-card .tut-finish').click();
      break;
    }
    if (s.result || await page.locator('.tut-card.kind-explain').count()) {
      if (s.result) {
        await page.waitForTimeout(400);
        expect((await shown(page, track)).result, `${id}: the result waits`).toBe(true);
      }
      await next(page);
    } else {
      await skip(page);
      // Load Program's summary (C6): its OK, as the student's
      const ok = page.locator('dialog.loadsummary[open] .btn.primary');
      if (await ok.count() || await ok.waitFor({ timeout: 1500 }).then(() => true, () => false)) await ok.click();
    }
    await expect.poll(async () => { const t = await shown(page, track); return `${t.id}${t.result ? 'r' : ''}`; }, { timeout: 20_000 }).not.toBe(`${id}${s.result ? 'r' : ''}`);
  }
  return seen;
}

for (const { scale, size } of SCREENS) {
  for (const track of ['logic', 'architecture'] as Track[]) {
    test(`${track} at 1920x1080 ${scale * 100} %: every step lit, dimmed, boxed, refusing, uncovered; [건너뛰기] alone to the end; the example unchanged`, async () => {
      test.setTimeout(300_000);
      const before = hashes();
      const r = await launch(size, { switches: [`--force-device-scale-factor=${scale}`] });
      const { page } = r;
      try {
        expect(await page.evaluate(() => window.devicePixelRatio)).toBe(scale);
        await quick(page);
        await begin(page, track);
        const seen = await walk(page, track);
        const results = track === 'logic' ? ['L4', 'L5', 'L6', 'L7', 'L9', 'L12', 'L13', 'L14', 'L15'] : ['C5', 'C6', 'C8', 'C9', 'C12', 'C13'];
        const steps = track === 'logic' ? 16 : 14;
        expect(seen.filter((x) => !x.endsWith('r'))).toEqual(Array.from({ length: steps }, (_, i) => `${track === 'logic' ? 'L' : 'C'}${i + 1}`));
        expect(seen.filter((x) => x.endsWith('r'))).toEqual(results.map((x) => `${x}r`));
        await expect.poll(() => active(page, track)).toBe(false);
        // the example's copy closed; nothing else was open: the first screen
        await expect(page.locator('.tut')).toHaveCount(0);
        await expect(page.locator('.filebar .ptab')).toHaveCount(0);
        await expect(page.locator('.wcard')).toBeVisible();
        expect(hashes()).toEqual(before);
      } finally {
        await r.close();
      }
    });
  }
}

test.describe(() => {
  let r: Running;
  test.beforeEach(async () => { r = await launch(); });
  test.afterEach(async () => { await r.close(); });

  test('a practice step goes on only on the student\'s own action; a result beat waits for [다음]', async () => {
    test.setTimeout(120_000);
    const { page } = r;
    await begin(page, 'logic');   // [건너뛰기] after its six seconds
    await atStep(page, 'logic', 'L1');
    await page.keyboard.press('ArrowRight');
    await atStep(page, 'logic', 'L2');
    // no [다음]; → waits; keys the step does not ask for do nothing (F5 would run the clock, Ctrl+S save)
    await expect(page.locator('.tut-card .tut-next')).toHaveCount(0);
    await page.keyboard.press('ArrowRight');
    await page.keyboard.press('F5');
    await page.keyboard.press('Control+s');
    await page.waitForTimeout(700);
    expect((await shown(page, 'logic')).id).toBe('L2');
    await expect(page.locator('.status .run')).toHaveCount(0);
    await expect(page.locator('dialog[open]')).toHaveCount(0);
    await expect(page.locator('.tut-card .tut-skip')).toHaveCount(0);   // not yet: six seconds
    // the student's own action: the Components search, and, AND Gate -- in hand
    await page.locator('.upper .compsearch-input').fill('and');
    await page.locator('.upper .compresults button.tool', { hasText: /^AND Gate/ }).first().click();
    await atStep(page, 'logic', 'L3');
    // L3: a click in the AND Gate place puts it there
    const box = (await shown(page, 'logic')).targets[0];
    await page.mouse.click((box.left + box.right) / 2, (box.top + box.bottom) / 2);
    await atStep(page, 'logic', 'L4');
    // L4 waits for the three wires (the fake engine draws no nets): [건너뛰기], then its result waits
    await expect(page.locator('.tut-card .tut-skip')).toBeVisible({ timeout: 10_000 });
    await skip(page);
    await atStep(page, 'logic', 'L4', true);
    await page.keyboard.press('F10');
    await page.keyboard.press('ArrowLeft');   // ← goes back a step
    await atStep(page, 'logic', 'L3');
    await page.keyboard.press('ArrowRight');   // L3 is done already: straight on
    await atStep(page, 'logic', 'L4');
    // keys the step does not ask for never reach the window (v2 addendum 3, 2): the gate chosen, then Delete, R
    // (rotate), Ctrl+Z (undo), Ctrl+D (duplicate) -- the gate stays, as it was, and alone
    const gates = () => page.evaluate(() => [...(window as unknown as { __hcsCanvas: { scene: { components: Map<string, { name: string; facing: string | null }> } } }).__hcsCanvas.scene.components.values()]
      .filter((c) => c.name === 'AND Gate').map((c) => c.facing));
    const before = await gates();
    expect(before).toHaveLength(1);
    const g = (await shown(page, 'logic')).targets[0];
    await page.mouse.click((g.left + g.right) / 2, (g.top + g.bottom) / 2);
    for (const k of ['Delete', 'r', 'Control+z', 'Control+d', 'Backspace']) await page.keyboard.press(k);
    await page.waitForTimeout(500);
    expect(await gates()).toEqual(before);
    expect((await shown(page, 'logic')).id).toBe('L4');
  });

  test('Esc asks first; the example closes and the tab on show before is back; the course goes on from where it stopped within the run; the caption buttons darken with the page', async () => {
    test.setTimeout(120_000);
    const { page, app } = r;
    await newCircuit(r);
    const patch = () => app.evaluate(({ BrowserWindow }) => (BrowserWindow.getAllWindows()[0] as unknown as { overlayColor?: string }).overlayColor ?? '#ffffff');
    // with a file open the first screen is away: Help › Tutorial › 컴퓨터구조
    const fromMenu = async () => {
      await page.getByTitle('Menu').click();
      await page.locator('.ovmenu.barmenu').getByRole('menuitem', { name: /^Help/ }).click();
      await page.locator('.ovmenu').last().getByRole('menuitem', { name: /^Tutorial/ }).click();
      await page.locator('.ovmenu').last().getByRole('menuitem', { name: '컴퓨터구조' }).click();
    };
    await fromMenu();
    await atStep(page, 'architecture', 'C1');
    await expect(page.locator('.titlebar .coursechip')).toHaveText('컴퓨터구조');   // the window in that course (A-08)
    await expect(page.locator('.filebar .ptab')).toHaveCount(2);
    await expect.poll(patch).toBe(overlayColor(true, false));
    await page.keyboard.press('ArrowRight');
    await atStep(page, 'architecture', 'C2');
    await page.keyboard.press('ArrowRight');
    await atStep(page, 'architecture', 'C3');
    await page.keyboard.press('Escape');
    const ask = page.locator('dialog.ask[open]');
    await expect(ask).toContainText('튜토리얼을 그만둘까요?');
    await expect.poll(patch).toBe(overlayColor(true, true));
    await ask.getByRole('button', { name: 'Continue' }).click();
    expect(await active(page, 'architecture')).toBe(true);
    // a click outside the question puts only the question away: the tutorial goes on (v2 addendum 3, 2)
    await page.keyboard.press('Escape');
    await expect(ask).toBeVisible();
    await page.mouse.click(8, 500);
    await expect(page.locator('dialog.ask[open]')).toHaveCount(0);
    expect(await active(page, 'architecture')).toBe(true);
    await atStep(page, 'architecture', 'C3');
    await page.keyboard.press('Escape');
    await ask.getByRole('button', { name: 'Quit Tutorial' }).click();
    await expect.poll(() => active(page, 'architecture')).toBe(false);
    await expect.poll(patch).toBe('#ffffff');
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ']);
    await expect(page.locator('.tut')).toHaveCount(0);
    // again in this run: asked whether to go on from step 3
    await fromMenu();
    await expect(page.locator('dialog.ask[open]')).toContainText('3단계');
    await page.locator('dialog.ask[open]').getByRole('button', { name: /Resume/ }).click();
    await atStep(page, 'architecture', 'C3');
  });
});

test('a new start of the program: the course from its first step, not asked to go on (nothing kept)', async () => {
  const userData = path.join((await import('node:os')).tmpdir(), `hcs-tut-${process.pid}`);
  const first = await launch(undefined, { userData });
  await begin(first.page, 'logic');
  await atStep(first.page, 'logic', 'L1');
  await first.page.keyboard.press('ArrowRight');
  await atStep(first.page, 'logic', 'L2');
  await first.close();
  const again = await launch(undefined, { userData });
  try {
    await begin(again.page, 'logic');
    await atStep(again.page, 'logic', 'L1');
    await expect(again.page.locator('dialog.ask[open]')).toHaveCount(0);
  } finally {
    await again.close();
  }
});
