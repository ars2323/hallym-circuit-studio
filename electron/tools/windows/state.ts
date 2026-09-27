/* What the program and its installer leave on a Windows PC (N-23, D-148;
   the lab-PC rule, v2 brief 7): a snapshot of the places they could write,
   and the difference between two snapshots.  Read-only -- it lists folders
   and runs reg.exe query, nothing else, so that taking a snapshot changes
   none of what it looks at (PowerShell would: its own profile data in
   %LOCALAPPDATA%).

     node tools/windows/state.ts snapshot <out.json>
     node tools/windows/state.ts diff <before.json> <after.json> [--expect install|none] [--report <file>]

   The places:
     files     %APPDATA%, %LOCALAPPDATA% (not the install folder, not Temp),
               the desktop (the user's and the public one), the all-users
               Start menu; each file with its size and time, each folder
     temp      %TEMP%'s own entries (top level) and this program's run
               folders (%TEMP%\HallymCircuitStudio): the test tools write
               there too, so only this program's names count
     registry  HKCU\Software\JavaSoft and HKLM\Software\JavaSoft (Java's
               preferences: the engine keeps its own in memory), every key of
               HKCU\Software but Microsoft and Classes, the uninstall entries
               and Run keys, Windows Installer's per-user products, and under
               HKCU\Software\Classes .circ and any key named after this program

   --expect none      nothing may differ (a run of the program)
   --expect install   only what the installer writes: the Start menu
                      shortcut, the uninstall entry and its install record
                      (HKCU\Software\<guid>)
   --expect uninstalled  nothing left
   Everywhere Windows' own is not counted (notOurs(), reported as "info"):
   the empty containers Windows makes (%LOCALAPPDATA%\Programs, the per-user
   Uninstall key, Windows Installer's and the crypto API's keys without
   values) and what Windows and the test tools write whatever runs
   (WINDOWS_OWN: the registry hive's files, PowerShell's startup cache, Store
   apps' data, the shell's caches; while installing also the shell's jump
   lists).  Links are recorded as links, not followed. */

import { execFileSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { APP_GUID, INSTALL_FOLDER, PRODUCT_NAME } from '../package-config.ts';

export interface State {
  files: Record<string, string>;      // path (root-relative, with the root's name) -> "dir" or "<size> <mtime>"
  temp: Record<string, string>;
  registry: Record<string, string>;   // key path, or key path + " :: " + value name -> "<type> <data>"
  taken: string;                      // when (ISO), for the report
}

export interface Change {
  where: 'files' | 'temp' | 'registry';
  what: 'added' | 'removed' | 'changed';
  path: string;
  before?: string;
  after?: string;
}

// This program's names in %TEMP%: its run folders, Java's per-process files if it ever wrote them.
export const OUR_TEMP = /^(HallymCircuitStudio|hsperfdata_.*|hs_err_pid.*|hcs-.*|jna.*|\.java.*)$/i;

// ---- the difference ---------------------------------------------------------------------

export function diffStates(a: State, b: State): Change[] {
  const out: Change[] = [];
  for (const where of ['files', 'temp', 'registry'] as const) {
    const x = a[where];
    const y = b[where];
    for (const k of Object.keys(y).sort()) {
      if (!(k in x)) out.push({ where, what: 'added', path: k, after: y[k] });
      else if (x[k] !== y[k] && !(where === 'files' && x[k] === 'dir' && y[k] === 'dir')) out.push({ where, what: 'changed', path: k, before: x[k], after: y[k] });
    }
    for (const k of Object.keys(x).sort()) if (!(k in y)) out.push({ where, what: 'removed', path: k, before: x[k] });
  }
  return out;
}

// Windows' own containers, which appear empty once anything is installed per user and stay:
// the per-user programs folder, the per-user uninstall key, Windows Installer's per-user keys
// (an MSI install and uninstall leaves their parents; a product's own keys have values), and the
// crypto API's per-user policy stores (created empty when Windows checks a file's signature).
const PER_USER_PROGRAMS = /^LOCALAPPDATA\\Programs$/i;
export const windowsContainer = (c: Change): boolean => c.what === 'added' && (
  (c.where === 'files' && PER_USER_PROGRAMS.test(c.path) && c.after === 'dir') ||
  (c.where === 'registry' && c.after === 'key' &&
    /^HKCU\\Software\\(Microsoft\\(Windows\\CurrentVersion\\Uninstall|Installer(\\[^\\]+)*)|Policies\\Microsoft\\SystemCertificates(\\[^\\]+)*)$/i.test(c.path)));

/* What Windows and the test tools write whatever runs, seen on the CI runner
   (D-148): not the program's, and not a place a desktop program writes.
     the registry hive's own files     UsrClass.dat* (the registry is compared key by key)
     PowerShell's startup cache        the check script is PowerShell, and electron-builder's
                                       installer asks PowerShell whether the program is running
     Store apps' data (Packages)       Windows Search re-indexes the Start menu's programs
     the shell's caches                icons, the desktop wallpaper at a new screen size
     Windows' spelling word lists      %APPDATA%\Microsoft\Spelling\<language>\default.*: the
                                       system spell checker's per-user lists, shared by every
                                       program that checks spelling; Chromium opens it at its
                                       start whatever the program sets (tried: the window's and
                                       the session's spell checker off, a profile with it off),
                                       and they stay empty (the program adds no word) */
export const WINDOWS_OWN: [RegExp, string][] = [
  [/^LOCALAPPDATA\\Microsoft\\Windows\\UsrClass\.dat/i, 'the registry hive\'s own files'],
  [/^LOCALAPPDATA\\Microsoft\\(Windows\\)?PowerShell\\/i, 'PowerShell\'s startup cache'],
  [/^LOCALAPPDATA\\Packages\\/i, 'Store apps\' data (Windows Search)'],
  [/^LOCALAPPDATA\\Microsoft\\Windows\\Caches\\/i, 'the shell\'s caches'],
  [/^APPDATA\\Microsoft\\Windows\\Themes\\/i, 'the desktop wallpaper\'s cache'],
  [/^APPDATA\\Microsoft\\Spelling(\\|$)/i, 'Windows\' spelling word lists'],
];
// And while installing: the shell's jump lists record the installers it saw start (msiexec, the setup exe).
export const INSTALLING_OWN: [RegExp, string][] = [
  [/^APPDATA\\Microsoft\\Windows\\Recent\\AutomaticDestinations\\/i, 'the shell\'s jump lists (the installers started)'],
];

export type Expect = 'none' | 'install' | 'uninstalled';

// Why a change is not the program's, or null when it counts.
export function notOurs(c: Change, expect: Expect = 'none'): string | null {
  if (c.where === 'temp' && !OUR_TEMP.test(c.path.split(/[\\/]/)[1] ?? '')) return 'the temp folder (the test tools\' and Windows\')';
  if (windowsContainer(c)) return 'Windows\' own empty container';
  if (c.where === 'files') {
    for (const [re, why] of expect === 'none' ? WINDOWS_OWN : [...WINDOWS_OWN, ...INSTALLING_OWN]) if (re.test(c.path)) return why;
  }
  return null;
}

// What counts: in %TEMP% only this program's names; nowhere Windows' own.
export const counts = (c: Change, expect: Expect = 'none'): boolean => notOurs(c, expect) === null;

const exactly = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const START_MENU_SHORTCUT = new RegExp(`^APPDATA\\\\Microsoft\\\\Windows\\\\Start Menu\\\\Programs\\\\${exactly(PRODUCT_NAME)}\\.lnk$`, 'i');
const UNINSTALL_ENTRY = new RegExp(`^HKCU\\\\Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Uninstall\\\\${APP_GUID}( :: .*)?$`, 'i');
const INSTALL_RECORD = new RegExp(`^HKCU\\\\Software\\\\${APP_GUID}( :: .*)?$`, 'i');

// What an install may add (--expect install): the rest must not differ.
export function allowedByInstall(c: Change): boolean {
  if (c.what !== 'added') return false;
  if (c.where === 'files') return START_MENU_SHORTCUT.test(c.path);
  if (c.where === 'registry') return UNINSTALL_ENTRY.test(c.path) || INSTALL_RECORD.test(c.path);
  return false;
}

// The changes that are not allowed: none for a run, the installer's own for an install, none left after an uninstall.
export function unexpected(changes: Change[], expect: Expect): Change[] {
  return changes.filter((c) => counts(c, expect)).filter((c) => !(expect === 'install' && allowedByInstall(c)));
}

export const describe = (c: Change): string =>
  `${c.what.padEnd(7)} ${c.where.padEnd(8)} ${c.path}${c.what === 'changed' ? `  (${c.before} -> ${c.after})` : c.after && c.after !== 'dir' ? `  (${c.after})` : ''}`;

// ---- reg.exe query output -------------------------------------------------------------------

/* `reg query <key> /s` prints each key on its own line, then its values
   indented: "    <name>    <REG_TYPE>    <data>" ("(기본값)"/"(Default)" for
   the default value, whatever the system's language).  A value's data may
   be empty. */
export function parseRegQuery(text: string, into: Record<string, string> = {}): Record<string, string> {
  let key: string | null = null;
  for (const raw of text.split(/\r?\n/)) {
    if (!raw.trim()) continue;
    if (/^HKEY_/.test(raw)) {
      key = raw.trim().replace(/^HKEY_CURRENT_USER/, 'HKCU').replace(/^HKEY_LOCAL_MACHINE/, 'HKLM');
      into[key] = 'key';
      continue;
    }
    const m = /^ {4}(.*?) {4}(REG_[A-Z_]+)(?: {4}(.*))?$/.exec(raw);
    if (key && m) {
      const name = /^\((Default|기본값)\)$/.test(m[1]) ? '(Default)' : m[1];
      into[`${key} :: ${name}`] = `${m[2]} ${m[3] ?? ''}`.trimEnd();
    }
  }
  return into;
}

// ---- taking a snapshot (Windows) -------------------------------------------------------------

function reg(args: string[]): string {
  try {
    return execFileSync('reg.exe', ['query', ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], maxBuffer: 256 << 20, windowsHide: true });
  } catch {
    return '';   // no such key (reg exits 1)
  }
}

