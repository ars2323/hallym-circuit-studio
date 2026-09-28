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
   (tools/windows/state.ts).  Before each run a control period of the same
   helpers and at least a run's length measures what Windows changes by
   itself meanwhile (report/noise-<run>.json): only those places, and
   state.ts ALLOWED, do not count.  It measures the start, the first after
   the install and a second one: the window, the first screen, the engine
   ready (N-22's "start in 4 s") -- into $HCS_E2E_REPORT/launch.json. */

import { _electron, expect, test, type ElectronApplication, type Page } from '@playwright/test';
import { execFileSync, spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { describe, diffStates, loadNoise, measureNoise, quiet, report as stateReport, snapshot, type State } from '../../tools/windows/state.ts';
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
function wmic(args: string[]): string {
  const raw = execFileSync('wmic.exe', args, { windowsHide: true, maxBuffer: 16 << 20 });
  return raw.includes(0) ? raw.toString('utf16le') : raw.toString('latin1');
}
const imagePath = (pid: number): string => /ExecutablePath=(.*)/.exec(wmic(['process', 'where', `ProcessId=${pid}`, 'get', 'ExecutablePath', '/value']))?.[1]?.trim() ?? '';

// The processes running from the install folder (none may, in a control period).
function oursRunning(): string[] {
  const dir = `${path.dirname(exe!).toLowerCase()}\\`;
  return [...wmic(['process', 'get', 'ExecutablePath', '/value']).matchAll(/ExecutablePath=(.*)/g)].map((m) => m[1].trim()).filter((p) => p.toLowerCase().startsWith(dir));
}

/* Windows Search re-indexes the Start menu's programs for a while after an
   install (seen on the runner: its folder under %LOCALAPPDATA%\Packages
   changing during the first run).  The control period starts once it is
   quiet -- nothing added, removed or written there for 10 s (sizes too: a
   hive's log grows without its time changing), at most 2 minutes. */
const SEARCH = path.join(process.env.LOCALAPPDATA ?? '', 'Packages', 'Microsoft.Windows.Search_cw5n1h2txyewy');

/* Windows' own noise before a run (D-148 12): snapshot A, the harness's
   helpers as around a run -- a program started and ended without a shell
   (as Playwright starts the program), wmic (as imagePath) --, a wait as
   long as a run or longer (the run's own length, its launch to its quit, is
   on its report's first line), snapshot B; nothing of ours running all along.  What changed from A to B
   is Windows' noise, saved as report/noise-<what>.json beside the install
   checks' own (tools/windows/check-install.ps1); B is the run's "before". */
const CONTROL_MS = 20_000;
async function control(what: string): Promise<State> {
  expect(oursRunning(), `before the control period for ${what}: nothing of ours running`).toEqual([]);
  console.log(`Windows Search: ${await quiet([SEARCH])}`);
  const a = snapshot();
  const t0 = performance.now();
  spawnSync(path.join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'hostname.exe'), { windowsHide: true, stdio: 'ignore' });
  const running = oursRunning();
  await new Promise((done) => setTimeout(done, Math.max(0, CONTROL_MS - (performance.now() - t0))));
  expect([...running, ...oursRunning()], `the control period for ${what}: nothing of ours running`).toEqual([]);
  const b = snapshot();
  const n = measureNoise(what, a, b);
  mkdirSync(report, { recursive: true });
  writeFileSync(path.join(report, `noise-${what}.json`), `${JSON.stringify(n, null, 1)}\n`);
  console.log(`control period before ${what} (${CONTROL_MS} ms): ${n.changes.length} place(s) changed by Windows itself`);
  return b;
}

function nothingLeft(before: State, what: string, runMs: number): void {
  const after = snapshot();
  const { lines, bad } = stateReport(`${what} (run ${Math.round(runMs)} ms, control ${CONTROL_MS} ms)`, diffStates(before, after), 'none', loadNoise(report));
  mkdirSync(report, { recursive: true });
  writeFileSync(path.join(report, `state-${what}.txt`), `${lines.join('\n')}\n`);
  expect(bad.map(describe), `${what}: left on the PC (report/state-${what}.txt)`).toEqual([]);
}

const measured: Record<string, Times> = {};

test.afterAll(() => {
  mkdirSync(report, { recursive: true });
  writeFileSync(path.join(report, 'launch.json'), `${JSON.stringify(measured, null, 1)}\n`);
});

test('installed: the first screen, the engine on the bundled runtime, a circuit with the MIPS library; after quit nothing is left', async () => {
  test.setTimeout(330_000);
  const work = path.join(root, 'test-results', 'installed');
  mkdirSync(work, { recursive: true });
  const file = path.join(work, 'demo-datapath.circ');
  copyFileSync(path.join(root, '..', 'tests/circ/demo-datapath.circ'), file);
  const before = await control('first-run');

  const t0 = performance.now();
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
  nothingLeft(before, 'first-run', performance.now() - t0);
});

test('installed: a second start (warm), and again nothing is left', async () => {
  test.setTimeout(330_000);
  const before = await control('second-run');
  const t0 = performance.now();
  const { app, page, times } = await start();
  measured.second = times;
  console.log(`second start: window ${times.windowMs} ms, first screen ${times.startScreenMs} ms, engine ready ${times.engineReadyMs} ms`);
  try {
    await expect(page.locator('.wcard')).toBeVisible();
  } finally {
    await quit(app);
  }
  nothingLeft(before, 'second-run', performance.now() - t0);
});
