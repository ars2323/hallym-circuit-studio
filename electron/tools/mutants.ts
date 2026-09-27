/* Shows that the tests catch a wrong module, before they are trusted to say
   a right one is right (derived from Hallym MIPS v2.3.0
   electron/tools/mutants.ts: the runner; the mutants are this app's).

     node tools/mutants.ts [FILTER]

   Each mutant below changes one thing in one file -- the text `find` must
   occur exactly once -- in a copy of src/, tests/, tools/ and packaging/ in a
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
    find: '      p.reject(new EngineError(p.method, m.error));', replace: '      p.resolve(m.error);', tests: ['tests/unit/engine.test.ts'] },
  { module: 'engine', file: 'src/main/engine.ts', what: 'no restart after a crash',
    find: 'if (this.crashes.length > this.opts.maxRestarts) {', replace: 'if (true) {', tests: ['tests/unit/engine.test.ts'] },
  { module: 'engine', file: 'src/main/engine.ts', what: 'restarts without end',
    find: 'if (this.crashes.length > this.opts.maxRestarts) {', replace: 'if (false) {', tests: ['tests/unit/engine.test.ts'] },
  { module: 'engine', file: 'src/main/engine.ts', what: 'stderr not kept for the dialog',
    find: '      this.stderrTail.push(line);\n', replace: '', tests: ['tests/unit/engine.test.ts'] },
  { module: 'engine', file: 'src/main/engine.ts', what: 'answers not told (the journal and the shadow never hear of them)',
    find: "        this.emit('answer', { method: p.method, params: p.params, result: m.result, tag: p.tag });\n", replace: '',
    tests: ['tests/unit/engine.test.ts'] },
  { module: 'engine', file: 'src/main/engine.ts', what: 'engine.hello without the restarted engine\'s parameters',
    find: "{ ...this.opts.client, ...this.opts.helloParams?.() }", replace: 'this.opts.client', tests: ['tests/unit/engine.test.ts'] },
  // ---- crash recovery (src/main/recovery.ts, N-04)
  { module: 'recovery', file: 'src/main/recovery.ts', what: 'edits replayed file by file, not in the order the engine answered them',
    find: '.sort((x, y) => x.e.seq - y.e.seq)', replace: '', tests: ['tests/unit/recovery.test.ts'] },
  { module: 'recovery', file: 'src/main/recovery.ts', what: 'saving does not start the journal over',
    find: "Object.assign(f, { opened: { kind: 'path', path, readOnly }, fingerprint, circuits, entries: [], broken: null });",
    replace: "Object.assign(f, { opened: { kind: 'path', path, readOnly }, fingerprint, circuits, broken: null });", tests: ['tests/unit/recovery.test.ts'] },
  { module: 'recovery', file: 'src/main/recovery.ts', what: 'a part matched without its attributes',
    find: '  return ka.length === Object.keys(b).length && ka.every((k) => a[k] === b[k]);', replace: '  return true;',
    tests: ['tests/unit/recovery.test.ts'] },
  { module: 'recovery', file: 'src/main/recovery.ts', what: 'a restarted engine\'s ids not above the old ones',
    find: 'return floor > 0 ? { idFloor: floor } : {};', replace: 'return {};', tests: ['tests/unit/recovery.test.ts'] },
  { module: 'recovery', file: 'src/main/recovery.ts', what: 'replaying again after the engine died while replaying',
    find: 'const replay = this.crashes <= 1;', replace: 'const replay = true;', tests: ['tests/unit/recovery.test.ts'] },
  { module: 'recovery', file: 'src/main/recovery.ts', what: 'the window\'s calls not held back while recovering',
    find: 'settled(): Promise<void> { return this.gate?.promise ?? Promise.resolve(); }', replace: 'settled(): Promise<void> { return Promise.resolve(); }',
    tests: ['tests/unit/recovery.test.ts'] },
  { module: 'recovery', file: 'src/main/recovery.ts', what: 'edits replayed onto a file changed on disk',
    find: "      else if (f.opened.kind === 'path' && this.fingerprint(f.opened.path) !== f.fingerprint) lost.set(f.fileId, 'changedOnDisk');\n", replace: '',
    tests: ['tests/unit/recovery.test.ts'] },
  { module: 'recovery', file: 'src/main/recovery.ts', what: 'a file that failed its replay left half replayed',
    find: "        await this.call('file.close', { fileId: f.fileId }).catch((x) => { if (x instanceof EngineGone) throw x; });\n", replace: '',
    tests: ['tests/unit/recovery.test.ts'] },
  // ---- packaging and notices (tools/stage-engine.ts, NOTICE, About)
  { module: 'package', file: 'tools/stage-engine.ts', what: 'the runtime\'s links point into the build tree',
    find: 'verbatimSymlinks: true', replace: 'verbatimSymlinks: false', tests: ['tests/unit/package.test.ts'] },
  { module: 'package', file: 'tools/stage-engine.ts', what: 'packaged without the runtime, silently',
    find: "    [path.join(p.runtime, 'bin', javaExe), ':engine:runtime'],\n", replace: '', tests: ['tests/unit/package.test.ts'] },
  { module: 'notice', file: '../NOTICE', what: 'NOTICE without the runtime\'s license',
    find: '  License: GNU General Public License, version 2, with the Classpath\n           Exception', replace: '  License: GNU General Public License, version 2',
    tests: ['tests/unit/notice.test.ts'] },
  { module: 'notice', file: 'src/main/paths.ts', what: 'About > Licenses without the runtime',
    find: "  { name: 'LICENSE.openjdk.txt', title: 'OpenJDK runtime (Eclipse Temurin 21.0.12) — GNU General Public License, version 2, with the Classpath Exception' },\n",
    replace: '', tests: ['tests/unit/notice.test.ts'] },
  { module: 'recovered', file: 'src/renderer/app/logic/recovered.ts', what: 'the band does not say the simulation is back to Reset',
    find: '  if (any) band.push(RESET);', replace: '', tests: ['tests/unit/recovered.test.ts'] },
  { module: 'files', file: 'src/renderer/app/logic/files.ts', what: 'a reopened file keeps its old simulation state',
    find: '    f.sim = null;\n', replace: '', tests: ['tests/unit/files.test.ts'] },
  { module: 'engine-locate', file: 'src/main/engine-locate.ts', what: 'the JVM writes its performance file to the temp folder',
    find: "    '-XX:-UsePerfData',\n", replace: '', tests: ['tests/unit/engine-locate.test.ts'] },
  { module: 'engine-locate', file: 'src/main/engine-locate.ts', what: 'a -sources jar taken for the engine',
    find: "!/-(sources|javadoc|plain)\\.jar$/.test(n)", replace: 'true', tests: ['tests/unit/engine-locate.test.ts'] },
  { module: 'engine-locate', file: 'src/main/engine-locate.ts', what: 'the packaged app falls back to a Java of the PC\'s own',
    find: "    return { reason: `Java 런타임이 없습니다: ${path.join('runtime', 'bin', exe)}`, looked: bundled };\n", replace: '',
    tests: ['tests/unit/engine-locate.test.ts'] },
  { module: 'engine-locate', file: 'src/main/engine-locate.ts', what: 'the JVM\'s own warnings on stdout (the JSON-RPC lines)',
    find: "    '-Xlog:disable', '-Xlog:all=warning:stderr',\n", replace: '', tests: ['tests/unit/engine-locate.test.ts'] },
  { module: 'engine-locate', file: 'src/main/engine-locate.ts', what: 'the bundled runtime without its AppCDS archive',
    find: "    ...(archive ? [`-XX:SharedArchiveFile=${archive}`] : []),\n", replace: '', tests: ['tests/unit/engine-locate.test.ts'] },
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
  // ---- Messages (src/renderer/app/logic/messages.ts, messages.ts; N-13, D-143)
  { module: 'messages', file: 'src/renderer/app/logic/messages.ts', what: 'an unknown kind dropped',
    find: '  return [...known.values(), other].filter((g) => g.messages.length > 0);', replace: '  return [...known.values()].filter((g) => g.messages.length > 0);',
    tests: ['tests/unit/messages.test.ts'] },
  { module: 'messages', file: 'src/renderer/app/logic/messages.ts', what: '"1 messages"',
    find: "message${n === 1 ? '' : 's'}", replace: 'messages', tests: ['tests/unit/messages.test.ts'] },
  { module: 'messages', file: 'src/renderer/app/logic/messages.ts', what: 'a static message sent with a cycle',
    find: "cycle: m.kind === 'dynamic' && typeof l.cycle === 'number' ? l.cycle : null,", replace: "cycle: typeof l.cycle === 'number' ? l.cycle : null,",
    tests: ['tests/unit/messages.test.ts'] },
  { module: 'messages', file: 'src/renderer/app/logic/messages.ts', what: 'the chosen message forgotten at every new list',
    find: '  return chosen !== null && list.some((m) => m.id === chosen) ? chosen : null;', replace: '  return null;',
    tests: ['tests/unit/messages.test.ts', 'tests/e2e/messages.e2e.ts'] },
  { module: 'messages', file: 'src/renderer/app/messages.ts', what: 'choosing a message sends nothing',
    find: '    o.onReveal(revealOf(state.fileId, m));', replace: '', tests: ['tests/e2e/messages.e2e.ts'] },
  { module: 'messages', file: 'src/renderer/app/messages.ts', what: 'the focus lost when the list comes again',
    find: '    if (focusId) box.querySelector', replace: '    if (!focusId) box.querySelector', tests: ['tests/e2e/messages.e2e.ts'] },
  { module: 'messages', file: 'src/renderer/app/app.ts', what: 'diag.changed not taken',
    find: '    diags.set(fileId, (p as unknown as DiagList).messages);', replace: '', tests: ['tests/e2e/messages.e2e.ts'] },
  { module: 'messages', file: 'src/renderer/app/app.css', what: 'a character on screen next to messages',
    find: 'body.messages-shown img.char { visibility: hidden; }', replace: '', tests: ['tests/e2e/messages.e2e.ts'] },
  { module: 'messages', file: 'tests/fake-engine/fake-engine.ts', what: 'the fake engine\'s ids not found from the fixture',
    find: "  const comp = (in_: Circuit | undefined, x: Place) => in_?.comps.find((k) => k.name === x.name && same(k.loc, x.loc))?.id ?? '';",
    replace: "  const comp = (_in: Circuit | undefined, _x: Place) => '';", tests: ['tests/unit/messages.test.ts'] },
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
    find: "  engine.on('notification', (method, params) => { if (!recovery.quiet()) send('engine:notify', method, params); });", replace: '',
    tests: ['tests/e2e/engine.e2e.ts'] },
  { module: 'window', file: 'src/renderer/app/app.ts', what: 'a file that could not be opened again keeps its tab',
    find: '  for (const f of r.closed) files.close(f.fileId);\n', replace: '', tests: ['tests/e2e/recovery.e2e.ts'] },
  { module: 'window', file: 'src/main/main.ts', what: 'the window\'s calls not journaled (the recovery replays nothing)',
    find: '  return engine.call<T>(method, params, { tag: WINDOW });', replace: '  return engine.call<T>(method, params);',
    tests: ['tests/e2e/recovery.e2e.ts'] },
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
  // ---- the program: Load Program, the summary, the band, the Console (N-16, D-147)
  { module: 'program', file: 'src/renderer/app/logic/program.ts', what: 'no band while a reload has failed',
    find: 'if (!f) return null;', replace: 'return null;', tests: ['tests/unit/program.test.ts'] },
  { module: 'program', file: 'src/renderer/app/logic/program.ts', what: 'the band gives the failure\'s time, not the last load\'s',
    find: 'const at = f.kept.loadedAt;', replace: 'const at = f.at;', tests: ['tests/unit/program.test.ts'] },
  { module: 'program', file: 'src/renderer/app/logic/program.ts', what: 'a changed source not the yellow row',
    find: 'warn: s.source.warn', replace: 'warn: false', tests: ['tests/unit/program.test.ts'] },
  { module: 'program', file: 'src/renderer/app/logic/console.ts', what: 'what the Console printed since is dropped',
    find: "+ (u.append ?? '')", replace: '', tests: ['tests/unit/program.test.ts'] },
  { module: 'program', file: 'src/main/program-path.ts', what: 'Load .hmx… for an old .s opens next to the .circ',
    find: 'if (source && source.trim()) {', replace: 'if (false) {', tests: ['tests/unit/program.test.ts'] },
  { module: 'program', file: 'src/main/main.ts', what: 'Load Program\'s dialog shows every file',
    find: 'filters: [IMAGE_FILTER],', replace: "filters: [IMAGE_FILTER, { name: 'All files', extensions: ['*'] }],", tests: ['tests/e2e/program.e2e.ts'] },
  { module: 'program', file: 'src/renderer/app/app.ts', what: 'no band while a reload has failed (the window)',
    find: "if (b) programBand.show(b.text, 'warn', b.title);", replace: 'if (b) void 0;', tests: ['tests/e2e/program.e2e.ts'] },
  { module: 'program', file: 'src/renderer/app/program.ts', what: 'a character next to a file that cannot be loaded',
    find: "cls: 'loaderror', character: false,", replace: "cls: 'loaderror', character: true,", tests: ['tests/e2e/program.e2e.ts'] },
  { module: 'program', file: 'src/renderer/app/program.ts', what: 'the answer to "which memory?" not sent',
    find: 'o = { ...o, again: true, picks: { ...(o.picks ?? {}), [r.choose.kind]: id } };', replace: 'o = { ...o, again: true };',
    tests: ['tests/e2e/program.e2e.ts'] },
  { module: 'program', file: 'src/main/recovery.ts', what: 'a program load not replayed after an engine crash',
    find: " || method === 'mips.load'", replace: '', tests: ['tests/e2e/program.e2e.ts'] },
  { module: 'program', file: 'src/renderer/app/app.ts', what: 'the Console\'s stream not shown',
    find: 'if (files.get(String(p.fileId))) consoleView.update(p as unknown as ConsoleUpdate);', replace: 'void 0;', tests: ['tests/e2e/program.e2e.ts'] },
  // ---- the Canvas (src/renderer/canvas, N-05, N-06, D-137)
  { module: 'canvas', file: 'src/renderer/canvas/parts/gates.ts', what: 'a gate\'s input lines left out (its ports not reached)',
    find: 'if (end > px + 0.01) shapes.push(stubLocal(', replace: 'if (false) shapes.push(stubLocal(', tests: ['tests/unit/canvas-geometry.test.ts'] },
  { module: 'canvas', file: 'src/renderer/canvas/parts/subcircuit.ts', what: 'a subcircuit\'s appearance placed about the wrong anchor',
    find: 'ox: p.loc[0] - (ax * cos - ay * sin)', replace: 'ox: p.loc[0] - (ax * cos + ay * sin)', tests: ['tests/unit/canvas-geometry.test.ts'] },
  { module: 'canvas', file: 'src/renderer/canvas/tokens.ts', what: 'the Canvas\'s 1 another green than the legend\'s',
    find: "  vOne: '#22b14c',", replace: "  vOne: '#22b14d',", tests: ['tests/unit/canvas-registry.test.ts'] },
  { module: 'canvas', file: 'src/renderer/canvas/wires.ts', what: 'a jump where wires connect',
    find: 'if (inside(v, at) && !dots.has(key(at)))', replace: 'if (inside(v, at))', tests: ['tests/unit/canvas-scene.test.ts'] },
  { module: 'canvas', file: 'src/renderer/canvas/labels.ts', what: 'a label chip placed on a wire',
    find: 'let chosen = all.find((b) => !obs.hits(inflate(b, CLEAR))) ?? null;', replace: 'let chosen = all[0] ?? null;', tests: ['tests/unit/canvas-labels.test.ts'] },
  { module: 'canvas', file: 'src/renderer/canvas/view.ts', what: 'Ctrl+wheel zooms about the corner, not the pointer',
    find: 'return { zoom: z, x: cx - at[0] / z, y: cy - at[1] / z };', replace: 'return { zoom: z, x: v.x, y: v.y };', tests: ['tests/unit/canvas-scene.test.ts'] },
  { module: 'canvas', file: 'src/renderer/canvas/layers.ts', what: 'the fills painted last (a part hidden by another\'s fill)',
    find: '  for (const c of ordered) for (const s of shapesOf(c).base) out.push({ part: c, shape: s, pass: \'base\' });\n  for (const c of ordered) for (const s of shapesOf(c).top) out.push({ part: c, shape: s, pass: \'top\' });',
    replace: '  for (const c of ordered) for (const s of shapesOf(c).top) out.push({ part: c, shape: s, pass: \'top\' });\n  for (const c of ordered) for (const s of shapesOf(c).base) out.push({ part: c, shape: s, pass: \'base\' });',
    tests: ['tests/unit/canvas-layers.test.ts'] },
  { module: 'canvas', file: 'src/renderer/canvas/labels.ts', what: 'a port name written right on its wire',
    find: '    if (clear.some((t) => boxesMeet(t, box))) continue;   // never on or next to a wire\n', replace: '', tests: ['tests/unit/canvas-labels.test.ts'] },
  { module: 'canvas', file: 'src/renderer/canvas/view.ts', what: 'fit at the 25 % floor centred (cutting the circuit\'s edges)',
    find: '    : lo - FIT_MARGIN / zoom);', replace: '    : (lo + hi) / 2 - size / 2 / zoom);', tests: ['tests/unit/canvas-scene.test.ts'] },
  { module: 'canvas', file: 'src/renderer/canvas/svg.ts', what: 'an exported arc drawn the other way round',
    find: 'const flag = s[6] ? 0 : 1;', replace: 'const flag = s[6] ? 1 : 0;', tests: ['tests/unit/canvas-draw.test.ts'] },
  // ---- the Cycle View's models (N-14: src/renderer/app/logic/cycle.ts, registers.ts, memory.ts, values.ts)
  { module: 'cycle', file: 'src/renderer/app/logic/cycle.ts', what: 'the table jumps although the cycle on show is in the window shown',
    find: 'if (before && cycle >= before.from && cycle <= before.from + n - 1 && before.from >= first) return clamp(before.from);', replace: '',
    tests: ['tests/unit/cycle.test.ts'] },
  { module: 'cycle', file: 'src/renderer/app/logic/cycle.ts', what: 'no edge where a wave changes at a column\'s start',
    find: 'edgeIn: i > 0 && prev !== a', replace: 'edgeIn: false', tests: ['tests/unit/cycle.test.ts'] },
  { module: 'cycle', file: 'src/renderer/app/logic/cycle.ts', what: 'a past cycle said as the latest',
    find: 'cycle: s.past ? `Cycle ${c} / ${s.last.toLocaleString(\'en-US\')}` : `Cycle ${c}`', replace: 'cycle: `Cycle ${c}`', tests: ['tests/unit/cycle.test.ts'] },
  { module: 'cycle', file: 'src/renderer/app/logic/cycle.ts', what: 'Run Until without a limit on its cycles',
    find: 'max < 1 || max > 1_000_000', replace: 'max < 1', tests: ['tests/unit/cycle.test.ts'] },
  { module: 'cycle', file: 'src/renderer/app/logic/cycle.ts', what: 'an instruction name sent as typed',
    find: "value: v.toLowerCase(), maxCycles: max } };", replace: 'value: v, maxCycles: max } };', tests: ['tests/unit/cycle.test.ts'] },
  { module: 'values', file: 'src/renderer/app/logic/values.ts', what: 'an E bit shown as x',
    find: "nibble.includes('E') ? 'E' : nibble.includes('x') ? 'x'", replace: "nibble.includes('x') || nibble.includes('E') ? 'x'", tests: ['tests/unit/cycle.test.ts'] },
  { module: 'values', file: 'src/renderer/app/logic/values.ts', what: 'decimal without its sign',
    find: 'return u >= 2 ** (w - 1) ? u - 2 ** w : u;', replace: 'return u;', tests: ['tests/unit/cycle.test.ts'] },
  { module: 'registers', file: 'src/renderer/app/logic/registers.ts', what: 'PC painted as the register that just changed',
    find: "changed: r.changed && r.key !== 'PC',", replace: 'changed: r.changed,', tests: ['tests/unit/panels.test.ts'] },
  { module: 'registers', file: 'src/renderer/app/logic/registers.ts', what: 'Mark as PC does not rebuild the rows',
    find: '|${v.markedPc}`', replace: '`', tests: ['tests/unit/panels.test.ts'] },
  { module: 'memory', file: 'src/renderer/app/logic/memory.ts', what: 'a word\'s characters big-endian',
    find: 'printable((v >>> (8 * k)) & 0xff)', replace: 'printable((v >>> (8 * (3 - k))) & 0xff)', tests: ['tests/unit/panels.test.ts'] },
  { module: 'memory', file: 'src/renderer/app/logic/memory.ts', what: '$sp tints the line\'s first word only',
    find: 'pointed.some((p) => p.at >= a && p.at < a + 4)', replace: 'pointed.some((p) => p.at === from)', tests: ['tests/unit/panels.test.ts'] },
  { module: 'cycle', file: 'src/renderer/app/logic/cycle.ts', what: 'the pinned rows kept after their message went',
    find: 'pinned !== undefined && !ids.includes(pinned)', replace: 'false', tests: ['tests/unit/cycle.test.ts'] },
  { module: 'recovery', file: 'src/main/recovery.ts', what: 'Mark as PC, the register file and its mapping not journaled',
    find: ' || MODEL_EDITS.includes(method)', replace: '', tests: ['tests/unit/recovery.test.ts'] },
  // ---- the Windows installer (tools/package-config.ts, packaging/installer.nsh; N-23, D-148)
  { module: 'installer', file: 'tools/package-config.ts', what: 'a desktop shortcut',
    find: 'createDesktopShortcut: false,', replace: 'createDesktopShortcut: true,', tests: ['tests/unit/package-config.test.ts'] },
  { module: 'installer', file: 'tools/package-config.ts', what: 'an installer with pages (not one click)',
    find: 'oneClick: true,', replace: 'oneClick: false,', tests: ['tests/unit/package-config.test.ts'] },
  { module: 'installer', file: 'tools/package-config.ts', what: 'for all users (an administrator)',
    find: 'perMachine: false,', replace: 'perMachine: true,', tests: ['tests/unit/package-config.test.ts'] },
  { module: 'installer', file: 'tools/package-config.ts', what: 'a block map beside the installer (differential updates)',
    find: '      differentialPackage: false,\n', replace: '', tests: ['tests/unit/package-config.test.ts'] },
  { module: 'installer', file: 'tools/package-config.ts', what: '.circ associated with the program',
    find: '    // No fileAssociations, no protocols', replace: "    fileAssociations: [{ ext: 'circ', name: 'Logisim circuit' }],\n    // No fileAssociations, no protocols",
    tests: ['tests/unit/package-config.test.ts'] },
  { module: 'installer', file: 'tools/package-config.ts', what: 'a zip of the program beside the installer',
    find: "target: [{ target: 'nsis', arch: ['x64'] }]", replace: "target: [{ target: 'nsis', arch: ['x64'] }, { target: 'zip', arch: ['x64'] }]",
    tests: ['tests/unit/package-config.test.ts'] },
  { module: 'installer', file: 'packaging/installer.nsh', what: 'the v1.0.x MSI never looked for',
    find: '  !insertmacro hcsRemoveV1Msi\n', replace: '', tests: ['tests/unit/package-config.test.ts'] },
  { module: 'installer', file: 'packaging/installer.nsh', what: 'another UpgradeCode than the 1.0.x MSI\'s',
    find: '{6206F18C-D7FA-366B-98DA-E7980F6083D6}', replace: '{6206F18C-D7FA-366B-98DA-E7980F6083D7}', tests: ['tests/unit/package-config.test.ts'] },
  { module: 'installer', file: 'packaging/installer.nsh', what: 'msiexec with its own dialogs',
    find: '/x $R1 /qn /norestart', replace: '/x $R1', tests: ['tests/unit/package-config.test.ts'] },
  { module: 'installer', file: 'packaging/installer.nsh', what: 'a notice that waits in a silent install',
    find: ': ${HCS_V1_MSI_NAME}" /SD IDOK', replace: ': ${HCS_V1_MSI_NAME}"', tests: ['tests/unit/package-config.test.ts'] },
  { module: 'installer', file: 'packaging/installer.nsh', what: 'a particle after the program\'s name',
    find: '다음 프로그램을 제거합니다: ${PRODUCT_NAME}', replace: '${PRODUCT_NAME}을(를) 제거합니다', tests: ['tests/unit/package-config.test.ts'] },
  { module: 'installer', file: 'packaging/installer.nsh', what: 'the installer\'s copy for an updater kept',
    find: '  RMDir "$LOCALAPPDATA\\hallym-circuit-studio-updater"\n', replace: '', tests: ['tests/unit/package-config.test.ts'] },
  // ---- what a release may carry (tools/release-assets.ts; N-23)
  { module: 'release', file: 'tools/release-assets.ts', what: 'an MSI taken for something else',
    find: "else if (lower.endsWith('.msi'))", replace: "else if (lower.endsWith('.msi') && false)", tests: ['tests/unit/release-assets.test.ts'] },
  { module: 'release', file: 'tools/release-assets.ts', what: 'any zip taken for track A\'s',
    find: "`^hcs-mips-${escape(version)}-(windows|linux)\\\\.zip$`", replace: "`^.*\\\\.zip$`", tests: ['tests/unit/release-assets.test.ts'] },
  { module: 'release', file: 'tools/release-assets.ts', what: 'a release without the setup exe',
    find: '  if (!names.includes(setup)) problems.push', replace: '  if (false) problems.push', tests: ['tests/unit/release-assets.test.ts'] },
  { module: 'release', file: 'tools/release-assets.ts', what: 'the 1.0.x releases checked too',
    find: "return Number(version.split('.')[0]) >= 2;", replace: "return Number(version.split('.')[0]) >= 1;", tests: ['tests/unit/release-assets.test.ts'] },
  // ---- what a run or an install may leave on Windows (tools/windows/state.ts; N-23)
  { module: 'state', file: 'tools/windows/state.ts', what: 'the program\'s folder in %TEMP% not counted',
    find: "if (c.where === 'temp' && !OUR_TEMP.test(", replace: "if (c.where === 'temp' && !!OUR_TEMP.test(", tests: ['tests/unit/state.test.ts'] },
  { module: 'state', file: 'tools/windows/state.ts', what: 'a run\'s recent items taken for the shell\'s',
    find: "expect === 'none' ? WINDOWS_OWN :", replace: "false ? WINDOWS_OWN :", tests: ['tests/unit/state.test.ts'] },
  { module: 'state', file: 'tools/windows/state.ts', what: 'all of %LOCALAPPDATA% taken for Windows\'',
    find: "[/^LOCALAPPDATA\\\\Packages\\\\/i,", replace: "[/^LOCALAPPDATA\\\\/i,", tests: ['tests/unit/state.test.ts'] },
  { module: 'state', file: 'tools/windows/state.ts', what: 'an install may add any registry key',
    find: 'return UNINSTALL_ENTRY.test(c.path) || INSTALL_RECORD.test(c.path);', replace: 'return true;', tests: ['tests/unit/state.test.ts'] },
  { module: 'state', file: 'tools/windows/state.ts', what: 'a Windows Installer product\'s values taken for an empty container',
    find: "(c.where === 'registry' && c.after === 'key' &&", replace: "(c.where === 'registry' &&", tests: ['tests/unit/state.test.ts'] },
  { module: 'state', file: 'tools/windows/state.ts', what: 'the default value named by the system\'s language',
    find: "/^\\((Default|기본값)\\)$/.test(m[1]) ? '(Default)' : m[1]", replace: 'm[1]', tests: ['tests/unit/state.test.ts'] },
  { module: 'run-folder', file: 'src/main/run-folder.ts', what: 'the runs\' folder left in the temp folder',
    find: 'try{rmdirSync(runs)}catch{}', replace: '', tests: ['tests/unit/run-folder.test.ts'] },
];

function copyTree(dir: string): void {
  for (const d of ['src', 'tests', 'tools', 'packaging']) cpSync(path.join(root, d), path.join(dir, d), { recursive: true });
  for (const f of ['package.json', 'tsconfig.json', 'playwright.config.ts', 'ORIGIN.md', 'LICENSE.hallym-mips.txt', 'hallym-assets.md']) cpSync(path.join(root, f), path.join(dir, f));
  symlinkSync(path.join(root, 'node_modules'), path.join(dir, 'node_modules'));
  // The repository around electron/: its notices, the marks and characters, the test circuits.
  for (const f of ['LICENSE', 'NOTICE']) cpSync(path.join(root, '..', f), path.join(dir, '..', f));
  // engine/: its build file names the runtime's OpenJDK release (tests/unit/notice.test.ts).
  for (const d of ['assets', 'tests', 'engine']) symlinkSync(path.join(root, '..', d), path.join(dir, '..', d));
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
