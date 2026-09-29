/* The screenshots' fixed conditions (tests/e2e/screen-conditions.ts, D-158,
   D-167): the first screen taken as tools/capture-screens.ts takes it, in
   two launches, is the same picture pixel for pixel -- the video held at
   3.0 s -- and the window's clock reads 10:00:00 (Seoul) and stays there. */

import { expect, test } from '@playwright/test';

import { pixelDiff } from './png.ts';
import { FIXED_MS, START_AT, startForScreens, videoAt } from './screen-conditions.ts';

const FHD = { width: 1920, height: 1032 };

async function startScreen(): Promise<{ png: Buffer; clock: { now: number; text: string; later: number }; video: { t: number; paused: boolean } }> {
  const r = await startForScreens(FHD, { motion: true });
  try {
    await videoAt(r);
    const { page } = r;
    await page.mouse.move(-10, -10);
    await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
    await page.evaluate(() => document.fonts.ready);
    await page.waitForFunction(() => [...document.images].every((i) => i.complete));
    const clock = await page.evaluate(async () => {
      const now = Date.now();
      await new Promise((ok) => setTimeout(ok, 300)); // timers still run; the clock does not move
      return { now, text: new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }), later: Date.now() };
    });
    const video = await page.evaluate(() => {
      const v = document.querySelector('.wback video') as HTMLVideoElement;
      return { t: v.currentTime, paused: v.paused };
    });
    return { png: await page.screenshot(), clock, video };
  } finally {
    await r.close();
  }
}

test('the first screen under the fixed conditions: two launches, the same pixels; the clock at 10:00:00; the video at 3.0 s', async () => {
  test.setTimeout(120_000);
  const a = await startScreen();
  const b = await startScreen();
  expect(pixelDiff(a.png, b.png)).toBe('');
  for (const { clock, video } of [a, b]) {
    expect(clock.now).toBe(FIXED_MS);
    expect(clock.later).toBe(FIXED_MS);
    expect(clock.text).toBe('10:00:00');
    expect(video).toEqual({ t: START_AT, paused: true });
  }
});

test('the held frame has the video\'s own look: its filter and transform (shared.css .wback video)', async () => {
  const r = await startForScreens(FHD, { motion: true });
  try {
    await videoAt(r);
    const look = await r.page.evaluate(() => {
      const v = getComputedStyle(document.querySelector('.wback video')!), c = getComputedStyle(document.querySelector('.wback canvas.capture-frame')!);
      return { video: [v.filter, v.transform], frame: [c.filter, c.transform], hidden: v.visibility };
    });
    expect(look.frame).toEqual(look.video);
    expect(look.video[0]).not.toBe('none');
    expect(look.hidden).toBe('hidden');
  } finally {
    await r.close();
  }
});
