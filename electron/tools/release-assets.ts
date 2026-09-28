/* What a release may carry (N-23, D-148; the user's rule, v2 brief 2 and 8):
   the Windows program is the setup exe alone -- no zip of the app, no MSI --
   with track A beside it (hcs-mips.jar and its zip) and the guide PDFs.
   CI's release job checks the files before it uploads them and the release
   after; .github/workflows/release-assets.yml checks a release again when
   it is published or edited (guides are uploaded by hand).

     node tools/release-assets.ts check --version <v> <file>...   the files (paths or names)
     node tools/release-assets.ts check --version <v> -           names on stdin, one a line
     node tools/release-assets.ts version <tag>                   the version a tag is for (v2.0.0 -> 2.0.0)

   Exits 1 with every problem listed when a name is not allowed or the
   setup exe is missing. */

import { readFileSync } from 'node:fs';
import path from 'node:path';

import { setupExeName } from './package-config.ts';

export type Kind = 'setup' | 'track-a-jar' | 'track-a-zip' | 'guide-pdf';

export interface Verdict {
  ok: boolean;
  kinds: Record<string, Kind>;   // each allowed file, by name
  problems: string[];            // one line each: the name and what is wrong
}

const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

// The track A zip (tools/package-track-a.sh): the same jar and guide, one folder per OS name.
export const trackAZip = (version: string): RegExp => new RegExp(`^hcs-mips-${escape(version)}-(windows|linux)\\.zip$`);
// A guide as PDF: an ASCII name with "guide" in it (GitHub renames other names).
export const GUIDE_PDF = /^[A-Za-z0-9._-]*guide[A-Za-z0-9._-]*\.pdf$/i;

export function checkReleaseAssets(names: readonly string[], version: string): Verdict {
  const kinds: Record<string, Kind> = {};
  const problems: string[] = [];
  const setup = setupExeName(version);
  for (const name of names) {
    const lower = name.toLowerCase();
    if (name === setup) kinds[name] = 'setup';
    else if (name === 'hcs-mips.jar') kinds[name] = 'track-a-jar';
    else if (trackAZip(version).test(name)) kinds[name] = 'track-a-zip';
    else if (GUIDE_PDF.test(name)) kinds[name] = 'guide-pdf';
    else if (lower.endsWith('.msi')) problems.push(`${name}: an MSI -- the Windows program is the setup exe only (D-122, D-148)`);
    else if (lower.endsWith('.zip')) problems.push(`${name}: a zip other than track A's (hcs-mips-${version}-windows.zip / -linux.zip) -- the Windows program is the setup exe only (D-148)`);
    else if (/-setup\.exe$/i.test(name)) problems.push(`${name}: a setup exe, but not ${setup} (this release's version is ${version})`);
    else problems.push(`${name}: not a release file (allowed: ${setup}, hcs-mips.jar, hcs-mips-${version}-windows.zip / -linux.zip, guide PDFs)`);
  }
  if (!names.includes(setup)) problems.push(`${setup}: missing -- a release carries the Windows setup exe`);
  const seen = new Set<string>();
  for (const name of names) {
    if (seen.has(name)) problems.push(`${name}: twice`);
    seen.add(name);
  }
  return { ok: problems.length === 0, kinds, problems };
}

// The version a release tag is for: v2.0.0 -> 2.0.0, v2.0.0-alpha.1 -> 2.0.0-alpha.1.
export function versionOfTag(tag: string): string {
  const m = /^v(\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)$/.exec(tag);
  if (!m) throw new Error(`${tag}: not a release tag (v<major>.<minor>.<patch>[-<pre>])`);
  return m[1];
}

// Whether the rule applies to a release: from 2.0.0 on (1.0.x carried a zip and an MSI, and stay as they were).
export function ruleApplies(version: string): boolean {
  return Number(version.split('.')[0]) >= 2;
}

function main(argv: string[]): number {
  const [command, ...rest] = argv;
  if (command === 'version') {
    console.log(versionOfTag(rest[0] ?? ''));
    return 0;
  }
  if (command !== 'check') {
    console.error('usage: release-assets.ts check --version <v> <file>... | -\n       release-assets.ts version <tag>');
    return 2;
  }
  const at = rest.indexOf('--version');
  if (at < 0 || !rest[at + 1]) {
    console.error('check: --version <v> is needed');
    return 2;
  }
  const version = rest[at + 1];
  const args = rest.filter((_, i) => i !== at && i !== at + 1);
  const names = (args.length === 1 && args[0] === '-'
    ? readFileSync(0, 'utf8').split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
    : args).map((f) => path.basename(f));
  if (!ruleApplies(version)) {
    console.log(`${version}: before 2.0.0, released as it was (a zip and an MSI then); not checked`);
    return 0;
  }
  const v = checkReleaseAssets(names, version);
  for (const [name, kind] of Object.entries(v.kinds)) console.log(`ok    ${name}  (${kind})`);
  for (const p of v.problems) console.log(`FAIL  ${p}`);
  console.log(v.ok ? `release files for ${version}: allowed` : `release files for ${version}: ${v.problems.length} problem(s)`);
  return v.ok ? 0 : 1;
}

if (import.meta.main) process.exit(main(process.argv.slice(2)));
