/* The courses (A-08, D-168) with the real engine: what a file saves does
   not depend on the course.  A new 논리설계 및 실험 file with a gate saves
   the same bytes as the same edit in 컴퓨터구조 -- the original 2.7.1's seven
   libraries, no Hallym MIPS -- and a Radix Probe placed in 논리설계 brings
   the Hallym MIPS library in exactly as it does in 컴퓨터구조.  That file,
   named on the command line, starts in 논리설계 (Radix Probe is not a
   MIPS-only part); demo-datapath starts in 컴퓨터구조.  Opt-in as
   real-engine.e2e.ts. */

import { expect, test, type Page } from '@playwright/test';
import { copyFileSync, existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { answerSave, DATAPATH, launch, newCircuit, repo, type LaunchOptions, type Running } from './harness.ts';

const JAR = path.join(repo, 'engine/build/stage/hcs-engine.jar');
const real: LaunchOptions['env'] = { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: JAR };
const LOGIC = '논리설계 및 실험' as const;
const ARCH = '컴퓨터구조' as const;

test.skip(process.env.HCS_E2E_REAL_ENGINE !== '1', 'opt-in: HCS_E2E_REAL_ENGINE=1 with engine/build/stage built');

// A part from the Components list dropped on the Canvas at one place.
async function place(page: Page, group: string, part: string, count: number): Promise<void> {
  const g = page.locator('.upper .libgroup', { has: page.locator('summary', { hasText: group }) });
  if (!(await g.evaluate((e) => (e as HTMLDetailsElement).open))) await g.locator('summary').click();
  await g.getByRole('button', { name: part, exact: true }).dragTo(page.locator('.pbody.canvas'), { targetPosition: { x: 300, y: 200 } });
  await expect(page.locator('.status')).toContainText(`${count} component`);
}

// Saves the file on show as `name` (Save asks where: never saved) and returns its bytes.
async function saveAs(r: Running, name: string): Promise<string> {
  const out = path.join(r.dir, name);
  await answerSave(r.app, out);
  await r.page.keyboard.press('Control+s');
  await expect(r.page.locator('.status .ok')).toContainText(`저장했습니다 · ${name}`);
  return readFileSync(out, 'utf8');
}

// Closes the file on show (the first screen again: the course step).
async function closeAll(page: Page): Promise<void> {
  await page.locator('.filebar .tabclose').first().click();
  await expect(page.locator('.wcard')).toBeVisible();
}

const libs = (circ: string) => [...circ.matchAll(/<lib desc="([^"]+)"/g)].map((m) => m[1]);

test('the real engine and the courses: the same edit saves the same bytes in both; a Radix Probe in 논리설계 brings Hallym MIPS in as in 컴퓨터구조; the command line\'s course', async () => {
  expect(existsSync(JAR), `${JAR}: ./gradlew :engine:stage`).toBe(true);
  const r = await launch(undefined, { env: real });
  const { page } = r;
  const keep = mkdtempSync(path.join(tmpdir(), 'hcs-course-'));
  const radixLogic = path.join(keep, 'radix-logic.circ');
  try {
    const saved: Record<string, { gate: string; radix: string }> = {};
    for (const course of [LOGIC, ARCH]) {
      await newCircuit(r, course);
      await expect(page.locator('.titlebar .coursechip')).toHaveText(course);
      await place(page, 'Gates', 'AND Gate', 1);
      const gate = await saveAs(r, `gate-${course === LOGIC ? 'logic' : 'arch'}.circ`);
      await closeAll(page);
      await newCircuit(r, course);
      await place(page, 'Hallym MIPS', 'Radix Probe', 1);
      const radix = await saveAs(r, `radix-${course === LOGIC ? 'logic' : 'arch'}.circ`);
      await closeAll(page);
      saved[course] = { gate, radix };
    }
    // no Hallym MIPS without its part: the original's seven libraries, nothing of ours
    expect(saved[LOGIC].gate).toBe(saved[ARCH].gate);
    expect(libs(saved[LOGIC].gate)).toEqual(['#Wiring', '#Gates', '#Plexers', '#Arithmetic', '#Memory', '#I/O', '#Base']);
    expect(saved[LOGIC].gate).not.toContain('hcs:');
    // a Radix Probe: the library's descriptor, byte for byte as 컴퓨터구조 saves it
    expect(saved[LOGIC].radix).toBe(saved[ARCH].radix);
    expect(libs(saved[LOGIC].radix).at(-1)).toMatch(/^jar#.*hcs-mips\.jar#kr\.ac\.hallym\.hcs\.mips\.MipsLibrary$/);
    copyFileSync(path.join(r.dir, 'radix-logic.circ'), radixLogic);
  } finally {
    await r.close();
  }
  // named on the command line: Radix Probe alone is 논리설계, demo-datapath (MIPS-only parts) 컴퓨터구조
  for (const [file, course] of [[radixLogic, LOGIC], [path.join(repo, DATAPATH), ARCH]] as const) {
    const again = await launch(undefined, { env: real, args: [file], waitFor: '.filebar .ptab' });
    try {
      await expect(again.page.locator('.titlebar .coursechip')).toHaveText(course);
      await expect(again.page.locator('.courseband')).toBeHidden();
    } finally {
      await again.close();
    }
  }
  rmSync(keep, { recursive: true, force: true });
});
