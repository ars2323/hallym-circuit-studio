/* What the program and its installer leave on a Windows PC (N-23, D-148;
   the lab-PC rule, v2 brief 7): a snapshot of the places they could write,
   and the difference between two snapshots.  Read-only -- it lists folders
   and runs reg.exe query, nothing else, so that taking a snapshot changes
   none of what it looks at (PowerShell would: its own profile data in
   %LOCALAPPDATA%).

     node tools/windows/state.ts snapshot <out.json>
     node tools/windows/state.ts diff <before.json> <after.json> [--expect none|install|uninstalled] [--report <file>]

   The places:
     files     %APPDATA%, %LOCALAPPDATA% (not the install folder; Temp is
               its own place), the desktop (the user's and the public one),
               the all-users Start menu: each file with its size and time,
               each folder, each link (not followed)
     temp      all of %TEMP%, the same way
     registry  all of HKCU\Software (Microsoft's, Classes and Policies
               included) and HKLM\Software\JavaSoft (Java's preferences for
               all users): each key, each value with its data

   --expect none         a run of the program: nothing may differ
   --expect install      only what the installer writes: the Start menu
                         shortcut, the uninstall entry and electron-builder's
                         install record beside it (HKCU\Software\<guid>); the
                         install folder itself is not looked into
   --expect uninstalled  nothing left (compared with before the install)
   What Windows and the test tools themselves change is not counted only
   where it was seen on the runner: KNOWN below, each with its place, its
   kind of change, the checks it may appear in and why (reported as "info").
   Anything else counts. */

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

/* What Windows and the test tools themselves change, each seen on the CI
   runner (D-148 12), by exact place, by kind of change, and only in the
   checks it was seen in: anything else in these places, or these changes
   in another check, count.  A change that names this program counts even
   there (NAMES_US), but for the records Windows keeps of every program --
   its launch counter, Windows Search's icon, the shell's shortcut history --
   which say so (mayName).  A check is a run of the program ('none'), an
   install ('install'), or an uninstall ('uninstalled': compared with before
   the install, so the install's and the run's are in it too). */
export type Expect = 'none' | 'install' | 'uninstalled';

export interface Known {
  where: Change['where'];
  what: Change['what'][];
  path: RegExp;
  in: Expect[];
  mayName?: boolean;     // may name this program (otherwise a change that does counts)
  keyOnly?: boolean;     // registry: a key without values
  dirOnly?: boolean;     // files: a folder (and nothing in it)
  why: string;
}

const ALL: Expect[] = ['none', 'install', 'uninstalled'];
const INSTALLING: Expect[] = ['install', 'uninstalled'];
const G = '\\{[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}\\}';
const SEARCH = 'LOCALAPPDATA\\\\Packages\\\\Microsoft\\.Windows\\.Search_cw5n1h2txyewy\\\\';

