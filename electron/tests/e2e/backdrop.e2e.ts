/* The first screen's background (src/renderer/shared/backdrop.ts; derived
   from Hallym MIPS v2.5.0 electron/tests/e2e/start.e2e.ts, D-155): the
   university's video, silent, looping, from the app's own files; one video
   for every step of the card, running on from one to the other; the card
   readable over it (the card does not change while the video does, and the
   ground around it is dark); on the SCREEN, tinted toward the navy and
   blurred, at the lab PCs' 1920x1080 at 100, 125 and 150 %; the still only
   under prefers-reduced-motion (what Windows' animation effects off gives);
   nothing of it once a file is open, and back with the first screen; the
   brand's navy, quietly, when the clip cannot play.  The card's character
   is unchanged (start.e2e.ts).

   tests/e2e/windows-start.e2e.ts measures the same on the installed
   program on Windows. */

import { expect, test, type Page } from '@playwright/test';

import { compare, groundRect, rawPixels, readbackPixels, screenPixels } from './backdrop-measure.ts';
import { launch, newCircuit, type Running } from './harness.ts';

const NAVY = 'rgb(0, 32, 91)';
const clip = (page: Page) => page.evaluate(() => {
  const v = document.querySelector('.wback video') as HTMLVideoElement & { webkitAudioDecodedByteCount: number };
  return { src: v.currentSrc, hasSrc: v.hasAttribute('src'), paused: v.paused, muted: v.muted, time: v.currentTime, duration: v.duration,
           width: v.videoWidth, height: v.videoHeight, audioBytes: v.webkitAudioDecodedByteCount, network: v.networkState,
           playing: v.closest('.wback')!.classList.contains('playing') };
});
// Stops the clip at `t` seconds and waits for the frame to be on screen.
async function at(page: Page, t: number): Promise<void> {
  await page.evaluate((t) => new Promise<void>((done) => {
    const v = document.querySelector('.wback video') as HTMLVideoElement;
    v.pause();
    v.addEventListener('seeked', () => requestAnimationFrame(() => requestAnimationFrame(() => done())), { once: true });
    v.currentTime = t;
  }), t);
  await page.waitForTimeout(100);
}
// The window's pixels in `rect` (CSS pixels): their mean luminance (0..1), and the bytes themselves.
const pixels = (r: Running, rect: { x: number; y: number; width: number; height: number }) =>
  r.app.evaluate(async ({ BrowserWindow }, rect) => {
    const image = await BrowserWindow.getAllWindows()[0].webContents.capturePage(rect);
    const b = image.toBitmap(); // BGRA
    let sum = 0;
    for (let i = 0; i < b.length; i += 4) sum += (0.0722 * b[i] + 0.7152 * b[i + 1] + 0.2126 * b[i + 2]) / 255;
    return { luminance: sum / (b.length / 4), hash: b.toString('base64') };
  }, { x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width), height: Math.round(rect.height) });

test('the first screen: the university video behind the card, from the app\'s own file, silent, playing', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await page.waitForSelector('.wback.playing');
    const c = await clip(page);
    expect(c.src).toMatch(/^file:.*\/assets\/hallym\/start\/start\.webm$/); // no network: the program's own file
    expect([c.width, c.height]).toEqual([960, 540]);
    expect(c.duration).toBeGreaterThan(6); // 6.7 s: 0:00.1-0:02.6 of the source, slowed to a third
    expect(c.muted).toBe(true);
    const t0 = c.time;
    await page.waitForTimeout(1200);
    const later = await clip(page);
    expect(later.paused).toBe(false);
    expect((later.time - t0 + later.duration) % later.duration).toBeGreaterThan(0.5); // it moves
    expect(later.audioBytes).toBe(0); // no sound decoded: the file has none
    expect(await page.locator('.wback').getAttribute('aria-hidden')).toBe('true');
    // Its still is there too, under it: the clip's first frame, 960x540.
    expect(await page.locator('.wback img.still').evaluate((i: HTMLImageElement) => [i.complete, i.naturalWidth])).toEqual([true, 960]);
    // Behind the card, not over it; the card and its character as before.
    expect(await page.evaluate(() => {
      const card = document.querySelector('.wcard')!.getBoundingClientRect();
      const top = document.elementFromPoint(card.left + card.width / 2, card.top + 20);
      return !!top?.closest('.wcard');
    })).toBe(true);
    await expect(page.locator('.wcard img.char')).toHaveAttribute('src', /haram-hari-greeting\.png$/);
    // The card is glass (Hallym MIPS v2.6.0, D-169): white at .82 blurring what is behind it, a lighter shadow.
    await expect(page.locator('.wcard')).toHaveCSS('background-color', 'rgba(255, 255, 255, 0.82)');
    await expect(page.locator('.wcard')).toHaveCSS('backdrop-filter', 'blur(18px) saturate(1.2)');
    await expect(page.locator('.wcard')).toHaveCSS('box-shadow', 'rgba(0, 16, 46, 0.25) 0px 10px 40px 0px');
    // The processing: CSS on the video and the still, a navy layer over them, under the whole window.
    for (const sel of ['.wback video', '.wback img.still']) await expect(page.locator(sel)).toHaveCSS('filter', 'blur(8px) saturate(1.25) sepia(0.1) hue-rotate(-6deg)');
    await expect(page.locator('.wback')).toHaveCSS('position', 'fixed');
    const after = await page.evaluate(() => { const a = getComputedStyle(document.querySelector('.wback')!, '::after'); return [a.backgroundImage, a.backgroundColor]; });
    expect(after[0]).toContain('rgba(0, 20, 56, 0.35)');
    expect(after[1]).toBe('rgba(0, 32, 91, 0.4)');
    // The bars over it are dark glass, their words and icons white (body.first-screen).
    for (const sel of ['.titlebar', '.status']) {
      await expect(page.locator(sel)).toHaveCSS('background-color', 'rgba(0, 20, 56, 0.3)');
      await expect(page.locator(sel)).toHaveCSS('backdrop-filter', 'blur(16px)');
    }
    await expect(page.locator('.titlebar .appname')).toHaveCSS('color', 'rgb(255, 255, 255)');
    await expect(page.locator('.titlebar .iconbtn img').first()).toHaveCSS('filter', 'brightness(0) invert(1)');
  } finally {
    await r.close();
  }
});

