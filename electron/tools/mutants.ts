/* Shows that the tests catch a wrong module, before they are trusted to say
   a right one is right (derived from Hallym MIPS v2.3.0
   electron/tools/mutants.ts: the runner; the mutants are this app's).

     node tools/mutants.ts [FILTER]

   Each mutant below changes one thing in one file -- the text `find` must
   occur exactly once -- in a copy of src/, tests/ and tools/ in a
   temporary directory, <tmp>/electron (node_modules/ is linked, not copied,
   and so are the repository's assets/ and tests/ next to it: the window's
   marks and characters, the test circuits), and runs the tests named for
   it there.  A mutant is KILLED when those tests fail; one that survives,
   or does not apply, fails this script.  Nothing in the working tree is
   touched.

   Tests named *.e2e.ts run the real window through Playwright (the copy's
   window script is bundled first); they need a display -- on Linux without
   one: xvfb-run -a -s '-screen 0 2400x1400x24' npm run test:mutants. */

import { execFileSync, spawnSync } from 'node:child_process';
import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const root = path.join(import.meta.dirname, '..');

interface Mutant {
  module: string;
  file: string;
  find: string;
  replace: string;
  tests: string[];
  what: string;
}

export const MUTANTS: Mutant[] = [
  // ---- JSON-RPC framing (src/main/rpc.ts)
  { module: 'rpc', file: 'src/main/rpc.ts', what: '\\r\\n lines keep their \\r',
    find: "p.endsWith('\\r') ? p.slice(0, -1) : p", replace: 'p', tests: ['tests/unit/rpc.test.ts'] },
  { module: 'rpc', file: 'src/main/rpc.ts', what: 'a character cut between chunks decoded on its own',
    find: 'this.decoder.decode(chunk, { stream: true })', replace: 'this.decoder.decode(chunk)', tests: ['tests/unit/rpc.test.ts'] },
  { module: 'rpc', file: 'src/main/rpc.ts', what: 'a request from the engine taken as a notification',
    find: "    if (hasId) return { kind: 'invalid', line, reason: 'a request from the engine (the engine only answers and notifies)' };\n", replace: '',
    tests: ['tests/unit/rpc.test.ts'] },
  // ---- the engine client (src/main/engine.ts)
  { module: 'engine', file: 'src/main/engine.ts', what: 'every request the same id',
    find: 'const id = this.nextId++;', replace: 'const id = this.nextId;', tests: ['tests/unit/engine.test.ts'] },
  { module: 'engine', file: 'src/main/engine.ts', what: 'an error answer taken as a result',
    find: 'else p.reject(new EngineError(p.method, m.error));', replace: 'else p.resolve(m.error);', tests: ['tests/unit/engine.test.ts'] },
  { module: 'engine', file: 'src/main/engine.ts', what: 'no restart after a crash',
    find: 'if (this.crashes.length > this.opts.maxRestarts) {', replace: 'if (true) {', tests: ['tests/unit/engine.test.ts'] },
  { module: 'engine', file: 'src/main/engine.ts', what: 'restarts without end',
    find: 'if (this.crashes.length > this.opts.maxRestarts) {', replace: 'if (false) {', tests: ['tests/unit/engine.test.ts'] },
  { module: 'engine', file: 'src/main/engine.ts', what: 'stderr not kept for the dialog',
    find: '      this.stderrTail.push(line);\n', replace: '', tests: ['tests/unit/engine.test.ts'] },
  { module: 'engine-locate', file: 'src/main/engine-locate.ts', what: 'the JVM writes its performance file to the temp folder',
    find: "    '-XX:-UsePerfData',\n", replace: '', tests: ['tests/unit/engine-locate.test.ts'] },
  { module: 'engine-locate', file: 'src/main/engine-locate.ts', what: 'a -sources jar taken for the engine',
    find: "!/-(sources|javadoc|plain)\\.jar$/.test(n)", replace: 'true', tests: ['tests/unit/engine-locate.test.ts'] },
  // ---- the lab-PC rule (src/main/run-folder.ts, main.ts)
  { module: 'run-folder', file: 'src/main/run-folder.ts', what: 'a running copy\'s folder removed',
    find: 'if (!pid || pid === self || isAlive(pid)) continue;', replace: 'if (!pid || pid === self) continue;', tests: ['tests/unit/run-folder.test.ts'] },
  { module: 'run-folder', file: 'src/main/run-folder.ts', what: 'a switch taken for the .circ',
    find: "    if (a.startsWith('-')) continue;\n", replace: '', tests: ['tests/unit/run-folder.test.ts'] },
  { module: 'lab-pc', file: 'src/main/main.ts', what: 'NSS\'s certificate store in the user\'s own folders (Linux)',
    find: "  process.env.XDG_DATA_HOME = path.join(runDir, 'data');\n", replace: '', tests: ['tests/e2e/labpc.e2e.ts'] },
  { module: 'lab-pc', file: 'src/main/main.ts', what: 'the run\'s folder left after quit',
    find: "    spawn(process.execPath, ['-e', removeAfterExitScript(process.pid, runDir)], {", replace: "    if (0) spawn(process.execPath, ['-e', removeAfterExitScript(process.pid, runDir)], {",
    tests: ['tests/e2e/labpc.e2e.ts'] },
  // ---- the window's logic (src/renderer/app/logic/)
  { module: 'layout', file: 'src/renderer/app/logic/layout.ts', what: 'the Canvas\'s least not kept',
    find: '  if (left + right > room) {', replace: '  if (false) {', tests: ['tests/unit/layout.test.ts'] },
  { module: 'layout', file: 'src/renderer/app/logic/layout.ts', what: 'narrow only under 900 px',
    find: 'const narrow = width < NARROW_PX;', replace: 'const narrow = width < 900;', tests: ['tests/unit/layout.test.ts'] },
  { module: 'files', file: 'src/renderer/app/logic/files.ts', what: 'closing shows the right neighbour',
    find: 'this.files[Math.max(0, i - 1)]', replace: 'this.files[i]', tests: ['tests/unit/files.test.ts'] },
  { module: 'facts', file: 'src/renderer/app/logic/facts.ts', what: 'tunnels in the order they come',
    find: "      .sort((a, b) => (a.label === '' ? 1 : b.label === '' ? -1 : a.label.localeCompare(b.label, 'en', { numeric: true }))),",
    replace: '', tests: ['tests/unit/facts.test.ts'] },
  { module: 'origin', file: 'src/renderer/shared/overlay.ts', what: 'a file copied from Hallym MIPS edited',
    find: 'export const TUTORIAL_DIM = { color: NAVY, alpha: 0.26 };', replace: 'export const TUTORIAL_DIM = { color: NAVY, alpha: 0.25 };',
    tests: ['tests/unit/origin.test.ts'] },
  // ---- the window (e2e)
  { module: 'window', file: 'src/renderer/shared/welcome.ts', what: 'the first step\'s card shorter (no back row)',
    find: "    back.style.visibility = step === spec.first ? 'hidden' : 'visible';", replace: '    back.hidden = step === spec.first;',
    tests: ['tests/e2e/start.e2e.ts'] },
  { module: 'window', file: 'src/renderer/app/app.ts', what: 'a toolbar on the first screen',
    find: '  bar.showToolbar(f !== null);', replace: '  bar.showToolbar(true);', tests: ['tests/e2e/start.e2e.ts'] },
  { module: 'window', file: 'src/main/main.ts', what: 'the .circ on the command line ignored',
    find: '  const startup = circArgument(process.argv, process.cwd());', replace: '  const startup = circArgument([], process.cwd());',
    tests: ['tests/e2e/start.e2e.ts'] },
  { module: 'window', file: 'src/renderer/shared/ask.ts', what: 'a character next to an error',
    find: '    const withCharacter = q.character !== false;', replace: '    const withCharacter = true;', tests: ['tests/e2e/start.e2e.ts'] },
  { module: 'window', file: 'src/renderer/shared/ask.ts', what: 'a click outside closes the dialog',
    find: '    dialog.showModal();', replace: "    dialog.showModal();\n    dialog.addEventListener('click', (e) => { if (e.target === dialog) dialog.close(); });",
    tests: ['tests/e2e/window.e2e.ts'] },
  { module: 'window', file: 'src/renderer/app/app.ts', what: 'Attributes not moved into the left panel when narrow',
    find: '      upperPanel.append(attributesBody.root);', replace: '', tests: ['tests/e2e/layout.e2e.ts'] },
  { module: 'window', file: 'src/renderer/shared/titlebar.ts', what: 'the title bar never gives way',
    find: '  const fitsBar = () => tools.getBoundingClientRect().right <= end() + 0.5;', replace: '  const fitsBar = () => true;',
    tests: ['tests/e2e/layout.e2e.ts'] },
  { module: 'window', file: 'src/main/main.ts', what: 'the page may call any engine method (paths too)',
    find: "    if (!allowed.has(method)) throw new Error(`not a method the window may call: ${method}`);\n", replace: '',
    tests: ['tests/e2e/window.e2e.ts'] },
  { module: 'window', file: 'src/renderer/app/app.ts', what: 'Run follows Simulation Enabled instead of the clock',
    find: '  const ticking = f?.sim?.ticking ?? false;', replace: '  const ticking = f?.sim?.running ?? false;', tests: ['tests/e2e/engine.e2e.ts'] },
  { module: 'window', file: 'src/main/main.ts', what: 'the engine\'s notifications not forwarded',
    find: "  engine.on('notification', (method, params) => send('engine:notify', method, params));", replace: '',
    tests: ['tests/e2e/engine.e2e.ts'] },
  { module: 'window', file: 'src/renderer/app/app.ts', what: 'the old engine\'s files kept after a restart',
    find: '    files.clear();\n', replace: '', tests: ['tests/e2e/engine.e2e.ts'] },
  // ---- the review round (D-135 points 14-17)
  { module: 'words', file: 'src/renderer/shared/shared.css', what: 'a character on screen next to an error',
    find: 'body.error-dialog img.char, body.band-shown img.char { visibility: hidden; }', replace: '', tests: ['tests/e2e/start.e2e.ts'] },
  { module: 'words', file: 'src/renderer/shared/shared.css', what: 'an English name broken across two lines',
    find: '.name { white-space: nowrap; }', replace: '', tests: ['tests/e2e/layout.e2e.ts'] },
  { module: 'words', file: 'src/renderer/app/app.ts', what: 'the engine\'s English shown for a file error',
    find: 'body: d.body, detail: d.detail', replace: 'body: (e as CallError).message, detail: d.detail', tests: ['tests/e2e/start.e2e.ts'] },
  { module: 'words', file: 'src/renderer/app/logic/facts.ts', what: '"1 wires"',
    find: "${n === 1 ? '' : 's'}", replace: 's', tests: ['tests/unit/facts.test.ts'] },
  { module: 'words', file: 'src/main/engine-locate.ts', what: 'the engine-failed sentence names another file than the one tried',
    find: 'path.basename(looked[0] ?? ENGINE_JAR)', replace: 'ENGINE_JAR', tests: ['tests/unit/engine-locate.test.ts'] },
  { module: 'origin', file: 'src/renderer/shared/ui.ts', what: 'a taken file that names the simulator core\'s op table',
    find: "import { h, icon } from './dom.ts';", replace: "import { h, icon } from './dom.ts'; // OP" + '_TABLE', tests: ['tests/unit/origin.test.ts'] },
  { module: 'window', file: 'src/renderer/app/app.ts', what: '한림 on screen',
    find: "'Hallym University의 논리설계와 컴퓨터구조 실습을 위한 회로 편집·시뮬레이션 도구입니다.'",
    replace: "'한림대학교 논리설계와 컴퓨터구조 실습을 위한 회로 편집·시뮬레이션 도구입니다.'", tests: ['tests/unit/origin.test.ts'] },
];

