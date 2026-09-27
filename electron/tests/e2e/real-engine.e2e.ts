/* The window with the real engine (engine/, N-03) instead of the fake one:
   the same flows end to end -- hello, a new circuit, a .circ with the MIPS
   library, the clock, saving, a crash, quitting.  Opt-in, as it needs the
   engine built and a Java 21:

     ./gradlew :engine:stage          (engine/build/stage/hcs-engine.jar, hcs-mips.jar beside it)
     HCS_E2E_REAL_ENGINE=1 HCS_JAVA=<a Java 21>/bin/java xvfb-run -a npx playwright test real-engine

   CI's electron job runs the window against the fake engine only; N-04
   (the bundled runtime) makes this part of CI. */

import { expect, test } from '@playwright/test';
import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { answerOpen, answerSave, DATAPATH, launch, newCircuit, openFile, repo, sample, type LaunchOptions } from './harness.ts';

const JAR = path.join(repo, 'engine/build/stage/hcs-engine.jar');
const real: LaunchOptions['env'] = { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: JAR };

test.skip(process.env.HCS_E2E_REAL_ENGINE !== '1', 'opt-in: HCS_E2E_REAL_ENGINE=1 with engine/build/stage built');

test('the real engine: hello, a new circuit, a .circ with the MIPS library, the clock, saving', async () => {
  expect(existsSync(JAR), `${JAR}: ./gradlew :engine:stage`).toBe(true);
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await expect(page.locator('.status .engine')).toContainText('Logisim 2.7.1 · Java 21');
    await newCircuit(r);
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ']);
    await expect(page.locator('.canvas h3')).toHaveText('빈 회로입니다');
    await expect(page.locator('.upper .libgroup summary').first()).toContainText('untitled.circ');
    await openFile(r, sample(r.dir, DATAPATH));
    await expect(page.locator('.canvas h3')).toContainText('이 회로에는 부품');
    await page.getByRole('tab', { name: 'Circuits' }).click();
    await expect(page.locator('.upper .pbody:visible .list > li')).toHaveText(['main', 'regfile', 'alu']);
    await expect(page.locator('.lower .list li').first()).toBeVisible();   // its tunnels
    // The clock (docs/engine-api.md sim.*): the engine's sim.state in the status bar.
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 1');
    await page.keyboard.press('F5');
    await expect(page.locator('.status .run')).toHaveText('Running (1 Hz)');
    await page.keyboard.press('F5');
    await expect(page.locator('.status .run')).toHaveCount(0);
    await page.getByRole('button', { name: /Reset/ }).click();
    await expect(page.locator('.status')).toContainText('Cycle 0');
    // Save the new circuit: Logisim's own writer, through the engine.
    await page.locator('.filebar .ptab', { hasText: 'untitled.circ' }).click();
    const target = path.join(r.dir, 'saved.circ');
    await answerSave(r.app, target);
    await page.keyboard.press('Control+s');
    await expect(page.locator('.status .ok')).toContainText('저장했습니다 · saved.circ');
    expect(statSync(target).size).toBeGreaterThan(100);
  } finally {
    await r.close();
  }
});

test('the real engine: a broken circuit\'s Messages (N-13), the same words as the fake engine\'s fixture, and the one the clock finds', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    const fixture = JSON.parse(readFileSync(path.join(repo, 'electron/tests/fixtures/messages.json'), 'utf8')) as
      Record<string, { static: { text: { ko: string } }[]; afterCycles: { text: { ko: string } }[] }>;
    await openFile(r, sample(r.dir, 'electron/tests/fixtures/broken-datapath.circ'));
    await expect(page.locator('.msg .say')).toHaveText(fixture['broken-datapath.circ'].static.map((m) => m.text.ko));
    await expect(page.locator('.status .msgcount')).toHaveText('2 messages');
    await page.keyboard.press('F10'); // the fixture's one cycle
    await expect(page.locator('.status')).toContainText('Cycle 1');
    await expect(page.locator('.msg .say')).toHaveText(fixture['broken-datapath.circ'].afterCycles.map((m) => m.text.ko));
    await expect(page.locator('.msg[data-kind="dynamic"] .where')).toHaveText('main · Cycle 0');
    await page.locator('.msg').first().click();
    await expect(page.locator('.msg.on')).toHaveCount(1);
    await page.getByRole('button', { name: /Reset/ }).first().click();
    await expect(page.locator('.msg')).toHaveCount(2);
  } finally {
    await r.close();
  }
});

test('the real engine: its file errors in the window\'s words (a file that is not there, a file Logisim cannot read)', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    const dialog = page.locator('dialog.ask');
    await answerOpen(r.app, path.join(r.dir, 'lab3.circ'));
    await page.keyboard.press('Control+o');
    await expect(dialog.locator('.askfile')).toHaveText('File: lab3.circ');
    await expect(dialog).toContainText('그 자리에 파일이 없습니다.');
    expect(await dialog.innerText()).not.toContain(r.dir);
    await dialog.getByRole('button', { name: 'Close' }).click();
    const notCirc = path.join(r.dir, 'notes.circ');
    writeFileSync(notCirc, 'not a circuit\n');
    await answerOpen(r.app, notCirc);
    await page.keyboard.press('Control+o');
    await expect(dialog.locator('.askfile')).toHaveText('File: notes.circ');
    await expect(dialog).toContainText('Logisim이 이 파일을 회로로 읽지 못했습니다.');
  } finally {
    await r.close();
  }
});

test('the real engine ended by a crash: started again, the band, files open again', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    const file = sample(r.dir, DATAPATH);
    await openFile(r, file);
    await r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { kill(): void } } }).__hcs.engine.kill());
    await expect(page.locator('.band')).toHaveText('엔진이 멈춰서 다시 시작했습니다 · 열려 있던 파일 1개를 닫았습니다');
    await openFile(r, file);
    await expect(page.locator('.canvas h3')).toContainText('이 회로에는 부품');
  } finally {
    await r.close();
  }
});

test('the real engine and the lab-PC rule: after quit nothing in HOME but the JDK\'s font list cache (Linux)', async () => {
  const runs = mkdtempSync(path.join(tmpdir(), 'hcs-runs-'));
  const r = await launch(undefined, { env: real, userData: runs });
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await r.page.keyboard.press('F10');
    await expect(r.page.locator('.status')).toContainText('Cycle 1');
  } finally {
    await r.app.close();
  }
  await expect.poll(() => (existsSync(runs) ? readdirSync(runs) : []), { timeout: 20_000 }).toEqual([]);
  // Linux only: the JDK's fontconfig list (D-134) and the libraries' caches under Chromium (labpc.e2e.ts).
  const left = readdirSync(r.home, { recursive: true }).map(String).filter((f) => statSync(path.join(r.home, f)).isFile())
    .filter((f) => !/^\.java\/fonts\//.test(f) && !/^\.cache\/(fontconfig|mesa_shader_cache|nvidia)\//.test(f));
  expect(left).toEqual([]);
  rmSync(r.dir, { recursive: true, force: true });
  rmSync(runs, { recursive: true, force: true });
});
