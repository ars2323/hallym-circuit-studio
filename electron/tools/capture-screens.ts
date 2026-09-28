/* The fixed set of screenshots (docs/screens/README.md), every one of them
   taken here -- none by hand -- and all of them again every round, under
   the same names (derived from Hallym MIPS v2.3.0
   electron/tools/capture-screens.ts: the PNG writer, the checks and shot();
   the scenes are this app's):

     xvfb-run -a -s '-screen 0 2400x1400x24' npm run screens

   The files and steps are written below, so a round's shots can be laid
   over the last round's.  The whole window at 1920x1032 (a 1920x1080 lab
   PC, maximised over its taskbar) unless the name says otherwise; no mouse
   cursor, hover or tooltip in any of them (the pointer is moved out of the
   window and checked).  The engine is the fake one
   (tests/fake-engine/fake-engine.ts): its answers are fixed, so the same
   code gives the same pixels.  Each PNG is written without its ancillary
   chunks (metadata), losslessly, and must stay within 1.5 MB.

   SCREENS_OUT: write somewhere else. */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { answerOpen, DATAPATH, launch, openFile, repo, root, sample, type Running } from '../tests/e2e/harness.ts';

const out = process.env.SCREENS_OUT ? path.resolve(process.env.SCREENS_OUT) : path.join(root, 'docs/screens');
mkdirSync(out, { recursive: true });
const MAX_BYTES = 1536 * 1024;
const FHD = { width: 1920, height: 1032 };

// PNG without its ancillary chunks: the signature, then IHDR, PLTE, tRNS,
// IDAT and IEND only.  The pixels are untouched.
export function stripPng(file: string): number {
  const b = readFileSync(file);
  const keep = new Set(['IHDR', 'PLTE', 'tRNS', 'IDAT', 'IEND']);
  const parts = [b.subarray(0, 8)];
  for (let at = 8; at < b.length;) {
    const end = at + 12 + b.readUInt32BE(at);
    if (keep.has(b.toString('latin1', at + 4, at + 8))) parts.push(b.subarray(at, end));
    at = end;
  }
  const png = Buffer.concat(parts);
  writeFileSync(file, png);
  return png.length;
}

function written(name: string): void {
  const file = path.join(out, `${name}.png`);
  const bytes = stripPng(file);
  console.log(`wrote ${path.relative(root, file)} (${Math.round(bytes / 1024)} KB)`);
  if (bytes > MAX_BYTES) throw new Error(`${name}.png is ${bytes} bytes, over ${MAX_BYTES}: crop it`);
}

async function shot(r: Running, name: string): Promise<void> {
  const { page } = r;
  await page.mouse.move(-10, -10); // out of the window: no hover, no tooltip
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
  await page.evaluate(() => document.fonts.ready);
  await page.waitForFunction(() => [...document.images].every((i) => i.complete));
  await page.waitForTimeout(400);
  const hovered = await page.evaluate(() => document.querySelectorAll(':hover').length);
  if (hovered) throw new Error(`${name}: ${hovered} elements still hovered`);
  await page.screenshot({ path: path.join(out, `${name}.png`) });
  written(name);
}

const kill = (r: Running) => r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { kill(): void } } }).__hcs.engine.kill());

{
  const r = await launch(FHD);
  const { page } = r;
  await shot(r, 'start');
  await page.getByRole('button', { name: /튜토리얼 보기/ }).click();
  await shot(r, 'start-tutorial');
  await page.getByRole('button', { name: '← 처음으로' }).click();
  await page.getByRole('button', { name: /바로 시작/ }).click();
  await shot(r, 'start-2');
  await page.getByRole('button', { name: /새 회로/ }).click();
  await page.locator('.canvas h3').waitFor();
  await shot(r, 'new-circuit');
  await openFile(r, sample(r.dir, DATAPATH));
  await page.locator('.canvas h3', { hasText: '부품 35개' }).waitFor();
  await shot(r, 'open-file');
  await page.getByRole('tab', { name: 'Circuits' }).click();
  await page.locator('.upper .pbody:visible .list > li', { hasText: 'regfile' }).getByRole('button').click();
  await page.getByRole('button', { name: /1 Cycle/ }).click();
  await page.getByRole('button', { name: /1 Cycle/ }).click();
  await page.locator('.status', { hasText: 'Cycle 2' }).waitFor();
  await shot(r, 'circuit-tabs');
  await page.getByTitle('About').click();
  await page.locator('dialog.about[open]').waitFor();
  await shot(r, 'about');
  await page.locator('dialog.about').getByRole('tab', { name: 'Licenses' }).click();
  await page.locator('dialog.about details').nth(2).locator('summary').click();
  await page.locator('dialog.about details').nth(2).locator('pre', { hasText: 'BSD' }).waitFor();
  await shot(r, 'about-licenses');
  await page.keyboard.press('Escape');
  await answerOpen(r.app, path.join(r.dir, 'lab3.circ'));
  await page.keyboard.press('Control+o');
  await page.locator('dialog.ask').waitFor();
  await shot(r, 'dialog-error');
  await page.keyboard.press('Escape');
  await kill(r);
  await page.locator('.band', { hasText: '다시 시작했습니다' }).waitFor();
  await shot(r, 'engine-restarted');
  await r.close();
}