export const KNOWN: Known[] = [
  // ---- the test tools
  { where: 'temp', what: ['added'], in: ALL, path: /^TEMP\\playwright-artifacts-[A-Za-z0-9]+(\\.*)?$/,
    why: 'Playwright (the test tool): a folder per launch, until the test run ends' },
  { where: 'temp', what: ['added'], in: INSTALLING, path: /^TEMP\\playwright-transform-cache(\\.*)?$/,
    why: 'Playwright (the test tool): its compiled test files' },
  { where: 'files', what: ['changed'], in: INSTALLING, path: /^LOCALAPPDATA\\Microsoft\\(Windows\\)?PowerShell\\StartupProfileData-NonInteractive$/,
    why: 'PowerShell\'s startup cache (the check script is PowerShell)' },
  // ---- Windows reacting to an install or an uninstall (the Start menu changed, a program ran from a download)
  { where: 'files', what: ['added', 'removed', 'changed'], in: INSTALLING,
    path: new RegExp(`^${SEARCH}(LocalState\\\\(AppIconCache(\\\\100(\\\\[^\\\\]+)?)?|ConstraintIndex\\\\Apps_${G}(\\\\[^\\\\]+)?|DeviceSearchCache\\\\AppCache\\d+\\.txt)|Settings\\\\settings\\.dat\\.LOG[12])$`),
    why: 'Windows Search re-indexing the Start menu\'s programs' },
  { where: 'files', what: ['added', 'removed'], in: INSTALLING, path: new RegExp(`^LOCALAPPDATA\\\\Microsoft\\\\Windows\\\\Caches\\\\${G}\\.\\d+\\.ver0x[0-9a-f]+\\.db$`),
    why: 'the shell\'s cache of the Start menu' },
  { where: 'files', what: ['changed'], in: INSTALLING, path: /^LOCALAPPDATA\\Microsoft\\Windows\\WebCache\\(V01\.log|WebCacheV01\.dat|WebCacheV01\.jfm)$/,
    why: 'WinINet\'s cache database (Windows checking a downloaded program)' },
  { where: 'files', what: ['changed'], in: INSTALLING, path: /^LOCALAPPDATA\\Microsoft\\Windows\\Notifications\\wpndatabase\.db-wal$/,
    why: 'the notification platform\'s database (a Start menu entry went)' },
  { where: 'files', what: ['changed'], in: ALL, path: /^LOCALAPPDATA\\Microsoft\\Windows\\UsrClass\.dat\.LOG[12]$/,
    why: 'the HKCU\\Software\\Classes hive\'s own log (the registry is compared key by key)' },
  { where: 'files', what: ['added'], in: INSTALLING, dirOnly: true, path: /^LOCALAPPDATA\\Programs$/,
    why: 'Windows\' folder for per-user programs, left empty' },
  { where: 'registry', what: ['added'], in: INSTALLING, keyOnly: true, path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall$/,
    why: 'Windows\' per-user uninstall key, left empty' },
  { where: 'registry', what: ['added'], in: INSTALLING, keyOnly: true,
    path: /^HKCU\\Software\\Policies\\Microsoft\\SystemCertificates\\TrustedPublisher(\\(CRLs|CTLs|Certificates))?$/,
    why: 'the crypto API\'s per-user policy store, made empty when Windows checks a program\'s signature' },
  { where: 'registry', what: ['added'], in: INSTALLING, keyOnly: true, path: /^HKCU\\Software\\Microsoft\\Installer(\\[^\\]+)*$/,
    why: 'Windows Installer\'s per-user keys, left empty by the 1.0.x MSI\'s install and removal' },
  { where: 'registry', what: ['added'], in: INSTALLING, keyOnly: true,
    path: /^HKCU\\Software\\Microsoft\\SystemCertificates\\TrustedPublisher(\\(CRLs|CTLs|Certificates))?$/,
    why: 'the crypto API\'s per-user store, made empty when Windows checks a program\'s signature' },
  { where: 'registry', what: ['added'], in: INSTALLING, keyOnly: true, path: /^HKCU\\Software\\Microsoft\\RestartManager$/,
    why: 'the Restart Manager\'s per-user key, left empty (Windows Installer asks it which programs use the files)' },
  // ---- Explorer and the Start menu keeping up with a program coming or going (their own stores)
  { where: 'files', what: ['added', 'changed'], in: INSTALLING,
    path: /^APPDATA\\Microsoft\\Windows\\Recent\\AutomaticDestinations\\(13d33cf42d4c3237|93b890b537dcc3c5|73d6a8f0346f297b)\.automaticDestinations-ms$/,
    why: 'the shell\'s jump lists made while Windows Installer and the uninstaller ran (these three names on every runner)' },
  { where: 'registry', what: ['added', 'removed', 'changed'], in: INSTALLING,
    path: /^HKCU\\Software\\Classes\\Local Settings\\MuiCache\\\d+\\[0-9A-F]{8}( :: .*)?$/,
    why: 'the shell\'s cache of Windows\' own display names (Explorer rebuilds it)' },
  { where: 'registry', what: ['added'], in: INSTALLING,
    path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\(?!\.circ(\\| ::|$))\.[^\\:]+(\\OpenWithProgids)?( :: .*)?$/,
    why: 'Explorer\'s per-user open-with lists (Windows Media Player\'s file types, filled in the background; never .circ)' },
  { where: 'registry', what: ['added', 'changed'], in: INSTALLING,
    path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\(Taskband|UserAssist\\\{[0-9A-F-]{36}\}(\\Count)?)( :: .*)?$/,
    why: 'Explorer\'s taskbar layout and program-launch counters' },
  { where: 'registry', what: ['added', 'changed'], in: INSTALLING,
    path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\CloudStore\\Store\\Cache\\DefaultAccount\\[^\\]+(\\Current)?( :: Data)?$/,
    why: 'the Start menu\'s layout store' },
  { where: 'registry', what: ['changed'], in: INSTALLING,
    path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Search( :: InstalledWin32AppsRevision|\\Microsoft\.Windows\.Search_cw5n1h2txyewy\\AppsConstraintIndex :: LatestConstraintIndexFolder)$/,
    why: 'Windows Search\'s revision of the installed programs' },
  { where: 'registry', what: ['added', 'changed'], in: INSTALLING,
    path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings\\Windows\.SystemToast\.[A-Za-z]+( :: .*)?$/,
    why: 'Windows\' own notifications\' settings (its startup-app notice)' },
  { where: 'registry', what: ['added'], in: INSTALLING, path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\RunNotification( :: .*)?$/,
    why: 'Windows\' startup-app notice, naming the runner\'s own startup programs' },
  { where: 'files', what: ['added'], in: INSTALLING, mayName: true,
    path: new RegExp(`^${SEARCH}LocalState\\\\AppIconCache\\\\100\\\\(kr_ac_hallym_circuit-studio|C__Users_[^\\\\]+_AppData_Local_HallymCircuitStudio_HallymCircuitStudio_exe)$`),
    why: 'Windows Search\'s icon for a Start menu entry of this program (its app id; the 1.0.x MSI\'s program), kept by Windows Search as for any program' },
  { where: 'registry', what: ['added', 'changed'], in: ALL, mayName: true,
    path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\UserAssist\\\{[0-9A-F-]{36}\}\\Count :: xe\.np\.unyylz\.pvephvg-fghqvb$/,
    why: 'Explorer\'s launch counter for this program\'s app id (ROT13 of kr.ac.hallym.circuit-studio), kept by Windows as for every program' },
  { where: 'registry', what: ['added', 'changed'], in: ALL, path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings :: QuietHoursTelemetryLastRun$/,
    why: 'the notification platform\'s own telemetry time' },
  { where: 'registry', what: ['added'], in: INSTALLING, mayName: true, path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\UFH\\SHC :: \d+$/,
    why: 'the shell\'s history of the shortcuts Windows Installer made (the 1.0.x MSI\'s, which it names; seen only where that MSI was installed)' },
  // ---- the job itself: the screen was set to 1920x1080 before
  { where: 'files', what: ['added'], in: ALL, path: /^APPDATA\\Microsoft\\Windows\\Themes\\CachedFiles\\CachedImage_\d+_\d+_POS\d+\.jpg$/,
    why: 'the desktop wallpaper for the new screen size (the job set it before)' },
];

