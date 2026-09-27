/* The installed program, on Windows (N-23, D-148): CI's setup-e2e and
   setup-upgrade jobs install the setup exe silently and run this against it
   (HCS_E2E_EXE = <install folder>\HallymCircuitStudio.exe), as a student
   starts it: its own environment, no test engine, no run folder of the
   tests' choosing.

   It starts the program, sees the first screen and the engine on the
   bundled runtime (resources\runtime\bin\java.exe), makes a new circuit and
   opens a .circ that uses the MIPS library, quits -- and then nothing of it
   is left: HKCU\Software\JavaSoft\Prefs and the program's keys, %APPDATA%,
   %LOCALAPPDATA% (but the install folder), its folder in %TEMP%
   (tools/windows/state.ts).  It measures the start, the first after the
   install and a second one: the window, the first screen, the engine
   ready (N-22's "start in 4 s") -- into $HCS_E2E_REPORT/launch.json. */

import { _electron, expect, test, type ElectronApplication, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { describe, diffStates, notOurs, snapshot, unexpected, type State } from '../../tools/windows/state.ts';
import { alive, enginePid } from './model.ts';

const exe = process.env.HCS_E2E_EXE;
const report = path.resolve(process.env.HCS_E2E_REPORT ?? 'report');
const root = path.join(import.meta.dirname, '..', '..');

test.skip(!exe || process.platform !== 'win32', 'the installed program on Windows: HCS_E2E_EXE=<install folder>\\HallymCircuitStudio.exe (CI setup-e2e)');

// The program's own environment: none of the tests' switches (HCS_*), Electron not as Node.
function studentEnv(): Record<string, string> {
  const env: Record<string, string> = {};
  for (const [k, v] of Object.entries(process.env)) if (v !== undefined && !/^HCS_|^ELECTRON_RUN_AS_NODE$/i.test(k)) env[k] = v;
  return env;
}

interface Times { windowMs: number; startScreenMs: number; engineReadyMs: number }

async function start(): Promise<{ app: ElectronApplication; page: Page; times: Times }> {
  const t0 = performance.now();
  const app = await _electron.launch({ executablePath: exe!, env: studentEnv(), timeout: 60_000 });
  const page = await app.firstWindow();
  const windowMs = performance.now() - t0;
  await page.waitForFunction(() => document.querySelector('.wcard') !== null, null, { polling: 10, timeout: 60_000 });
  const startScreenMs = performance.now() - t0;
  await page.waitForFunction(() => /Java \d+/.test(document.querySelector('.status .engine')?.textContent ?? ''), null, { polling: 10, timeout: 60_000 });
  const engineReadyMs = performance.now() - t0;
  const round = (n: number) => Math.round(n);
  return { app, page, times: { windowMs: round(windowMs), startScreenMs: round(startScreenMs), engineReadyMs: round(engineReadyMs) } };
}

// Quits as the window's close would, and waits for the program, its engine and its run folder to be gone.
async function quit(app: ElectronApplication): Promise<void> {
  const main = await app.evaluate(() => process.pid);
  const engine = await enginePid(app);
  await app.close();
  const runs = path.join(tmpdir(), 'HallymCircuitStudio');
  await expect.poll(() => [alive(main), engine !== undefined && alive(engine), existsSync(runs) ? readdirSync(runs).length : 0],
    { timeout: 30_000, message: 'the program, its engine and its run folder gone after quit' }).toEqual([false, false, 0]);
}

// The executable a process runs (wmic: read-only, and PowerShell would write its own profile data).
// wmic writes UTF-16 to a pipe on some Windows versions, the OEM code page on others.
function imagePath(pid: number): string {
  const raw = execFileSync('wmic.exe', ['process', 'where', `ProcessId=${pid}`, 'get', 'ExecutablePath', '/value'], { windowsHide: true });
  const out = raw.includes(0) ? raw.toString('utf16le') : raw.toString('latin1');
  return /ExecutablePath=(.*)/.exec(out)?.[1]?.trim() ?? '';
}

function nothingLeft(before: State, what: string): void {
  const after = snapshot();
  const changes = diffStates(before, after);
  const bad = unexpected(changes, 'none');
  mkdirSync(report, { recursive: true });
  writeFileSync(path.join(report, `state-${what}.txt`), [`${what}: ${changes.length} difference(s), ${bad.length} not allowed`,
    ...changes.map((c) => (bad.includes(c) ? `FAIL  ${describe(c)}` : `info  ${describe(c)}  -- ${notOurs(c)}`))].join('\n') + '\n');
  expect(bad.map(describe), `${what}: left on the PC (report/state-${what}.txt)`).toEqual([]);
}

const measured: Record<string, Times> = {};

test.afterAll(() => {
  mkdirSync(report, { recursive: true });
  writeFileSync(path.join(report, 'launch.json'), `${JSON.stringify(measured, null, 1)}\n`);
});

test('installed: the first screen, the engine on the bundled runtime, a circuit with the MIPS library; after quit nothing is left', async () => {
  test.setTimeout(180_000);
  const work = path.join(root, 'test-results', 'installed');
  mkdirSync(work, { recursive: true });
  const file = path.join(work, 'demo-datapath.circ');
  copyFileSync(path.join(root, '..', 'tests/circ/demo-datapath.circ'), file);
  const before = snapshot();

  const { app, page, times } = await start();
  measured.first = times;
  console.log(`first start after install: window ${times.windowMs} ms, first screen ${times.startScreenMs} ms, engine ready ${times.engineReadyMs} ms`);
  try {
    await expect(page.locator('.wcard h1')).toHaveText('안녕하세요!');
    await expect(page.locator('.action').nth(0)).toContainText('튜토리얼 보기');
    await expect(page.locator('.action').nth(1)).toContainText('바로 시작');
    await expect(page.locator('.status .engine')).toContainText('Logisim 2.7.1 · Java 21');
    // The engine runs on the runtime the installer put in, not on a Java of the PC's.
    const resources = await app.evaluate(() => process.resourcesPath);
    expect(resources.toLowerCase()).toBe(path.join(path.dirname(exe!), 'resources').toLowerCase());
    const java = imagePath((await enginePid(app))!);
    expect(java.toLowerCase()).toBe(path.join(resources, 'runtime', 'bin', 'java.exe').toLowerCase());
    // A new circuit (the engine's file.new), then a file with the MIPS library (hcs-mips.jar beside the engine).
    await page.getByRole('button', { name: /바로 시작/ }).click();
    await page.getByRole('button', { name: /새 회로/ }).click();
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ']);
    await app.evaluate(({ dialog }, f) => {
      dialog.showOpenDialog = (async () => ({ canceled: false, filePaths: [f] })) as typeof dialog.showOpenDialog;
    }, file);
    await page.keyboard.press('Control+o');
    await expect(page.locator('.filebar .ptab', { hasText: 'demo-datapath.circ' })).toBeVisible();
    await expect(page.locator('.band')).toBeHidden();
    await page.keyboard.press('F10');   // a cycle, on the engine
    await expect(page.locator('.status')).toContainText('Cycle 1');
  } finally {
    await quit(app);
  }
  nothingLeft(before, 'first-run');
});

test('installed: a second start (warm), and again nothing is left', async () => {
  test.setTimeout(120_000);
  const before = snapshot();
  const { app, page, times } = await start();
  measured.second = times;
  console.log(`second start: window ${times.windowMs} ms, first screen ${times.startScreenMs} ms, engine ready ${times.engineReadyMs} ms`);
  try {
    await expect(page.locator('.wcard')).toBeVisible();
  } finally {
    await quit(app);
  }
  nothingLeft(before, 'second-run');
});