function copyTree(dir: string): void {
  for (const d of ['src', 'tests', 'tools']) cpSync(path.join(root, d), path.join(dir, d), { recursive: true });
  for (const f of ['package.json', 'tsconfig.json', 'playwright.config.ts', 'ORIGIN.md', 'LICENSE.hallym-mips.txt', 'hallym-assets.md']) cpSync(path.join(root, f), path.join(dir, f));
  symlinkSync(path.join(root, 'node_modules'), path.join(dir, 'node_modules'));
  // The repository around electron/: its notices, the marks and characters, the test circuits.
  for (const f of ['LICENSE', 'NOTICE']) cpSync(path.join(root, '..', f), path.join(dir, '..', f));
  for (const d of ['assets', 'tests']) symlinkSync(path.join(root, '..', d), path.join(dir, '..', d));
}

if (import.meta.main) {
  const filter = process.argv[2] ?? '';
  const selected = MUTANTS.filter((m) => `${m.module} ${m.what}`.includes(filter));
  let bad = 0;
  const rows: string[] = [];
  for (const m of selected) {
    const outer = mkdtempSync(path.join(os.tmpdir(), 'mutant-'));
    const dir = path.join(outer, 'electron');
    mkdirSync(dir);
    try {
      copyTree(dir);
      const file = path.join(dir, m.file);
      const text = readFileSync(file, 'utf8');
      const count = text.split(m.find).length - 1;
      if (count !== 1) {
        rows.push(`NOT APPLIED  ${m.module}: ${m.what} (found ${count} times)`);
        bad += 1;
        continue;
      }
      writeFileSync(file, text.replace(m.find, m.replace));
      const e2e = m.tests.every((t) => t.endsWith('.e2e.ts'));
      if (e2e) execFileSync(process.execPath, ['tools/build-ui.ts'], { cwd: dir, stdio: 'ignore' });
      const run = e2e
        ? spawnSync(process.execPath, [path.join(root, 'node_modules/@playwright/test/cli.js'), 'test', ...m.tests],
                    { cwd: dir, encoding: 'utf8', timeout: 300000 })
        : spawnSync(process.execPath, ['--test', '--test-reporter=tap', ...m.tests], { cwd: dir, encoding: 'utf8', timeout: 300000 });
      const firstFailure = (e2e ? /^\s*\d+\) (.*)$/m.exec(run.stdout)?.[1]?.replace(/─+$/, '').trim()
                                : /^\s*not ok \d+ - (.*)$/m.exec(run.stdout)?.[1]) ?? '(no test reported a failure)';
      if (run.status === 0) {
        rows.push(`SURVIVED     ${m.module}: ${m.what}`);
        bad += 1;
      } else {
        rows.push(`killed       ${m.module}: ${m.what}  <-  ${firstFailure}`);
      }
    } finally {
      rmSync(outer, { recursive: true, force: true });
    }
  }
  console.log(rows.join('\n'));
  console.log(`\n${selected.length - bad} of ${selected.length} mutants killed`);
  process.exit(bad === 0 ? 0 : 1);
}
