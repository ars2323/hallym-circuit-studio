/* What this app takes from Hallym MIPS Simulator (ars2323/hallym-mips-simulator,
   BSD 3-Clause, the same author), and a way to take it again.

   Only Hallym MIPS's own screen code, design values, fonts, icons, tools and
   test harness are taken (D-133 point 5, D-135).  Nothing from SPIM: not
   electron/src/core/op-table.ts (generated from SPIM's CPU/op.h), not
   electron/native/** (links the SPIM core), and no file that leans on SPIM's
   tables or strings (src/core/*, src/sim/*, the Editor, the Text, Data,
   Registers, Inspector and Console panels, the tutorial's MIPS steps).
   tests/unit/origin.test.ts checks that none of those names turns up here.

   Two kinds of file:
     copy     byte for byte the upstream file at TAG (its sha256 in
              tools/hmips-sums.json).
     derived  written from the upstream file, changed for this app; the
              change is in `note` and in ORIGIN.md.

     node tools/import-hmips.ts [--ref <clone>] [--tag <tag>]
         copy:    writes the upstream file at <tag> over ours
         derived: writes the upstream file to build/hmips/<tag>/…, and says
                  whether it changed since the version ours was made from
                  (then merge the change by hand and --record)
     node tools/import-hmips.ts --record [--ref <clone>] [--tag <tag>]
         writes tools/hmips-sums.json: the upstream files' sha256 at <tag>
     node tools/import-hmips.ts --check
         offline: every file is here, every copy is still byte for byte
     node tools/import-hmips.ts --lucide <unpacked lucide-static package>
         also takes LUCIDE_EXTRA from lucide-static, made the way Hallym
         MIPS's icons are made (the license comment and the class
         attribute left out; the paths untouched)

   <clone> defaults to ../ref/hallym-mips-simulator (the repository's ref/,
   git-ignored): gh repo clone ars2323/hallym-mips-simulator ref/hallym-mips-simulator */

import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

export const root = path.join(import.meta.dirname, '..');
export const HMIPS_REPO = 'ars2323/hallym-mips-simulator';
export const HMIPS_TAG = 'v2.3.0';

export interface Taken {
  from: string;       // path in Hallym MIPS, from its repository root
  to: string;         // path here, from electron/
  how: 'copy' | 'derived';
  note: string;       // what it is; for derived, what changed
}