// A change that names this program -- in its place, its value's name or its data -- is never
// Windows' own, unless the entry says it may (UserAssist keeps program paths in ROT13: Unyylz).
export const NAMES_US = /hallym|circuit-studio|circuitstudio|unyylz/i;
const namesUs = (c: Change): boolean => NAMES_US.test(c.path) || NAMES_US.test(c.after ?? '') || NAMES_US.test(c.before ?? '');

// Why a change is Windows' or the test tools', or null when it counts.
export function notOurs(c: Change, expect: Expect = 'none'): string | null {
  for (const k of KNOWN) {
    if (k.where !== c.where || !k.what.includes(c.what) || !k.in.includes(expect) || !k.path.test(c.path)) continue;
    if (k.keyOnly && c.after !== 'key') continue;
    if (k.dirOnly && c.after !== 'dir') continue;
    if (!k.mayName && namesUs(c)) continue;
    return k.why;
  }
  return null;
}

export const counts = (c: Change, expect: Expect = 'none'): boolean => notOurs(c, expect) === null;

const exactly = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const START_MENU_SHORTCUT = new RegExp(`^APPDATA\\\\Microsoft\\\\Windows\\\\Start Menu\\\\Programs\\\\${exactly(PRODUCT_NAME)}\\.lnk$`, 'i');
const UNINSTALL_ENTRY = new RegExp(`^HKCU\\\\Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Uninstall\\\\${APP_GUID}( :: .*)?$`, 'i');
const INSTALL_RECORD = new RegExp(`^HKCU\\\\Software\\\\${APP_GUID}( :: .*)?$`, 'i');

// What an install adds (--expect install): its Start menu shortcut, its uninstall entry and
// electron-builder's install record beside it (all three removed by the uninstaller).
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
  for (const k of ['HKCU\\Software', 'HKLM\\Software\\JavaSoft']) parseRegQuery(reg([k, '/s']), r);
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
  const tempState: Record<string, string> = {};
  walk(temp, 'TEMP', tempState, () => false);
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