test('every step of the first screen: one background, which runs on from one to the other', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await page.waitForSelector('.wback.playing');
    // Marks the element and counts every (re)load of it.
    await page.evaluate(() => {
      const v = document.querySelector('.wback video') as HTMLVideoElement & { mark?: string };
      v.mark = 'first';
      (window as unknown as { loads: number }).loads = 0;
      for (const e of ['loadstart', 'emptied']) v.addEventListener(e, () => { (window as unknown as { loads: number }).loads++; });
    });
    const same = () => page.evaluate(() => ({
      mark: (document.querySelector('.wback video') as HTMLVideoElement & { mark?: string } | null)?.mark ?? null,
      backs: document.querySelectorAll('.wback').length,
      loads: (window as unknown as { loads: number }).loads,
    }));
    let before = (await clip(page)).time;
    for (const go of [/논리설계 및 실험/, /바로 시작/, /이전/, /이전/]) {
      await page.getByRole('button', { name: go }).click();
      await page.waitForTimeout(700);
      expect(await same()).toEqual({ mark: 'first', backs: 1, loads: 0 });
      const now = await clip(page);
      expect(now.playing).toBe(true);
      expect(now.paused).toBe(false);
      const moved = (now.time - before + now.duration) % now.duration;
      expect(moved).toBeGreaterThan(0.3); // on from where it was, not from the start
      expect(moved).toBeLessThan(3);
      before = now.time;
    }
    await expect(page.getByRole('button', { name: /논리설계 및 실험/ })).toBeVisible();
  } finally {
    await r.close();
  }
});

test('the glass card stays readable over the video (Hallym MIPS v2.6.0, D-169): light at every frame, its secondary text 4.5:1 or better, barely moving while the video does; the ground around it dark', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await page.waitForSelector('.wback.playing');
    await page.mouse.move(-10, -10);
    const card = (await page.locator('.wcard').boundingBox())!;
    const stage = (await page.locator('.stage-welcome').boundingBox())!;
    const inner = { x: card.x + 16, y: card.y + 16, width: card.width - 32, height: card.height - 32 }; // inside the rounded corners
    // A strip of the ground: above the card, or (a short window) beside it.
    const ground = card.y - stage.y > 60
      ? { x: stage.x, y: stage.y, width: stage.width, height: card.y - stage.y - 30 }
      : { x: stage.x, y: stage.y, width: Math.max(24, card.x - stage.x - 30), height: stage.height };
    const cards: number[] = [], grounds: string[] = [];
    // --text-2-glass (#4b5563): its relative luminance, and the contrast over the card's mean at each frame
    const lin = (v: number) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4);
    const text = 0.2126 * lin(0x4b / 255) + 0.7152 * lin(0x55 / 255) + 0.0722 * lin(0x63 / 255);
    for (const t of [0.5, 3.0, 5.5]) {
      await at(page, t);
      const c = await pixels(r, inner), g = await pixels(r, ground);
      cards.push(c.luminance);
      grounds.push(g.hash);
      expect(g.luminance, `the ground at ${t} s`).toBeLessThan(0.4);
      expect(c.luminance, `the card at ${t} s`).toBeGreaterThan(0.8);   // measured 0.850 (the opaque card was 0.9+)
      expect((lin(c.luminance) + 0.05) / (text + 0.05), `the secondary text over the card at ${t} s`).toBeGreaterThanOrEqual(4.5);
    }
    expect(new Set(grounds).size).toBe(3); // the video does change behind it
    expect(Math.max(...cards) - Math.min(...cards)).toBeLessThan(0.01);   // the glass lets it through, blurred: measured 0.0005
  } finally {
    await r.close();
  }
});