export const TAKEN: Taken[] = [
  // ---- the window's shared parts (src/renderer/shared/)
  { from: 'electron/src/renderer/app/dom.ts', to: 'src/renderer/shared/dom.ts', how: 'derived',
    note: 'DOM helpers. codeText() splits `code` itself (the upstream imported codeParts from src/core/explain.ts, which is not taken); asset() and character() point at this repository\'s assets/hallym/ originals; monoCh() and userScrolls() left out (no table here yet).' },
  { from: 'electron/src/renderer/app/ui.ts', to: 'src/renderer/shared/ui.ts', how: 'derived',
    note: 'Panel heads (panelHead, tabsHead, headButton). columnButton() left out; tabsHead can hide a tab and give it back (narrow windows move panels into tabs).' },
  { from: 'electron/src/renderer/app/notice.ts', to: 'src/renderer/shared/notice.ts', how: 'derived',
    note: 'The empty-state word of a panel. The character is optional (most panels show the words alone; errors never have one).' },
  { from: 'electron/src/renderer/app/panels/ask.ts', to: 'src/renderer/shared/ask.ts', how: 'derived',
    note: 'The window\'s own question dialog. Adds `character: false` and one-button use for errors (the engine could not start): no character next to an error.' },
  { from: 'electron/src/renderer/app/panels/welcome.ts', to: 'src/renderer/shared/welcome.ts', how: 'derived',
    note: 'The first screen\'s card and its two steps. The words and the choices come from the caller (src/renderer/app/start.ts); the second step can be one of several.' },
  { from: 'electron/src/renderer/app/panels/about.ts', to: 'src/renderer/shared/about.ts', how: 'derived',
    note: 'About with About / Licenses tabs. The About tab\'s lines come from the caller (this program: Logisim 2.7.1 by Carl Burch, the engine, the university\'s marks).' },
  { from: 'electron/src/renderer/app/logic/overlay.ts', to: 'src/renderer/shared/overlay.ts', how: 'copy',
    note: 'The caption buttons\' patch colour under a dim or a dialog\'s backdrop.' },
  { from: 'electron/src/renderer/app/logic/names.ts', to: 'src/renderer/shared/names.ts', how: 'copy',
    note: 'A file name shortened for the title bar (Hangul two columns).' },
  { from: 'electron/src/renderer/app/app.ts', to: 'src/renderer/shared/titlebar.ts', how: 'derived',
    note: 'The title bar\'s buttons and its fit in steps (button(), iconButton(), fitTitlebar()) taken out of app.ts into a component; one more step here: the toolbar moves to a row of its own under the bar.' },
  { from: 'electron/src/renderer/app/app.ts', to: 'src/renderer/shared/splitter.ts', how: 'derived',
    note: 'The splitter and the grip (drag to share, double-click for the default) taken out of app.ts into one component for both directions.' },
  { from: 'electron/src/renderer/app/app.css', to: 'src/renderer/shared/shared.css', how: 'derived',
    note: 'Tokens, fonts, title bar, buttons, panel heads, splitters, empty states, first screen, dialogs, About, status bar, band. The SPIM panels\' rules (Editor, Assemble, Text, Data, Registers, Inspector, Console) and the tutorial\'s are left out.' },
  { from: 'electron/src/renderer/app/index.html', to: 'src/renderer/app/index.html', how: 'derived',
    note: 'The same Content-Security-Policy; this app\'s title and style sheets.' },
  // ---- the main process
  { from: 'electron/src/main/main.ts', to: 'src/main/main.ts', how: 'derived',
    note: 'The window (no system title bar, titleBarOverlay, maximised at every start), the run\'s profile folder in the temp folder removed after quit, the caption buttons\' patch, About. The simulator parts are replaced by the engine client (src/main/engine.ts, new).' },
  { from: 'electron/src/main/paths.ts', to: 'src/main/paths.ts', how: 'derived',
    note: 'Where the page, preload, notices and the engine are; this app\'s notices.' },
  { from: 'electron/src/main/preload.cjs', to: 'src/main/preload.cjs', how: 'derived',
    note: 'The same narrow window.app over ipcRenderer, with this app\'s calls.' },
  // ---- fonts and icons (unchanged)
  { from: 'electron/src/renderer/assets/fonts/Pretendard-Regular.subset.woff2', to: 'src/renderer/assets/fonts/Pretendard-Regular.subset.woff2', how: 'copy', note: 'Pretendard (OFL), subset' },
  { from: 'electron/src/renderer/assets/fonts/Pretendard-Medium.subset.woff2', to: 'src/renderer/assets/fonts/Pretendard-Medium.subset.woff2', how: 'copy', note: 'Pretendard (OFL), subset' },
  { from: 'electron/src/renderer/assets/fonts/Pretendard-SemiBold.subset.woff2', to: 'src/renderer/assets/fonts/Pretendard-SemiBold.subset.woff2', how: 'copy', note: 'Pretendard (OFL), subset' },
  { from: 'electron/src/renderer/assets/fonts/Pretendard-Bold.subset.woff2', to: 'src/renderer/assets/fonts/Pretendard-Bold.subset.woff2', how: 'copy', note: 'Pretendard (OFL), subset' },
  { from: 'electron/src/renderer/assets/fonts/OFL-Pretendard.txt', to: 'src/renderer/assets/fonts/OFL-Pretendard.txt', how: 'copy', note: 'Pretendard\'s license' },
  { from: 'electron/src/renderer/assets/fonts/D2Coding.woff2', to: 'src/renderer/assets/fonts/D2Coding.woff2', how: 'copy', note: 'D2Coding (OFL)' },
  { from: 'electron/src/renderer/assets/fonts/OFL-D2Coding.txt', to: 'src/renderer/assets/fonts/OFL-D2Coding.txt', how: 'copy', note: 'D2Coding\'s license' },
  { from: 'electron/src/renderer/assets/icons/lucide/LICENSE.txt', to: 'src/renderer/assets/icons/lucide/LICENSE.txt', how: 'copy', note: 'Lucide\'s license (ISC; MIT for the Feather-derived icons)' },
  ...['circle-question-mark', 'file-plus', 'folder-open', 'save', 'play', 'square', 'step-forward', 'rotate-ccw'].map((n): Taken => (
    { from: `electron/src/renderer/assets/icons/lucide/${n}.svg`, to: `src/renderer/assets/icons/lucide/${n}.svg`, how: 'copy', note: 'Lucide icon' })),
  { from: 'electron/src/renderer/assets/hallym/README.md', to: 'hallym-assets.md', how: 'derived',
    note: 'The university\'s marks and characters: whose they are, the rules, how they are kept. Rewritten for this repository\'s assets/hallym/ file names.' },
  // ---- the license of what is taken
  { from: 'LICENSE', to: 'LICENSE.hallym-mips.txt', how: 'copy', note: 'Hallym MIPS\'s BSD 3-Clause license: goes with the code taken from it (About > Licenses, NOTICE)' },
  // ---- tools and tests
  { from: 'electron/tsconfig.json', to: 'tsconfig.json', how: 'derived', note: 'The same compiler options; this tree\'s folders.' },
  { from: 'electron/playwright.config.ts', to: 'playwright.config.ts', how: 'derived', note: 'The same (one worker, 60 s); the comment names the engine.' },
  { from: 'electron/.gitignore', to: '.gitignore', how: 'derived', note: 'The same, without native/.' },
  { from: 'electron/.gitattributes', to: '.gitattributes', how: 'copy', note: 'Every file byte for byte on every platform.' },
  { from: 'electron/tools/build-ui.ts', to: 'tools/build-ui.ts', how: 'derived', note: 'esbuild: the window\'s script; no simulator worker.' },
  { from: 'electron/tools/electron.ts', to: 'tools/electron.ts', how: 'copy', note: 'Starts Electron without ELECTRON_RUN_AS_NODE.' },
  { from: 'electron/tools/licenses.ts', to: 'tools/licenses.ts', how: 'derived', note: 'The bundled npm packages\' licenses; no addon.' },
  { from: 'electron/tools/e2e-widths.ts', to: 'tools/e2e-widths.ts', how: 'derived', note: 'Every e2e test at this app\'s window sizes (the lab PCs\' 1920x1080 at 100/125/150 %, half a screen).' },
  { from: 'electron/tools/capture-screens.ts', to: 'tools/capture-screens.ts', how: 'derived', note: 'The PNG writer, the checks (no hover, size limit) and shot(); this app\'s scenes.' },
  { from: 'electron/tools/mutants.ts', to: 'tools/mutants.ts', how: 'derived', note: 'The mutant runner (a copy per mutant in a temporary folder); this app\'s mutants, no native build.' },
  { from: 'electron/tools/package.ts', to: 'tools/package.ts', how: 'derived', note: 'electron-builder skeleton: NSIS, per user, one click, no elevation (the real installer is N-23).' },
  { from: 'electron/packaging/installer.nsh', to: 'packaging/installer.nsh', how: 'derived', note: 'The install folder named after the program; no updater copy.' },
  { from: 'electron/tests/e2e/harness.ts', to: 'tests/e2e/harness.ts', how: 'derived', note: 'Launch with a fresh run folder, sizes and scale switches, dialogs answered from the test; the fake engine.' },
  { from: 'electron/tests/renderer/overlay.test.ts', to: 'tests/unit/overlay.test.ts', how: 'derived', note: 'The same tests; the import path.' },
  { from: 'electron/tests/renderer/names.test.ts', to: 'tests/unit/names.test.ts', how: 'derived', note: 'The same tests; the import path.' },
];