function registry(): Record<string, string> {
  const r: Record<string, string> = {};
  for (const k of ['HKCU\\Software\\JavaSoft', 'HKLM\\Software\\JavaSoft', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall',
    'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce',
    'HKCU\\Software\\Microsoft\\Installer', 'HKCU\\Software\\Classes\\.circ']) {
    parseRegQuery(reg([k, '/s']), r);
  }
  // Every key of HKCU\Software itself but Microsoft's and Classes (the program and the installer must not add one).
  const top = Object.keys(parseRegQuery(reg(['HKCU\\Software']))).filter((k) => /^HKCU\\Software\\[^\\]+$/.test(k) && !k.includes(' :: '));
  for (const k of top) {
    if (/^HKCU\\Software\\(Microsoft|Classes)$/i.test(k)) { r[k] = 'key'; continue; }
    parseRegQuery(reg([k, '/s']), r);
  }
  // Under Classes: any key named after this program (a file type, a ProgID).
  const classes = parseRegQuery(reg(['HKCU\\Software\\Classes', '/s', '/k', '/f', 'hallym']));
  for (const k of Object.keys(classes)) if (!k.includes(' :: ') && k !== 'HKCU\\Software\\Classes') parseRegQuery(reg([k, '/s']), r);
  return r;
}