// As the SCREEN shows it, while the video plays (slowed, so that the frame
// holds while it is captured): the background moved toward the navy by the
// tint, and blurred -- against the clip's own frame at the same place.  The
// compositor's readback is not enough: on Windows Hallym MIPS saw it have
// both while the screen had neither (tests/e2e/backdrop-measure.ts).  At
// the lab PCs' three scales: the blur is in CSS pixels, so it grows with
// the scale and stays as strong at the clip's own scale.
// The window maximised over the taskbar (tools/e2e-widths.ts), at that scale.
for (const [scale, size] of [[1, { width: 1920, height: 1032 }], [1.25, { width: 1536, height: 816 }], [1.5, { width: 1280, height: 672 }]] as const) {
  test(`the background on the screen at 1920x1080 ${scale * 100} %, while the video plays: tinted toward the navy and blurred, against the raw frame`, async () => {
    const r = await launch(size, { switches: [`--force-device-scale-factor=${scale}`] });
    const { page } = r;
    try {
      expect(await page.evaluate(() => window.devicePixelRatio)).toBe(scale);
      await page.waitForSelector('.wback.playing');
      await page.mouse.move(-10, -10);
      await page.evaluate(() => { (document.querySelector('.wback video') as HTMLVideoElement).playbackRate = 0.0625; });
      await page.waitForTimeout(1500);
      expect((await clip(page)).paused).toBe(false);
      const rect = await groundRect(r);
      const raw = await rawPixels(r, rect);
      const onScreen = compare(await screenPixels(r, rect), raw);
      const inReadback = compare(await readbackPixels(r, rect), raw);
      const said = `${scale * 100} %: screen: toward navy ${onScreen.towardNavy.toFixed(3)}, sharpness ${onScreen.sharpness.toFixed(3)}; ` +
        `readback: ${inReadback.towardNavy.toFixed(3)}, ${inReadback.sharpness.toFixed(3)}; rect ${JSON.stringify(rect)}`;
      console.log(said);
      expect(onScreen.towardNavy, said).toBeGreaterThan(0.35); // the tint (v2.6.0, D-169: navy .4, darker at the edges): measured 0.43-0.44 above the card
      expect(onScreen.sharpness, said).toBeLessThan(0.6);     // the blur: about 0.3; unblurred, about 1
    } finally {
      await r.close();
    }
  });
}

test('prefers-reduced-motion: the still, and no video loaded, from the start or when it is turned on', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await page.waitForSelector('.wback.playing');
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await expect.poll(async () => { const c = await clip(page); return [c.hasSrc, c.playing]; }).toEqual([false, false]);
    await expect(page.locator('.wback img.still')).toBeVisible();
    await expect(page.locator('.wback video')).toHaveCSS('opacity', '0');
    // The still has the same processing.
    await expect(page.locator('.wback img.still')).toHaveCSS('filter', 'blur(8px) saturate(1.25) sepia(0.1) hue-rotate(-6deg)');
    // From the start: the window loaded again with the preference already on.
    await page.reload();
    await page.waitForSelector('.wcard');
    await page.waitForTimeout(800);
    const c = await clip(page);
    expect([c.hasSrc, c.playing, c.network]).toEqual([false, false, 0]); // NETWORK_EMPTY: never loaded
    await expect(page.locator('.wback img.still')).toBeVisible();
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await page.waitForSelector('.wback.playing');
  } finally {
    await r.close();
  }
});

test('a circuit on screen has no video: it is unloaded once a file is open, and back with the first screen', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await page.waitForSelector('.wback.playing');
    await newCircuit(r);
    await expect(page.locator('.wback')).toBeHidden();
    const c = await clip(page);
    expect([c.hasSrc, c.paused, c.playing]).toEqual([false, true, false]);
    // The last file closed: the first screen, and the video with it.
    await page.locator('.filebar .tabclose').first().click();
    await expect(page.locator('.wcard')).toBeVisible();
    await page.waitForSelector('.wback.playing');
    expect((await clip(page)).paused).toBe(false);
  } finally {
    await r.close();
  }
});

test('a clip that cannot play: the brand\'s navy, quietly -- no still, no video, no message, never white', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await page.waitForSelector('.wback.playing');
    const status = await page.locator('.status').innerText();
    await page.evaluate(() => { const v = document.querySelector('.wback video') as HTMLVideoElement; v.src = v.src.replace('start.webm', 'missing.webm'); });
    await page.waitForSelector('.wback.failed');
    await expect(page.locator('.wback img.still')).toBeHidden();
    await expect(page.locator('.wback video')).toBeHidden();
    await expect(page.locator('.wback')).toHaveCSS('background-color', NAVY);
    const stage = (await page.locator('.stage-welcome').boundingBox())!;
    await expect.poll(async () => (await pixels(r, { x: stage.x, y: stage.y, width: 40, height: 40 })).luminance)
      .toBeLessThan(0.2); // navy (0.12) under the tint, not white
    expect(await page.locator('.status').innerText()).toBe(status);
    await expect(page.locator('dialog[open]')).toHaveCount(0);
  } finally {
    await r.close();
  }
});