// Lucide icons Hallym MIPS does not have, from lucide-static (the npm package).
export const LUCIDE_VERSION = '1.48.0';
export const LUCIDE_EXTRA = ['undo-2', 'redo-2', 'mouse-pointer-2', 'pointer', 'workflow', 'type', 'square-dot', 'tag',
  'crosshair', 'activity', 'fast-forward', 'gauge', 'file-code', 'info', 'x', 'cpu', 'circuit-board'];

// Never taken (D-133 point 5): SPIM's tables and what leans on them.
export const NEVER = ['src/core/', 'src/sim/', 'native/', 'op-table', 'decoder', 'instruction-text', 'explain',
  'asm-errors', 'mips-syntax', 'panels/registers', 'panels/text', 'panels/data', 'panels/inspector', 'panels/console', 'editor.ts',
  'tutorial.ts'];

export const sha256 = (b: Uint8Array | string): string => createHash('sha256').update(b).digest('hex');

// The upstream files' sha256 at HMIPS_TAG, by `to` (--record writes it).
const SUMS_FILE = path.join(root, 'tools/hmips-sums.json');
export const sums = (): { tag: string; files: Record<string, string> } =>
  (existsSync(SUMS_FILE) ? JSON.parse(readFileSync(SUMS_FILE, 'utf8')) : { tag: HMIPS_TAG, files: {} });