function walk(root: string, name: string, into: Record<string, string>, skip: (full: string) => boolean): void {
  if (!existsSync(root)) return;
  const stack = [root];
  while (stack.length) {
    const dir = stack.pop()!;
    let entries: import('node:fs').Dirent[];
    try { entries = readdirSync(dir, { withFileTypes: true }); } catch { continue; }
    for (const e of entries) {
      const full = path.join(dir, e.name);
      if (skip(full)) continue;
      const rel = `${name}\\${path.relative(root, full)}`;
      // A junction or link (LOCALAPPDATA\Application Data -> LOCALAPPDATA): itself, not what it points to.
      if (e.isSymbolicLink()) {
        into[rel] = 'link';
      } else if (e.isDirectory()) {
        into[rel] = 'dir';
        stack.push(full);
      } else {
        try {
          const s = statSync(full);
          into[rel] = `${s.size} ${Math.round(s.mtimeMs)}`;
        } catch { into[rel] = 'unreadable'; }
      }
    }
  }
}

export function snapshot(env: NodeJS.ProcessEnv = process.env): State {
  const appData = env.APPDATA!;
  const local = env.LOCALAPPDATA!;
  const temp = env.TEMP ?? path.join(local, 'Temp');
  const install = path.join(local, 'Programs', INSTALL_FOLDER).toLowerCase();
  const skip = (full: string) => {
    const f = full.toLowerCase();
    return f === install || f === temp.toLowerCase() || f === path.join(local, 'Temp').toLowerCase();
  };
  const files: Record<string, string> = {};
  walk(appData, 'APPDATA', files, skip);
  walk(local, 'LOCALAPPDATA', files, skip);
  walk(path.join(env.USERPROFILE ?? '', 'Desktop'), 'DESKTOP', files, skip);
  if (env.PUBLIC) walk(path.join(env.PUBLIC, 'Desktop'), 'PUBLIC_DESKTOP', files, skip);
  if (env.ProgramData) walk(path.join(env.ProgramData, 'Microsoft\\Windows\\Start Menu'), 'COMMON_START_MENU', files, skip);
  // %TEMP%: its own entries, and inside this program's folder everything.
  const tempState: Record<string, string> = {};
  for (const e of (() => { try { return readdirSync(temp, { withFileTypes: true }); } catch { return []; } })()) {
    tempState[`TEMP\\${e.name}`] = e.isDirectory() ? 'dir' : 'file';
    if (e.isDirectory() && OUR_TEMP.test(e.name)) walk(path.join(temp, e.name), `TEMP\\${e.name}`, tempState, () => false);
  }
  return { files, temp: tempState, registry: registry(), taken: new Date().toISOString() };
}

// ---- the command line -----------------------------------------------------------------------

function main(argv: string[]): number {
  const [command, ...rest] = argv;
  if (command === 'snapshot' && rest[0]) {
    const t0 = Date.now();
    const s = snapshot();
    writeFileSync(rest[0], JSON.stringify(s));
    console.log(`snapshot ${rest[0]}: ${Object.keys(s.files).length} files and folders, ${Object.keys(s.temp).length} in %TEMP%, ${Object.keys(s.registry).length} registry entries (${Date.now() - t0} ms)`);
    return 0;
  }
  if (command === 'diff' && rest[0] && rest[1]) {
    const a = JSON.parse(readFileSync(rest[0], 'utf8')) as State;
    const b = JSON.parse(readFileSync(rest[1], 'utf8')) as State;
    const at = rest.indexOf('--expect');
    const expect = (at >= 0 ? rest[at + 1] : 'none') as Expect;
    const all = diffStates(a, b);
    const bad = unexpected(all, expect);
    const lines = [
      `${rest[0]} -> ${rest[1]} (expect ${expect}): ${all.length} difference(s), ${bad.length} not allowed`,
      ...all.map((c) => (bad.includes(c) ? `FAIL  ${describe(c)}` : notOurs(c, expect) ? `info  ${describe(c)}  -- ${notOurs(c, expect)}` : `ok    ${describe(c)}`)),
    ];
    console.log(lines.join('\n'));
    const r = rest.indexOf('--report');
    if (r >= 0 && rest[r + 1]) writeFileSync(rest[r + 1], `${lines.join('\n')}\n`);
    return bad.length ? 1 : 0;
  }
  console.error('usage: state.ts snapshot <out.json> | diff <before.json> <after.json> [--expect none|install|uninstalled] [--report <file>]');
  return 2;
}

if (import.meta.main) process.exit(main(process.argv.slice(2)));
