/* The lab-PC rule in the code (N-19, D-152): the window's settings are for
   this run only.  Nothing in the window uses storage that could outlive
   the run (web storage, IndexedDB, cookies, Cache Storage); the main
   process writes to disk only this run's folder (main.ts, run-folder.ts) --
   the student's files and their recovery files are written by the engine,
   never here; the settings UI says "이번 실행에만 적용됩니다".
   tests/e2e/labpc.e2e.ts changes every setting, quits and starts again. */

import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import { RUN_DEFAULTS, RUN_ONLY } from '../../src/renderer/app/logic/run-settings.ts';

const SRC = path.join(import.meta.dirname, '../../src');
const files = (dir: string): string[] => readdirSync(dir, { recursive: true }).map(String)
  .filter((f) => /\.(ts|cjs|js|html)$/.test(f)).map((f) => path.join(dir, f));
// The code without its comments (a comment may name what is not used).
const code = (f: string) => readFileSync(f, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:'"`])\/\/.*$/gm, '$1');

test('the note and the defaults', () => {
  assert.equal(RUN_ONLY, '이번 실행에만 적용됩니다');
  assert.doesNotMatch(RUN_ONLY, /하면 됩니다|한림/);
  assert.deepEqual(RUN_DEFAULTS, { hz: 1, busWidths: true });
  // the settings UI there is (Wire Colors: Show Bus Widths) says so
  assert.match(code(path.join(SRC, 'renderer/canvas/legend.ts')), /RUN_ONLY/);
  // the window takes its defaults from there
  const app = code(path.join(SRC, 'renderer/app/app.ts'));
  assert.match(app, /hz === RUN_DEFAULTS\.hz/);
  assert.match(app, /busWidths: RUN_DEFAULTS\.busWidths/);
});

test('the window keeps nothing that could outlive the run: no web storage, IndexedDB, cookies or Cache Storage', () => {
  const hits: string[] = [];
  for (const f of files(path.join(SRC, 'renderer'))) {
    const c = code(f);
    for (const api of ['localStorage', 'sessionStorage', 'indexedDB', 'document.cookie', 'caches.open', 'navigator.storage', 'openDatabase']) {
      if (c.includes(api)) hits.push(`${path.relative(SRC, f)}: ${api}`);
    }
  }
  assert.deepEqual(hits, []);
});

test('the main process writes to disk only this run\'s folder: the student\'s files and recovery files are the engine\'s to write', () => {
  const writes = /\b(writeFileSync|writeFile|appendFileSync|appendFile|createWriteStream|mkdirSync|mkdir|rmSync|rm|unlinkSync|unlink|renameSync|rename|copyFileSync|copyFile|rmdirSync|mkdtempSync|symlinkSync|truncateSync|utimesSync)\s*\(/g;
  const found: string[] = [];
  for (const f of files(path.join(SRC, 'main'))) {
    for (const m of code(f).matchAll(writes)) found.push(`${path.relative(SRC, f)} ${m[1]}`);
  }
  assert.deepEqual(found.sort(), [
    'main/main.ts mkdirSync',            // the run's folder
    'main/main.ts writeFileSync',        // its Chromium preferences (no spell checker)
    'main/run-folder.ts rmSync',         // an earlier run's folder
    'main/run-folder.ts rmSync',         // (the script that removes this one after quit)
    'main/run-folder.ts rmdirSync',
  ].sort());
  // recovery-files.ts only looks (stat): the engine writes and removes them
  assert.doesNotMatch(code(path.join(SRC, 'main/recovery-files.ts')), /from 'node:fs'.*(write|rm|unlink|rename)/);
});