// lucide-static's file, the way Hallym MIPS keeps its icons.
export function lucideIcon(text: string): string {
  return text.replace(/^<!-- @license[^\n]*-->\n/, '').replace(/\n {2}class="lucide[^"]*"/, '');
}

function upstream(ref: string, tag: string, from: string): Buffer {
  return execFileSync('git', ['-C', ref, 'show', `${tag}:${from}`], { maxBuffer: 64 * 1024 * 1024 });
}

export function check(): string[] {
  const problems: string[] = [];
  const recorded = sums();
  if (recorded.tag !== HMIPS_TAG) problems.push(`tools/hmips-sums.json is for ${recorded.tag}, not ${HMIPS_TAG}`);
  for (const t of TAKEN) {
    const expected = recorded.files[t.to] ?? '';
    const file = path.join(root, t.to);
    if (!existsSync(file)) { problems.push(`missing: ${t.to}`); continue; }
    if (!/^[0-9a-f]{64}$/.test(expected)) problems.push(`${t.to}: no upstream sha256 recorded`);
    else if (t.how === 'copy' && sha256(readFileSync(file)) !== expected) problems.push(`${t.to}: not byte for byte ${HMIPS_TAG}:${t.from}`);
  }
  for (const n of LUCIDE_EXTRA) {
    if (!existsSync(path.join(root, 'src/renderer/assets/icons/lucide', `${n}.svg`))) problems.push(`missing icon: ${n}.svg`);
  }
  return problems;
}

if (import.meta.main) {
  const arg = (name: string) => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : undefined; };
  if (process.argv.includes('--check')) {
    const problems = check();
    console.log(problems.length ? problems.join('\n') : `all ${TAKEN.length} files taken from Hallym MIPS ${HMIPS_TAG} are here; every copy is byte for byte`);
    process.exit(problems.length ? 1 : 0);
  }
  const ref = path.resolve(arg('--ref') ?? path.join(root, '..', 'ref', 'hallym-mips-simulator'));
  const tag = arg('--tag') ?? HMIPS_TAG;
  if (!existsSync(ref)) {
    console.error(`no clone at ${ref}: gh repo clone ${HMIPS_REPO} ref/hallym-mips-simulator`);
    process.exit(1);
  }
  if (process.argv.includes('--record')) {
    const files: Record<string, string> = {};
    for (const t of TAKEN) files[t.to] = sha256(upstream(ref, tag, t.from));
    writeFileSync(SUMS_FILE, `${JSON.stringify({ tag, files }, null, 2)}\n`);
    console.log(`wrote ${path.relative(root, SUMS_FILE)}: ${TAKEN.length} files at ${tag}`);
    process.exit(0);
  }
  const recorded = sums().files;
  let merge = 0;
  for (const t of TAKEN) {
    const bytes = upstream(ref, tag, t.from);
    const changed = recorded[t.to] !== undefined && sha256(bytes) !== recorded[t.to];
    if (t.how === 'copy') {
      mkdirSync(path.dirname(path.join(root, t.to)), { recursive: true });
      writeFileSync(path.join(root, t.to), bytes);
      console.log(`${changed ? 'updated ' : 'copied  '} ${t.to}`);
    } else {
      const out = path.join(root, 'build/hmips', tag, t.from);
      mkdirSync(path.dirname(out), { recursive: true });
      writeFileSync(out, bytes);
      if (changed) merge += 1;
      console.log(`${changed ? 'MERGE   ' : 'same    '} ${t.to}  (upstream: ${path.relative(root, out)})`);
    }
  }
  const lucide = arg('--lucide');
  if (lucide) {
    for (const n of LUCIDE_EXTRA) {
      const text = lucideIcon(readFileSync(path.join(lucide, 'icons', `${n}.svg`), 'utf8'));
      writeFileSync(path.join(root, 'src/renderer/assets/icons/lucide', `${n}.svg`), text);
      console.log(`lucide   ${n}.svg`);
    }
  }
  console.log(merge ? `\n${merge} derived files changed upstream since they were taken: merge them by hand, then --record` : '\ndone');
}
