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

test('when the runtime is built here, About\'s text is its legal/java.base files byte for byte', { skip: !existsSync(path.join(repo, 'engine/build/runtime/legal/java.base/LICENSE')) }, () => {
  const text = readFileSync(path.join(root, 'LICENSE.openjdk.txt'), 'utf8');
  for (const f of ['LICENSE', 'ASSEMBLY_EXCEPTION', 'ADDITIONAL_LICENSE_INFO']) {
    const legal = read(`engine/build/runtime/legal/java.base/${f}`).replace(/\r\n/g, '\n').trim();
    assert.ok(text.includes(legal), `LICENSE.openjdk.txt does not have the runtime's ${f}`);
  }
  const release = read('engine/build/runtime/release');
  assert.match(release, new RegExp(`JAVA_VERSION="${JDK!.replace(/\./g, '\\.')}`));
});