// Messages (N-13): a broken circuit after one cycle, a message chosen; a circuit with nothing to say.
{
  const r = await launch(FHD);
  const { page } = r;
  await openFile(r, sample(r.dir, 'electron/tests/fixtures/broken-datapath.circ'));
  await page.locator('.msg').nth(1).waitFor();
  await page.keyboard.press('F10');
  await page.locator('.msg').nth(2).waitFor();
  await page.locator('.msg').nth(1).click();
  await page.locator('.msg.on').waitFor();
  await shot(r, 'messages-list');
  await openFile(r, sample(r.dir, DATAPATH));
  await page.locator('.status .msgcount', { hasText: 'No messages' }).waitFor();
  await page.locator('.pbody.bottom .notice h3', { hasText: '메시지가 없습니다' }).waitFor();
  await shot(r, 'messages-empty');
  await r.close();
}

// The program (N-16): the summary after Load Program (ref-mips, data.hmx beside its .s), the Console after
// the program ran to its exit, the band after the .hmx was exported again cut off.  The fake engine answers
// with the real engine's words (tests/fixtures/programs.json); its clock is fixed and the zone is Seoul's.
{
  const r = await launch(FHD, { env: { TZ: 'Asia/Seoul' } });
  const { page } = r;
  const circ = sample(r.dir, 'tests/mips/ref-mips.circ');
  sample(r.dir, 'tests/hmx/hallym-mips-v2.4.0/data.s');
  const hmx = sample(r.dir, 'tests/hmx/hallym-mips-v2.4.0/data.hmx');
  await openFile(r, circ);
  await page.locator('.canvas h3', { hasText: '부품' }).waitFor();
  await answerOpen(r.app, hmx);
  await page.getByRole('button', { name: /Load Program/ }).click();
  await page.locator('dialog.loadsummary').waitFor();
  await shot(r, 'load-summary');
  await page.locator('dialog.loadsummary').getByRole('button', { name: 'OK' }).click();
  await page.getByRole('tab', { name: 'Console' }).click();
  await page.keyboard.press('F10');
  await page.locator('pre.consoletext', { hasText: 'sum = 14' }).waitFor();
  await shot(r, 'console');
  writeFileSync(hmx, readFileSync(path.join(repo, 'tests/hmx/truncated.hmx')));
  await page.locator('.progband:not([hidden])').waitFor();
  await shot(r, 'reload-kept');
  await r.close();
}

// The lab PCs at 125 % and 150 % (1536x816 and 1280x672 CSS px), and half a screen.
for (const [name, size, scale] of [
  ['lab-125', { width: 1536, height: 816 }, '1.25'],
  ['lab-150', { width: 1280, height: 672 }, '1.5'],
  ['narrow', { width: 960, height: 1032 }, '1'],
] as const) {
  const r = await launch(size, { switches: [`--force-device-scale-factor=${scale}`] });
  await openFile(r, sample(r.dir, DATAPATH));
  await r.page.locator('.canvas h3', { hasText: '부품 35개' }).waitFor();
  if (name === 'narrow') await r.page.locator('.upper').getByRole('tab', { name: 'Attributes' }).click();
  await shot(r, name);
  await r.close();
}

// No engine: the dialog (no character: an error) over the first screen and its band.
{
  const r = await launch(FHD, { env: { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: '/opt/hcs/hcs-engine.jar' } });
  await r.page.locator('dialog.ask').waitFor();
  await shot(r, 'engine-failed');
  await r.close();
}

