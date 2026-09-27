/* What a release may carry (N-23, D-148): the setup exe, track A (the jar
   and its zip), guide PDFs -- and never an MSI or another zip.  CI's
   release job and .github/workflows/release-assets.yml run
   tools/release-assets.ts; this is its rule. */

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { test } from 'node:test';

import { checkReleaseAssets, ruleApplies, versionOfTag } from '../../tools/release-assets.ts';

const root = path.join(import.meta.dirname, '..', '..');
const V = '2.0.0-alpha.1';
const GOOD = [`HallymCircuitStudio-${V}-win-x64-setup.exe`, 'hcs-mips.jar', `hcs-mips-${V}-windows.zip`, `hcs-mips-${V}-linux.zip`,
  'hallym-circuit-studio-GUIDE-ko.pdf', 'hallym-circuit-studio-TA-GUIDE-ko.pdf'];

test('the setup exe, track A\'s jar and zips, the guide PDFs: allowed, each by its kind', () => {
  const v = checkReleaseAssets(GOOD, V);
  assert.deepEqual(v.problems, []);
  assert.equal(v.ok, true);
  assert.deepEqual(v.kinds, {
    [GOOD[0]]: 'setup', 'hcs-mips.jar': 'track-a-jar', [GOOD[2]]: 'track-a-zip', [GOOD[3]]: 'track-a-zip',
    [GOOD[4]]: 'guide-pdf', [GOOD[5]]: 'guide-pdf',
  });
  // The setup exe alone is a release too (guides come by hand, track A may wait).
  assert.equal(checkReleaseAssets([GOOD[0]], V).ok, true);
});

test('an MSI fails the release, whatever its name', () => {
  for (const msi of [`hallym-circuit-studio-${V}-windows.msi`, 'HallymCircuitStudio.MSI', 'x.msi']) {
    const v = checkReleaseAssets([...GOOD, msi], V);
    assert.equal(v.ok, false);
    assert.equal(v.problems.length, 1);
    assert.match(v.problems[0], new RegExp(`^${msi.replace(/\./g, '\\.')}: an MSI -- the Windows program is the setup exe only`));
  }
});

test('any zip but track A\'s fails: the app zip of 1.0.x, a zip of another version, a runtime zip', () => {
  for (const zip of [`hallym-circuit-studio-${V}-windows.zip`, 'hcs-mips-2.0.0-windows.zip', 'hcs-runtime-windows-x64.zip', 'HallymCircuitStudio.zip',
    `hcs-mips-${V}-mac.zip`]) {
    const v = checkReleaseAssets([...GOOD, zip], V);
    assert.equal(v.ok, false, zip);
    assert.match(v.problems[0], /a zip other than track A's/);
  }
});

test('anything else fails too: hcs-asm (gone, D-141), a Markdown guide, a block map, another version\'s setup exe; and the setup exe must be there', () => {
  for (const [name, why] of [['hcs-asm.exe', /not a release file/], ['hcs-asm', /not a release file/], ['hcs-mips-guide-ko.md', /not a release file/],
    [`HallymCircuitStudio-${V}-win-x64-setup.exe.blockmap`, /not a release file/], ['HallymCircuitStudio-2.0.0-win-x64-setup.exe', /a setup exe, but not/],
    ['안내.pdf', /not a release file/]] as const) {
    const v = checkReleaseAssets([...GOOD, name], V);
    assert.equal(v.ok, false, name);
    assert.match(v.problems[0], why, name);
  }
  const none = checkReleaseAssets(GOOD.slice(1), V);
  assert.equal(none.ok, false);
  assert.deepEqual(none.problems, [`HallymCircuitStudio-${V}-win-x64-setup.exe: missing -- a release carries the Windows setup exe`]);
  assert.match(checkReleaseAssets([...GOOD, 'hcs-mips.jar'], V).problems[0], /hcs-mips\.jar: twice/);
});

test('tags: v2.0.0 is 2.0.0, a pre-release keeps its suffix; the rule applies from 2.0.0 (1.0.x stays as published)', () => {
  assert.equal(versionOfTag('v2.0.0'), '2.0.0');
  assert.equal(versionOfTag('v2.0.0-alpha.1'), '2.0.0-alpha.1');
  for (const bad of ['2.0.0', 'v2.0', 'swing-final', 'v2.0.0 ']) assert.throws(() => versionOfTag(bad), /not a release tag/);
  assert.equal(ruleApplies('2.0.0-alpha.1'), true);
  assert.equal(ruleApplies('10.0.0'), true);
  assert.equal(ruleApplies('1.0.3'), false);
});

test('the command line: exit 0 for the allowed files (paths or names on stdin), 1 with every problem listed', () => {
  const run = (args: string[], input?: string) =>
    spawnSync(process.execPath, [path.join(root, 'tools/release-assets.ts'), ...args], { encoding: 'utf8', input });
  const ok = run(['check', '--version', V, ...GOOD.map((g) => path.join('dist', g))]);
  assert.equal(ok.status, 0, ok.stdout + ok.stderr);
  assert.match(ok.stdout, /release files for 2\.0\.0-alpha\.1: allowed/);
  const bad = run(['check', '--version', V, '-'], [...GOOD, 'a.msi', 'app.zip'].join('\n'));
  assert.equal(bad.status, 1);
  assert.match(bad.stdout, /FAIL {2}a\.msi: an MSI/);
  assert.match(bad.stdout, /FAIL {2}app\.zip: a zip other than track A's/);
  assert.match(bad.stdout, /2 problem\(s\)/);
  const old = run(['check', '--version', '1.0.2', 'hallym-circuit-studio-1.0.2-windows.msi']);
  assert.equal(old.status, 0);
  assert.match(old.stdout, /before 2\.0\.0/);
  assert.equal(run(['version', 'v2.0.0-alpha.1']).stdout.trim(), '2.0.0-alpha.1');
  assert.equal(run(['check', 'x.exe']).status, 2);
});
