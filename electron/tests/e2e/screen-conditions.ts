/* The fixed conditions of the screenshots (tools/capture-screens.ts,
   docs/screens/README.md "Fixed conditions"; D-158, D-167), so that a
   round's pictures can be laid over the last round's -- and over a
   release's.  Kept apart from the capture tool so the e2e test
   (screen-conditions.e2e.ts) takes the first screen under the very same
   conditions.

   - The clock (D-167).  Every time the app shows is 10:00:00 (Seoul) on
     2026-09-28, however it gets there: the window's own Date is held there
     (Playwright's clock: Date.now and new Date() only -- timers,
     requestAnimationFrame and performance.now keep running, so the
     Canvas's animations still end), the zone is Seoul's (TZ), the fake
     engine's clock (a program's load time, a failed reload's time:
     FAKE_ENGINE_NOW) stands still there, and a recovery file's time is
     written as it (the file's mtime).  The main process shows no time of
     its own; the real engine's scenes show none either.
   - The window (D-158).  CPU raster on whole tiles and one thread, reduced
     motion but where a scene is about the video, no transition, animation
     or caret.
   - The first screen's video (D-158, D-167).  Paused at START_AT = 3.0 s
     (Hallym MIPS 2.6.0's start.jpg holds the same second), that frame
     drawn into a canvas that takes the video's place with the same look. */

import { launch, type LaunchOptions, type Running } from './harness.ts';

export const FIXED_TIME = '2026-09-28T10:00:00+09:00';
export const FIXED_MS = Date.parse(FIXED_TIME);
export const SCREEN_TZ = 'Asia/Seoul';
export const START_AT = 3.0;

// The launch's environment: the zone and the fake engine's clock.  A scene's own env comes over it.
export function screenOptions(options: LaunchOptions = {}): LaunchOptions {
  return { ...options, env: { TZ: SCREEN_TZ, FAKE_ENGINE_NOW: String(FIXED_MS), ...options.env } };
}

// The window's clock (every window of the app: the clock is the context's).
export async function fixClock(r: Running): Promise<void> {
  await r.page.clock.setFixedTime(FIXED_MS);
}

// A window with the fixed clock (the zone, the fake engine's clock, the window's Date).
export async function launchAtFixedTime(size: { width: number; height: number } | null, options: LaunchOptions = {}): Promise<Running> {
  const r = await launch(size, screenOptions(options));
  await fixClock(r);
  return r;
}

// Every window of the capture tool: no motion (prefers-reduced-motion, as a PC with Windows' animation effects off),
// no transition, animation or blinking caret (D-158: the same code, the same pixels).  motion: the scene is
// about the first screen's video (held at a second by videoAt()).
export const CAPTURE_CSS = '*, *::before, *::after { transition: none !important; animation: none !important; caret-color: transparent !important; }'
  // the first screen's video frame held in a canvas (videoAt) in the video's place; its filter and transform are the
  // video's own, read when it is held (D-167: a copy here had stayed at 2.5.0's blur(3px) scale(1.03) after 2.6.0's glass)
  + ' .wback canvas.capture-frame { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }';
// Chromium rasterising on the CPU, whole tiles, one thread: its GPU path (SwiftShader under Xvfb) and partial raster
// left a pixel of anti-aliasing at a rounded corner or a button's edge different from one run to the next.
export const CAPTURE_SWITCHES = ['--disable-gpu', '--disable-gpu-compositing', '--disable-partial-raster', '--num-raster-threads=1'];
export async function startForScreens(size: { width: number; height: number } | null, o: LaunchOptions & { motion?: boolean } = {}): Promise<Running> {
  const r = await launchAtFixedTime(size, { ...o, switches: [...CAPTURE_SWITCHES, ...(o.switches ?? [])] });
  if (!o.motion) await r.page.emulateMedia({ reducedMotion: 'reduce' });
  await r.page.addStyleTag({ content: CAPTURE_CSS });
  await r.page.evaluate(() => document.documentElement.classList.add('capture'));
  return r;
}

// The first screen's video, stopped at `t` seconds, that frame on screen (Hallym MIPS 2.5.0 capture-screens.ts).
export async function videoAt(r: Running, t = START_AT): Promise<void> {
  if (await r.page.locator('.stage-welcome').isHidden()) throw new Error('videoAt: the first screen is not on show');
  await r.page.waitForSelector('.wback.playing');
  // That frame decoded (requestVideoFrameCallback at its time), then drawn into a canvas that takes the video's place
  // with the same look (D-158): a paused video's frame reached the software compositor late now and then (the first
  // frame's still showed through in one round of two; D-167 measured a frame off in 4 launches of 10), a canvas's
  // bitmap never does.
  await r.page.evaluate((t) => new Promise<void>((done) => {
    const v = document.querySelector('.wback video') as HTMLVideoElement;
    v.pause();
    const hold = () => {
      const c = document.createElement('canvas');
      c.width = v.videoWidth;
      c.height = v.videoHeight;
      c.getContext('2d')!.drawImage(v, 0, 0);
      c.className = 'capture-frame';
      const look = getComputedStyle(v);
      c.style.filter = look.filter;
      c.style.transform = look.transform;
      v.after(c);
      v.style.visibility = 'hidden';
      requestAnimationFrame(() => requestAnimationFrame(() => done()));
    };
    const presented = () => v.requestVideoFrameCallback((_now, meta) => { if (Math.abs(meta.mediaTime - t) < 0.02) hold(); else presented(); });
    presented();
    v.currentTime = t;
  }), t);
}
