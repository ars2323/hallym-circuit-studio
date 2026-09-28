/* What the packaged app ships has its notice (N-04, D-142): while
   tools/stage-engine.ts puts the Java runtime into resources/runtime, the
   repository's NOTICE must have the OpenJDK entry -- what it is, the exact
   release (the one engine/build.gradle.kts builds the runtime from), GNU GPL
   version 2 with the Classpath Exception, where the full texts are
   (runtime/legal/), where the source is -- and About > Licenses must list
   it with its text. */

import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import { LICENSE_SOURCES, LICENSES } from '../../src/main/paths.ts';
import { extraResources } from '../../tools/stage-engine.ts';

const root = path.join(import.meta.dirname, '..', '..');
const repo = path.join(root, '..');
const read = (p: string) => readFileSync(path.join(repo, p), 'utf8');

// The OpenJDK release engine/build.gradle.kts makes the runtime from (it refuses another).
const JDK = /val runtimeJdk = "([0-9.]+)"/.exec(read('engine/build.gradle.kts'))?.[1];
const shipsRuntime = extraResources('/x').some((r) => r.to === 'runtime');

function noticeEntry(title: RegExp): string | null {
  const entries = read('NOTICE').split(/\n-{20,}\n/);
  return entries.find((e) => title.test(e.split('\n')[0])) ?? null;
}

test('the runtime\'s release is declared once, in engine/build.gradle.kts', () => {
  assert.match(JDK ?? '', /^21\.0\.\d+$/);
});

test('NOTICE: the bundled OpenJDK runtime, while the package ships runtime/', () => {
  assert.ok(shipsRuntime, 'tools/stage-engine.ts ships runtime/ (if it stops, this entry may go)');
  const e = noticeEntry(/^OpenJDK runtime/);
  assert.ok(e, 'NOTICE has no "OpenJDK runtime" entry');
  assert.match(e, new RegExp(`Eclipse Temurin ${JDK!.replace(/\./g, '\\.')}`));
  assert.match(e, /jlink/);
  assert.match(e, /License: GNU General Public License, version 2, with the Classpath\s+Exception/);
  assert.match(e, /runtime\/legal\/<module>\/: LICENSE, ASSEMBLY_EXCEPTION,\s+ADDITIONAL_LICENSE_INFO/);
  assert.match(e, /https:\/\/adoptium\.net\//);
  assert.match(e, /https:\/\/openjdk\.org\//);
  assert.match(e, /https:\/\/github\.com\/adoptium\/jdk21u/);
  assert.match(e, /java\.base, java\.datatransfer, java\.desktop,\s+java\.logging, java\.prefs, java\.sql, java\.transaction\.xa, java\.xml,\s+jdk\.charsets and jdk\.unsupported/);
});

test('About > Licenses lists the runtime, with the release, and its text is the runtime\'s own license', () => {
  const entry = LICENSES.find((l) => l.name === 'LICENSE.openjdk.txt');
  assert.ok(entry, 'About > Licenses has no OpenJDK runtime entry');
  assert.equal(entry.title, `OpenJDK runtime (Eclipse Temurin ${JDK}) — GNU General Public License, version 2, with the Classpath Exception`);
  const text = readFileSync(path.join(root, LICENSE_SOURCES['LICENSE.openjdk.txt']), 'utf8');
  assert.match(text, new RegExp(`^OpenJDK runtime \\(Eclipse Temurin ${JDK!.replace(/\./g, '\\.')}\\)`));
  for (const s of ['The GNU General Public License (GPL)', '"CLASSPATH" EXCEPTION TO THE GPL', 'OPENJDK ASSEMBLY EXCEPTION', 'ADDITIONAL INFORMATION ABOUT LICENSING',
    'runtime/legal/<module>/', 'https://adoptium.net/temurin/', 'https://openjdk.org/']) {
    assert.ok(text.includes(s), s);
  }
});

// D-155: the first screen's video is Hallym MIPS 2.5.0's file; its notice is Hallym MIPS NOTICE section 8's.
test('NOTICE and the marks\' notes: the first screen\'s video, whose it is and where it comes from, as Hallym MIPS NOTICE 8 says it', () => {
  const flat = (s: string) => s.replace(/\s+/g, ' ');
  const e = noticeEntry(/^Hallym University identity assets/);
  assert.ok(e, 'NOTICE has no entry for the university\'s identity assets');
  const text = flat(e);
  for (const s of [
    'the opening aerial shot of its promotional video (0:00.1 to 0:02.6), slowed down and without sound (the Electron edition\'s first screen), belong to Hallym University.',
    'The video is "[Official Video] 한림대학교 홍보영상｜The New Hallym 대학의 내일을 열다", from Hallym University\'s official YouTube channel, @HALLYMNEWS.',
    'Commercial use is prohibited',
    'electron/src/renderer/assets/hallym/start/',
  ]) assert.ok(text.includes(s), s);
  const notes = flat(readFileSync(path.join(root, 'hallym-assets.md'), 'utf8'));
  assert.ok(notes.includes('"[Official Video] 한림대학교 홍보영상｜The New Hallym 대학의 내일을 열다" (official YouTube channel @HALLYMNEWS)'));
  assert.ok(notes.includes('the character stands on the card\'s opaque white, never on the video'));
  assert.ok(LICENSES.some((l) => l.name === 'hallym-assets.md' && /video/.test(l.title)));
});

test('when the runtime is built here, About\'s text is its legal/java.base files byte for byte', { skip: !existsSync(path.join(repo, 'engine/build/runtime/legal/java.base/LICENSE')) }, () => {
  const text = readFileSync(path.join(root, 'LICENSE.openjdk.txt'), 'utf8');
  for (const f of ['LICENSE', 'ASSEMBLY_EXCEPTION', 'ADDITIONAL_LICENSE_INFO']) {
    const legal = read(`engine/build/runtime/legal/java.base/${f}`).replace(/\r\n/g, '\n').trim();
    assert.ok(text.includes(legal), `LICENSE.openjdk.txt does not have the runtime's ${f}`);
  }
  const release = read('engine/build/runtime/release');
  assert.match(release, new RegExp(`JAVA_VERSION="${JDK!.replace(/\./g, '\\.')}`));
});
