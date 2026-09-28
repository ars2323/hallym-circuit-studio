/* The installed program's first screen on Windows (D-155): CI's setup-e2e
   runs this after installed.e2e.ts, against the program the setup exe put
   in (HCS_E2E_EXE = <install folder>\HallymCircuitStudio.exe; the clip read
   from its app.asar).  As backdrop.e2e.ts does in the source tree: the
   background measured on the SCREEN (CopyFromScreen: what the display shows)
   against the clip's own frame, tinted toward the navy and blurred, at the
   lab PCs' 1920x1080 at 100, 125 and 150 % (Chromium's scale switch on the
   1920x1080 runner); and the still, the same, under prefers-reduced-motion
   (what Windows' animation effects off gives).  The numbers go to
   $HCS_E2E_REPORT/backdrop.json.

   Apart from installed.e2e.ts, whose runs count what is left on the PC:
   the screen is read here with PowerShell (which writes its own caches) --
   so this file's name does not match that step's filter ("installed"). */

import { _electron, expect, test, type ElectronApplication, type Page } from '@playwright/test';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { compare, groundRect, rawPixels, screenPixels } from './backdrop-measure.ts';
import { resize, type Running } from './harness.ts';

const exe = process.env.HCS_E2E_EXE;
const report = path.resolve(process.env.HCS_E2E_REPORT ?? 'report');

test.skip(!exe || process.platform !== 'win32', 'the installed program on Windows: HCS_E2E_EXE=<install folder>\\HallymCircuitStudio.exe (CI setup-e2e)');

function studentEnv(): Record<string, string> {
  const env: Record<string, string> = {};
  for (const [k, v] of Object.entries(process.env)) if (v !== undefined && !/^HCS_|^ELECTRON_RUN_AS_NODE$/i.test(k)) env[k] = v;
  return env;
}

async function start(scale: number, size: { width: number; height: number }): Promise<Running> {
  const app: ElectronApplication = await _electron.launch({ executablePath: exe!, args: [`--force-device-scale-factor=${scale}`], env: studentEnv(), timeout: 60_000 });
  const page: Page = await app.firstWindow();
  await page.waitForSelector('.wcard', { timeout: 60_000 });
  await resize({ app, page }, size);
  return { app, page, dir: '', home: '', userData: '', close: () => app.close() };
}

const measured: Record<string, unknown> = {};
test.afterAll(() => {
  mkdirSync(report, { recursive: true });
  writeFileSync(path.join(report, 'backdrop.json'), `${JSON.stringify(measured, null, 1)}\n`);
});

for (const [scale, size] of [[1, { width: 1920, height: 1032 }], [1.25, { width: 1536, height: 816 }], [1.5, { width: 1280, height: 672 }]] as const) {
  test(`installed: the first screen's background on the screen at 1920x1080 ${scale * 100} % -- the video tinted toward the navy and blurred; under reduced motion the still, the same`, async () => {
    test.setTimeout(120_000);
    const r = await start(scale, size);
    const { page } = r;
    try {
      expect(await page.evaluate(() => window.devicePixelRatio)).toBe(scale);
      await page.waitForSelector('.wback.playing', { timeout: 30_000 });
      expect(await page.evaluate(() => (document.querySelector('.wback video') as HTMLVideoElement).currentSrc)).toMatch(/app\.asar\/renderer\/assets\/hallym\/start\/start\.webm$/);
      await page.mouse.move(-10, -10);
      await page.evaluate(() => { (document.querySelector('.wback video') as HTMLVideoElement).playbackRate = 0.0625; });
      await page.waitForTimeout(1500);
      const rect = await groundRect(r);
      const video = compare(await screenPixels(r, rect), await rawPixels(r, rect));
      // Under reduced motion: the still, with the same processing.
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await expect.poll(() => page.evaluate(() => document.querySelector('.wback video')!.hasAttribute('src'))).toBe(false);
      await page.waitForTimeout(700); // the clip's fade out
      const still = compare(await screenPixels(r, rect), await rawPixels(r, rect));
      const said = `${scale * 100} %: video toward navy ${video.towardNavy.toFixed(3)}, sharpness ${video.sharpness.toFixed(3)}; ` +
        `still ${still.towardNavy.toFixed(3)}, ${still.sharpness.toFixed(3)}; rect ${JSON.stringify(rect)}`;
      console.log(said);
      measured[`${scale * 100}%`] = { video: { towardNavy: video.towardNavy, sharpness: video.sharpness }, still: { towardNavy: still.towardNavy, sharpness: still.sharpness }, rect };
      expect(video.towardNavy, said).toBeGreaterThan(0.4);
      expect(video.sharpness, said).toBeLessThan(0.6);
      expect(still.towardNavy, said).toBeGreaterThan(0.4);
      expect(still.sharpness, said).toBeLessThan(0.6);
    } finally {
      await r.close();
    }
  });
}
