/* tools/package.ts stageApp: the packaged window, staged on Linux as the Windows setup exe gets it (D-166).  Every
   file the page and its stylesheets refer to is there, every stylesheet in src/renderer is on the page, and the
   university's marks the window names through hallym() are staged -- so a PR that changes only the window, which
   no longer runs the Windows jobs, still checks what packaging does with it.  electron-builder is not run. */

import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { after, before, test } from 'node:test';

import { pageStylesheets, stageApp } from '../../tools/package.ts';

const root = path.join(import.meta.dirname, '../..');
const dir = mkdtempSync(path.join(tmpdir(), 'hcs-stage-app-'));
const staged = (...p: string[]) => path.join(dir, ...p);

function files(from: string, ext: string): string[] {
  return readdirSync(from, { recursive: true, encoding: 'utf8' }).filter((f) => f.endsWith(ext)).map((f) => path.join(from, f));
}

before(async () => { await stageApp(dir); });
after(() => rmSync(dir, { recursive: true, force: true }));

test('pageStylesheets reads the page\'s stylesheet links in order', () => {
  assert.deepEqual(pageStylesheets('<link rel="stylesheet" href="../a/a.css">\n<link rel="icon" href="x.png">\n<link rel="stylesheet" href="b.css">'),
    ['../a/a.css', 'b.css']);
});

test('every src and href of the packaged page is a staged file', () => {
  const page = readFileSync(staged('renderer/app/index.html'), 'utf8');
  const refs = [...page.matchAll(/\b(?:src|href)="([^"#]+)"/g)].map((m) => m[1]).filter((r) => !/^[a-z]+:/i.test(r));
  assert.ok(refs.includes('app.js'), 'the page loads the packaged app.js');
  for (const r of refs) assert.ok(existsSync(staged('renderer/app', r)), `index.html refers to ${r}, not staged`);
});

test('every stylesheet in src/renderer is on the page (so it is packaged)', () => {
  const page = readFileSync(path.join(root, 'src/renderer/app/index.html'), 'utf8');
  const linked = new Set(pageStylesheets(page).map((h) => path.resolve(root, 'src/renderer/app', h)));
  for (const css of files(path.join(root, 'src/renderer'), '.css')) {
    assert.ok(linked.has(path.resolve(css)), `${path.relative(root, css)} is not linked from index.html`);
  }
});

test('every url() in the packaged stylesheets is a staged file', () => {
  const sheets = files(staged('renderer'), '.css');
  assert.ok(sheets.length > 0);
  for (const css of sheets) {
    for (const m of readFileSync(css, 'utf8').matchAll(/url\(\s*['"]?([^'")]+)['"]?\s*\)/g)) {
      if (/^(data|https?):/i.test(m[1])) continue;
      assert.ok(existsSync(path.join(path.dirname(css), m[1])), `${path.relative(dir, css)}: url(${m[1]}) not staged`);
    }
  }
});

test('the marks and characters the window names through hallym() are staged', () => {
  const names = new Set<string>();
  for (const f of files(path.join(root, 'src/renderer'), '.ts')) {
    for (const m of readFileSync(f, 'utf8').matchAll(/hallym\(\s*['`]([^'`$]+)['`]\s*\)/g)) names.add(m[1]);
  }
  for (const n of names) assert.ok(existsSync(staged('renderer/hallym', n)), `hallym('${n}') not staged`);
});
