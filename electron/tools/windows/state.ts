/* What the program and its installer leave on a Windows PC (N-23, D-148;
   the lab-PC rule, v2 brief 7): a snapshot of the places they could write,
   the difference between two snapshots, and Windows' own noise measured
   in between.  Read-only -- it lists folders and runs reg.exe query,
   nothing else, so that taking a snapshot changes none of what it looks at
   (PowerShell would: its own profile data in %LOCALAPPDATA%).

     node tools/windows/state.ts snapshot <out.json>
     node tools/windows/state.ts noise <a.json> <b.json> <out.json>
     node tools/windows/state.ts diff <before.json> <after.json> [--expect none|install|uninstalled] [--noise <noise.json>] [--audit] [--report <file>]

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

   Windows changes its own places all the time, and which ones changes with
   the runner's image and timing.  So it is measured, not listed: before each
   checked period a control period -- snapshot A, the harness's own helpers
   started as in the checked period, the same wait, snapshot B, nothing of
   ours running -- and every place that changed from A to B is Windows' noise
   for that period (report/noise-<period>.json; --noise).  Only the control
   period measured just before a check excuses anything in it: not the union
   of all, so that a control period after something of ours ran cannot
   excuse a later check.  A change in a checked period counts unless it is
   at such a place, exactly,
   or in ALLOWED below: the few records Windows keeps of any program that
   starts, the test tool's own folder, and for an install what Windows does
   when any program is installed.  A change that names this program counts
   even at a noise place, and everywhere but in ALLOWED's entries that say it
   may (mayName: Windows' records of every program, by its app id).

   What Windows writes only once in a while -- Explorer flushing its icon
   cache, rolling the shell's versioned caches over and removing the old
   version later, its session counter -- a control period may miss (D-164).
   For those places the audit trail says who wrote them (tools/windows/
   audit.ps1 turns it on; auditedWriters reads it back, --audit): ALLOWED's
   entries with `writers` let a change there through only when it was
   written, and every write came from Windows' own program (Explorer), never
   the program, its installer or its uninstaller. */

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

// ---- Windows' own noise, measured -------------------------------------------------------------

/* The places Windows changed by itself in the control period(s) a check is given: each place
   (where + path, exactly: not its folder, not its neighbours) -> the control period it was seen in.  A control
   period is as long as the period it stands for, with the same helpers of the harness, and runs
   while nothing of ours does (the callers check that), so what changes in it is not ours. */
export type Noise = Map<string, string>;
export const NO_NOISE: Noise = new Map();
export const noiseKey = (c: Pick<Change, 'where' | 'path'>): string => `${c.where}\t${c.path}`;

export interface NoiseFile {
  name: string;          // the checked period it stands for (first-run, clean, uninstall, ...)
  from: string;          // snapshot A's time
  to: string;            // snapshot B's time
  changes: Change[];     // A -> B
}

export function measureNoise(name: string, a: State, b: State): NoiseFile {
  return { name, from: a.taken, to: b.taken, changes: diffStates(a, b) };
}

export function noiseOf(files: NoiseFile[]): Noise {
  const n: Noise = new Map();
  for (const f of files) for (const c of f.changes) if (!n.has(noiseKey(c))) n.set(noiseKey(c), f.name);
  return n;
}

// The control period(s) named for a check, each its own file (report/noise-<name>.json): a missing one is an error,
// never "no noise" silently taken from elsewhere.
export function readNoise(files: string[]): NoiseFile[] {
  return files.map((f) => JSON.parse(readFileSync(f, 'utf8')) as NoiseFile);
}

// ---- who wrote it: Windows' audit trail (D-164) ------------------------------------------------

/* The places tools/windows/audit.ps1 audits -> the programs (their image paths) that wrote each in a period: a file
   written, appended, its attributes set or deleted (event 4663 with such an access), a registry value set (4657).
   Keyed as the snapshots' changes are (noiseKey).  Only these places are ever in it. */
export type Writers = Map<string, Set<string>>;
export const NO_WRITERS: Writers = new Map();

const FILE_WRITES = 0x2 | 0x4 | 0x10 | 0x100 | 0x10000;   // WriteData, AppendData, WriteEA, WriteAttributes, DELETE

const xmlText = (t: string): string => t.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');

// wevtutil's XML (one <Event> after another) -> the writers, the paths made the snapshots' (LOCALAPPDATA\..., HKCU\...).
export function parseAudit(xml: string, env: { LOCALAPPDATA?: string; APPDATA?: string } = process.env): Writers {
  const out: Writers = new Map();
  const roots: [string, string][] = [];
  if (env.LOCALAPPDATA) roots.push([env.LOCALAPPDATA.toLowerCase(), 'LOCALAPPDATA']);
  if (env.APPDATA) roots.push([env.APPDATA.toLowerCase(), 'APPDATA']);
  const add = (key: string, image: string) => { if (!out.has(key)) out.set(key, new Set()); if (image) out.get(key)!.add(image); };
  for (const ev of xml.split(/<\/Event>/)) {
    const id = /<EventID[^>]*>(\d+)<\/EventID>/.exec(ev)?.[1];
    if (id !== '4663' && id !== '4657') continue;
    const data: Record<string, string> = {};
    for (const m of ev.matchAll(/<Data Name=['"]([^'"]+)['"]>([^<]*)<\/Data>/g)) data[m[1]] = xmlText(m[2]);
    const object = data.ObjectName ?? '';
    // a service host: its process id and the event's time too (auditedWriters names its services, when it is sure)
    const at = /<TimeCreated SystemTime=['"]([^'"]+)['"]/.exec(ev)?.[1] ?? '';
    const image = /\\svchost\.exe$/i.test(data.ProcessName ?? '') && data.ProcessId ? `${data.ProcessName} #${parseInt(data.ProcessId, 16)}@${at}` : data.ProcessName ?? '';
    if (id === '4657') {
      const key = /^\\REGISTRY\\USER\\S-1-5-21-[\d-]+\\(.+)$/i.exec(object)?.[1];
      if (key && data.ObjectValueName !== undefined) add(noiseKey({ where: 'registry', path: `HKCU\\${key} :: ${data.ObjectValueName}` }), image);
      continue;
    }
    if (data.ObjectType !== 'File' || !(parseInt(data.AccessMask ?? '0', 16) & FILE_WRITES)) continue;
    const lower = object.toLowerCase();
    const root = roots.filter(([r]) => lower.startsWith(`${r}\\`)).sort((a, b) => b[0].length - a[0].length)[0];
    if (root) add(noiseKey({ where: 'files', path: `${root[1]}${object.slice(root[0].length)}` }), image);
  }
  return out;
}

// The audit trail between two times (ISO), read with wevtutil (read-only; PowerShell would write its own profile data).
// Read at the end of the window, so the service hosts that wrote in it are still running when tasklist names them.
// Fails, rather than answers less, when the log may not hold the whole window (auditCoverage).
export function auditedWriters(from: string, to: string, env: NodeJS.ProcessEnv = process.env): Writers {
  const wev = (args: string[]) => execFileSync('wevtutil.exe', args, { encoding: 'utf8', maxBuffer: 1 << 30, windowsHide: true });
  const q = `*[System[(EventID=4663 or EventID=4657) and TimeCreated[@SystemTime>='${from}' and @SystemTime<='${to}']]]`;
  const xml = wev(['qe', 'Security', `/q:${q}`, '/f:xml']);
  const services = parseTasklist(execFileSync('tasklist.exe', ['/svc', '/fo', 'csv', '/nh'], { encoding: 'utf8', windowsHide: true }));
  const raw = execFileSync('wmic.exe', ['process', 'where', "name='svchost.exe'", 'get', 'CreationDate,ProcessId', '/value'], { windowsHide: true, maxBuffer: 16 << 20 });
  const created = parseWmicCreated(raw.includes(0) ? raw.toString('utf16le') : raw.toString('latin1'));
  const oldest = /<TimeCreated SystemTime=['"]([^'"]+)['"]/.exec(wev(['qe', 'Security', '/c:1', '/f:xml']))?.[1];
  const size = Number(/fileSize:\s*(\d+)/.exec(wev(['gli', 'Security']))?.[1] ?? NaN);
  const max = Number(/maxSize:\s*(\d+)/.exec(wev(['gl', 'Security']))?.[1] ?? NaN);
  auditCoverage(from, oldest, size, max);
  return nameServices(parseAudit(xml, env), services, created);
}

/* The Security log holds the whole window: its oldest event is no newer than the window's start, and it is not full
   (a full log overwrites its oldest events).  Otherwise a write in the window may be gone from it: an error, never
   "nothing written". */
export function auditCoverage(from: string, oldest: string | undefined, fileSize: number, maxSize: number): void {
  if (!oldest) throw new Error('audit trail: the Security log has no events (is auditing on? tools/windows/audit.ps1)');
  if (Date.parse(oldest) > Date.parse(from)) throw new Error(`audit trail: the Security log's oldest event (${oldest}) is newer than the window's start (${from})`);
  if (!(fileSize > 0) || !(maxSize > 0)) throw new Error(`audit trail: the Security log's size unknown (${fileSize} of ${maxSize})`);
  if (fileSize >= maxSize * 0.95) throw new Error(`audit trail: the Security log is full (${fileSize} of ${maxSize} bytes) and may have overwritten the window`);
}

// tasklist /svc /fo csv /nh: "image","pid","services" -> pid -> services.
export function parseTasklist(csv: string): Map<number, string> {
  const out = new Map<number, string>();
  for (const line of csv.split(/\r?\n/)) {
    const m = /^"([^"]*)","(\d+)","([^"]*)"/.exec(line.trim());
    if (m) out.set(Number(m[2]), m[3]);
  }
  return out;
}

// wmic's "CreationDate=20260929031500.123456+540" (local time, the offset in minutes) -> ISO (UTC).
export function wmicTime(t: string): string | undefined {
  const m = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})\.(\d{3})\d*([+-]\d+)$/.exec(t.trim());
  if (!m) return undefined;
  const local = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6], +m[7]);
  return new Date(local - Number(m[8]) * 60_000).toISOString();
}

// wmic process ... get CreationDate,ProcessId /value -> pid -> its start (ISO).
export function parseWmicCreated(text: string): Map<number, string> {
  const out = new Map<number, string>();
  for (const block of text.split(/(?:\r?\n\s*){2,}/)) {
    const c = /CreationDate=(\S+)/.exec(block)?.[1];
    const pid = /ProcessId=(\d+)/.exec(block)?.[1];
    const at = c ? wmicTime(c) : undefined;
    if (pid && at) out.set(Number(pid), at);
  }
  return out;
}

/* A service host's "#<pid>@<event time>" -> "[<its services>]" -- only when the process with that id is running now,
   started no later than the event (not an id used again), and tasklist names real services for it.  Otherwise
   "[pid <n> not named]", which WINDOWS_WRITERS does not take: an unknown writer counts. */
export function nameServices(w: Writers, services: Map<number, string>, created: Map<number, string>): Writers {
  const out: Writers = new Map();
  const name = (pid: number, at: string): string => {
    const svc = services.get(pid);
    const start = created.get(pid);
    const ok = svc && /^[A-Za-z][\w.-]*(,[A-Za-z][\w.-]*)*$/.test(svc) && start && at && Date.parse(start) <= Date.parse(at);
    return ok ? ` [${svc}]` : ` [pid ${pid} not named]`;
  };
  for (const [k, set] of w) out.set(k, new Set([...set].map((p) => p.replace(/ #(\d+)@(.*)$/, (_, pid: string, at: string) => name(Number(pid), at)))));
  return out;
}

// A period's audit window: from a minute before its first snapshot was finished (the snapshot's walk takes seconds) to now.
export const auditFrom = (taken: string): string => new Date(Date.parse(taken) - 60_000).toISOString();

// ---- what does not count ----------------------------------------------------------------------

/* A check is a run of the program ('none'), an install ('install'), or an uninstall
   ('uninstalled': compared with before the install, so the install's and the run's are in it too). */
export type Expect = 'none' | 'install' | 'uninstalled';

export interface Allowed {
  where: Change['where'];
  what: Change['what'][];
  path: RegExp;
  in: Expect[];
  mayName?: boolean;     // may name this program, by its app id (otherwise a change that does counts)
  data?: RegExp;         // registry: the value's data, exactly
  keyOnly?: boolean;     // registry: a key without values
  dirOnly?: boolean;     // files: a folder (and nothing in it)
  writers?: RegExp;      // only when the audit trail has this place written in the period, by these programs only (D-164)
  why: string;
}

const ALL: Expect[] = ['none', 'install', 'uninstalled'];
const INSTALLING: Expect[] = ['install', 'uninstalled'];
const SEARCH = 'LOCALAPPDATA\\\\Packages\\\\Microsoft\\.Windows\\.Search_cw5n1h2txyewy\\\\';
/* Windows' own programs, the only ones the audit trail may show writing Explorer's caches and counters (D-164):
   Explorer (the shell) and svchost.exe in System32, the host of Windows' services (the shell's versioned caches are
   also written by a service; the per-user installer, run without elevation, can register none) -- a svchost only
   with the services tasklist named for it (nameServices): never bare, unnamed, ended or N/A. */
export const WINDOWS_WRITERS = /^[A-Z]:\\Windows\\(explorer\.exe|System32\\svchost\.exe \[(?!ended\])[A-Za-z][\w.-]*(,[A-Za-z][\w.-]*)*\])$/i;

/* Where an installer puts what it installs under HKCU\Software\Microsoft and Classes: uninstall
   entries, programs run at logon, App Paths, the .circ file type and its open-with list, Windows
   Installer's products.  The install checks count every change there; the rest of those two
   keys is Windows' own (WINDOWS_STORES). */
export const INSTALLER_PLACES = /^HKCU\\Software\\(Microsoft\\Windows\\CurrentVersion\\(Uninstall|Run|RunOnce|App Paths|Installer)|Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\\.circ|Classes\\\.circ|Microsoft\\Installer)(\\| ::|$)/i;
const WINDOWS_STORES = /^HKCU\\Software\\(Microsoft|Classes)\\/;

/* Not measured, because a control period cannot show them: what happens only when a program starts
   (each check starts one: the program, the installer, the uninstaller) or is installed, and the test
   tool's own.  Each entry says why; nothing else is let through but the measured noise. */
export const ALLOWED: Allowed[] = [
  // ---- any check: a program starts
  { where: 'temp', what: ['added'], in: ALL, dirOnly: true, path: /^TEMP\\playwright-artifacts-[A-Za-z0-9]+$/,
    why: 'Playwright (the test tool): the empty folder its test process makes for each launch, kept until the test run ends' },
  { where: 'registry', what: ['added'], in: ALL, keyOnly: true, path: /^HKCU\\Software\\Microsoft\\Spelling$/,
    why: 'Windows\' spell checking, its per-user key made empty when Chromium asks at its start which languages there are; no language is opened, no word list made (D-148 13)' },
  { where: 'registry', what: ['added'], in: ALL, keyOnly: true, path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\WinTrust(\\Trust Providers(\\Software Publishing)?)?$/,
    why: 'WinTrust\'s per-user settings, made with their default the first time a program in the session has its signature checked (any program)' },
  { where: 'registry', what: ['added'], in: ALL, data: /^REG_DWORD 0x23c00$/,
    path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\WinTrust\\Trust Providers\\Software Publishing :: State$/,
    why: 'WinTrust\'s default state (0x23c00), written with that key' },
  { where: 'registry', what: ['added', 'changed'], in: ALL, mayName: true,
    path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\UserAssist\\\{[0-9A-F-]{36}\}\\Count :: xe\.np\.unyylz\.pvephvg-fghqvb$/,
    why: 'Explorer\'s launch counter for this program\'s app id (ROT13 of kr.ac.hallym.circuit-studio), kept by Windows for every program that starts' },
  { where: 'files', what: ['added', 'changed'], in: ALL, mayName: true, path: new RegExp(`^${SEARCH}LocalState\\\\AppIconCache\\\\100\\\\kr_ac_hallym_circuit-studio$`),
    why: 'Windows Search\'s icon for this program\'s Start menu entry, by its app id, kept by Windows Search for every program' },
  { where: 'registry', what: ['added', 'changed'], in: ALL, data: /^REG_BINARY [0-9A-F]{16}$/,
    path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings :: QuietHoursTelemetryLastRun$/,
    why: 'the notification platform\'s quiet-hours telemetry time (a time only), written once a session 1 to 8 s after an app\'s window first comes up (seen after the program\'s first start in both setup-upgrade runs of 2026-09-28)' },
  { where: 'files', what: ['changed'], in: ALL, path: new RegExp(`^${SEARCH}Settings\\\\settings\\.dat\\.LOG[12]$`),
    why: 'the log of Windows Search\'s own settings hive, written when a program newly in the Start menu is first opened (seen on first runs after an install only, never on a second run)' },
  { where: 'files', what: ['changed'], in: ALL, path: /^LOCALAPPDATA\\Microsoft\\Windows\\UsrClass\.dat(\.LOG[12])?$/,
    why: 'the files of the HKCU\\Software\\Classes hive (the registry itself is compared key by key)' },
  // ---- any check: Explorer's own caches and counters, written once in a while (a control period may miss it) --
  // only with the audit trail's word that Explorer, and nothing else, wrote them in the period (D-164)
  { where: 'files', what: ['added', 'removed', 'changed'], in: ALL, writers: WINDOWS_WRITERS,
    path: /^LOCALAPPDATA\\Microsoft\\Windows\\Explorer\\iconcache_idx\.db$/,
    why: 'the index of Explorer\'s icon cache: it flushes it at a time of its own after drawing an icon (a new Start menu entry) (seen after the MSI-to-setup install, 2026-09-28)' },
  { where: 'files', what: ['added', 'removed', 'changed'], in: ALL, writers: WINDOWS_WRITERS,
    path: /^LOCALAPPDATA\\Microsoft\\Windows\\Caches\\(cversions\.\d+\.db|\{3DA71D5A-20CC-432F-A115-DFE92379E91F\}\.\d+\.ver0x[0-9a-f]{16}\.db)$/,
    why: 'the shell\'s versioned caches: a new version when the Start menu changes, the old one removed later, at a time of Explorer\'s own (seen in a first run after an install, 2026-09-28)' },
  { where: 'registry', what: ['added', 'changed'], in: ALL, writers: WINDOWS_WRITERS,
    path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\UserAssist\\\{CEBFF5CD-ACE2-4F4F-9178-9926F41749EA\}\\Count :: HRZR_PGYFRFFVBA$/,
    why: 'Explorer\'s session record (UEME_CTLSESSION in ROT13), rewritten at a time of its own while a user is logged on (seen in a run check, 2026-09-28)' },
  // ---- an install or an uninstall: Windows' own stores, which Windows changes when any program is installed or
  // removed; there the checks count the installer's places (INSTALLER_PLACES) and anything naming this program
  { where: 'registry', what: ['added', 'removed', 'changed'], in: INSTALLING, path: WINDOWS_STORES,
    why: 'Windows\' own stores under HKCU\\Software\\Microsoft and Classes (Explorer, the Start menu, Search, notifications, security, crypto) change when a program is installed or removed; the install checks count there only the installer\'s places and anything naming this program' },
  { where: 'files', what: ['added', 'removed', 'changed'], in: INSTALLING,
    path: /^(LOCALAPPDATA\\Packages\\Microsoft\.Windows\.Search_cw5n1h2txyewy\\.+|LOCALAPPDATA\\Microsoft\\Windows\\(WebCache|Notifications)\\[^\\]+|APPDATA\\Microsoft\\Windows\\Recent\\(Automatic|Custom)Destinations\\[0-9a-f]{16}\.(automatic|custom)Destinations-ms)$/,
    why: 'Windows\' own stores: Windows Search re-indexing the Start menu, the shell\'s jump lists, WinINet\'s and the notification platform\'s databases' },
  { where: 'files', what: ['changed'], in: INSTALLING, path: /^LOCALAPPDATA\\Microsoft\\(Windows\\)?PowerShell\\StartupProfileData-NonInteractive$/,
    why: 'PowerShell\'s startup cache (the check script is PowerShell)' },
  { where: 'temp', what: ['added'], in: INSTALLING, path: /^TEMP\\playwright-transform-cache(\\.*)?$/,
    why: 'Playwright (the test tool): its compiled test files' },
  { where: 'files', what: ['added'], in: INSTALLING, dirOnly: true, path: /^LOCALAPPDATA\\Programs$/,
    why: 'Windows\' folder for per-user programs, left empty' },
  { where: 'registry', what: ['added'], in: INSTALLING, keyOnly: true, path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall$/,
    why: 'Windows\' per-user uninstall key, left empty' },
  { where: 'registry', what: ['added'], in: INSTALLING, keyOnly: true, path: /^HKCU\\Software\\Microsoft\\Installer(\\[^\\]+)*$/,
    why: 'Windows Installer\'s per-user keys, left empty by the 1.0.x MSI\'s install and removal' },
  { where: 'registry', what: ['added'], in: INSTALLING, keyOnly: true,
    path: /^HKCU\\Software\\Policies\\Microsoft\\SystemCertificates\\TrustedPublisher(\\(CRLs|CTLs|Certificates))?$/,
    why: 'the crypto API\'s per-user policy store, made empty when Windows checks a program\'s signature' },
  { where: 'files', what: ['added'], in: INSTALLING, mayName: true,
    path: new RegExp(`^${SEARCH}LocalState\\\\AppIconCache\\\\100\\\\C__Users_[^\\\\]+_AppData_Local_HallymCircuitStudio_HallymCircuitStudio_exe$`),
    why: 'Windows Search\'s icon for the 1.0.x MSI\'s program in the Start menu, kept by Windows Search as for any program' },
  { where: 'registry', what: ['added'], in: INSTALLING, mayName: true, path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\UFH\\SHC :: \d+$/,
    why: 'the shell\'s history of the shortcuts Windows Installer made (the 1.0.x MSI\'s, which it names; seen only where that MSI was installed)' },
  // The guided installer (D-155): its window has the focus while its pages show; the uninstaller's copy's name
  // (Un_<X>.exe in %TEMP%) does not name this program.
  { where: 'registry', what: ['added', 'changed'], in: INSTALLING, mayName: true,
    path: /^HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\UserAssist\\\{[0-9A-F-]{36}\}\\Count :: [^\n]*\\UnyylzPvephvgFghqvb-[0-9a-z.]+(-[0-9a-z.]+)?-jva-k64-frghc\.rkr$/,
    why: 'Explorer\'s launch and focus counter for the setup exe the student ran (ROT13 of <folder>\\HallymCircuitStudio-<version>-win-x64-setup.exe), kept by Windows for every program whose window has the focus: the guided installer\'s pages have it, a silent install none (seen in the first guided run, 2026-09-28)' },
];

// A change that names this program -- in its place, its value's name or its data: its name, its app id,
// its install folder, electron-builder's key for it -- is never Windows' own (UserAssist keeps program
// paths in ROT13: Unyylz), unless an entry of ALLOWED says it may.
export const NAMES_US = new RegExp(`hallym|circuit-studio|circuitstudio|unyylz|${APP_GUID}`, 'i');
export const namesUs = (c: Change): boolean => NAMES_US.test(c.path) || NAMES_US.test(c.after ?? '') || NAMES_US.test(c.before ?? '');

export function allowed(c: Change, expect: Expect, writers: Writers = NO_WRITERS): Allowed | undefined {
  for (const k of ALLOWED) {
    if (k.where !== c.where || !k.what.includes(c.what) || !k.in.includes(expect) || !k.path.test(c.path)) continue;
    if (k.writers) {
      const w = writers.get(noiseKey(c));
      if (!w || w.size === 0 || [...w].some((p) => !k.writers!.test(p))) continue;
    }
    if (k.keyOnly && c.after !== 'key') continue;
    if (k.dirOnly && c.after !== 'dir') continue;
    if (!k.mayName && namesUs(c)) continue;
    if (k.data && !k.data.test(c.after ?? '')) continue;
    if (k.path === WINDOWS_STORES && INSTALLER_PLACES.test(c.path)) continue;
    return k;
  }
  return undefined;
}

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

export type Verdict =
  | { kind: 'fail' }
  | { kind: 'ok' }                                // the installer's own (--expect install)
  | { kind: 'allowed'; why: string }              // ALLOWED
  | { kind: 'noise'; control: string };           // measured: Windows changed this place by itself

export function judge(c: Change, expect: Expect = 'none', noise: Noise = NO_NOISE, writers: Writers = NO_WRITERS): Verdict {
  if (expect === 'install' && allowedByInstall(c)) return { kind: 'ok' };
  const a = allowed(c, expect, writers);
  if (a) return { kind: 'allowed', why: a.why };
  const control = noise.get(noiseKey(c));
  if (control !== undefined && !namesUs(c)) return { kind: 'noise', control };
  return { kind: 'fail' };
}

export const counts = (c: Change, expect: Expect = 'none', noise: Noise = NO_NOISE, writers: Writers = NO_WRITERS): boolean => judge(c, expect, noise, writers).kind === 'fail';

// The changes that are not allowed: none for a run, the installer's own for an install, none left after an uninstall.
export function unexpected(changes: Change[], expect: Expect, noise: Noise = NO_NOISE, writers: Writers = NO_WRITERS): Change[] {
  return changes.filter((c) => counts(c, expect, noise, writers));
}

export const describe = (c: Change): string =>
  `${c.what.padEnd(7)} ${c.where.padEnd(8)} ${c.path}${c.what === 'changed' ? `  (${c.before} -> ${c.after})` : c.after && c.after !== 'dir' ? `  (${c.after})` : ''}`;

// A check's report: a line per change -- FAIL, ok (the installer's), info (ALLOWED, why), noise (the control period).
export function report(title: string, changes: Change[], expect: Expect, controls: NoiseFile[] = [], writers?: Writers): { lines: string[]; bad: Change[] } {
  const noise = noiseOf(controls);
  const bad: Change[] = [];
  // who wrote it, where the audit trail looked (D-164)
  const by = (c: Change): string => { const w = writers?.get(noiseKey(c)); return w ? `  [written by ${[...w].join(', ') || 'nothing audited'}]` : ''; };
  const body = changes.map((c) => {
    const v = judge(c, expect, noise, writers);
    if (v.kind === 'fail') { bad.push(c); return `FAIL  ${describe(c)}${by(c)}`; }
    if (v.kind === 'ok') return `ok    ${describe(c)}`;
    if (v.kind === 'allowed') return `info  ${describe(c)}${by(c)}  -- ${v.why}`;
    return `noise ${describe(c)}  -- changed by Windows itself in the control period before ${v.control}`;
  });
  const head = `${title} (expect ${expect}): ${changes.length} difference(s), ${bad.length} not allowed; Windows' own noise: ${noise.size} place(s) in ${controls.length} control period(s)${controls.length ? ` (${controls.map((f) => f.name).join(', ')})` : ''}${writers ? `; audit trail: ${writers.size} place(s) written` : ''}`;
  return { lines: [head, ...body], bad };
}

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

export function walk(root: string, name: string, into: Record<string, string>, skip: (full: string) => boolean = () => false): void {
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

export const lastTimes: { files: number; temp: number; registry: number } = { files: 0, temp: 0, registry: 0 };

export function snapshot(env: NodeJS.ProcessEnv = process.env): State {
  const appData = env.APPDATA!;
  const local = env.LOCALAPPDATA!;
  const temp = env.TEMP ?? path.join(local, 'Temp');
  const install = path.join(local, 'Programs', INSTALL_FOLDER).toLowerCase();
  const skip = (full: string) => {
    const f = full.toLowerCase();
    return f === install || f === temp.toLowerCase() || f === path.join(local, 'Temp').toLowerCase();
  };
  let t = Date.now();
  const files: Record<string, string> = {};
  walk(appData, 'APPDATA', files, skip);
  walk(local, 'LOCALAPPDATA', files, skip);
  walk(path.join(env.USERPROFILE ?? '', 'Desktop'), 'DESKTOP', files, skip);
  if (env.PUBLIC) walk(path.join(env.PUBLIC, 'Desktop'), 'PUBLIC_DESKTOP', files, skip);
  if (env.ProgramData) walk(path.join(env.ProgramData, 'Microsoft\\Windows\\Start Menu'), 'COMMON_START_MENU', files, skip);
  lastTimes.files = Date.now() - t;
  t = Date.now();
  const tempState: Record<string, string> = {};
  walk(temp, 'TEMP', tempState);
  lastTimes.temp = Date.now() - t;
  t = Date.now();
  const reg = registry();
  lastTimes.registry = Date.now() - t;
  return { files, temp: tempState, registry: reg, taken: new Date().toISOString() };
}

/* Waits until nothing in these folders has changed -- no file or folder added or removed, no size or
   time changed (a hive's log grows without its time changing) -- for quietMs, at most maxMs. */
export async function quiet(dirs: string[], quietMs = 10_000, maxMs = 120_000): Promise<string> {
  const print = (): string => {
    const s: Record<string, string> = {};
    dirs.forEach((d, i) => walk(d, String(i), s));
    return JSON.stringify(s);
  };
  const t0 = Date.now();
  let last = print();
  let since = Date.now();
  while (Date.now() - t0 < maxMs) {
    if (Date.now() - since >= quietMs) return `quiet after ${Date.now() - t0} ms`;
    await new Promise((done) => setTimeout(done, 1000));
    const now = print();
    if (now !== last) { last = now; since = Date.now(); }
  }
  return `still changing after ${maxMs} ms`;
}

// ---- the command line -----------------------------------------------------------------------

function readState(file: string): State {
  return JSON.parse(readFileSync(file, 'utf8')) as State;
}

function main(argv: string[]): number {
  const [command, ...rest] = argv;
  const option = (name: string): string | undefined => { const i = rest.indexOf(name); return i >= 0 ? rest[i + 1] : undefined; };
  if (command === 'snapshot' && rest[0]) {
    const t0 = Date.now();
    const s = snapshot();
    writeFileSync(rest[0], JSON.stringify(s));
    console.log(`snapshot ${rest[0]}: ${Object.keys(s.files).length} files and folders, ${Object.keys(s.temp).length} in %TEMP%, ${Object.keys(s.registry).length} registry entries (${Date.now() - t0} ms: files ${lastTimes.files}, temp ${lastTimes.temp}, registry ${lastTimes.registry})`);
    return 0;
  }
  if (command === 'noise' && rest[0] && rest[1] && rest[2]) {
    const name = path.basename(rest[2]).replace(/^noise-|\.json$/g, '');
    const n = measureNoise(name, readState(rest[0]), readState(rest[1]));
    writeFileSync(rest[2], JSON.stringify(n, null, 1));
    console.log([`noise before ${name} (${n.from} -> ${n.to}): ${n.changes.length} place(s) changed by Windows itself`, ...n.changes.map((c) => `      ${describe(c)}`)].join('\n'));
    return 0;
  }
  if (command === 'diff' && rest[0] && rest[1]) {
    const expect = (option('--expect') ?? 'none') as Expect;
    const noise = rest.flatMap((a, i) => (a === '--noise' && rest[i + 1] ? [rest[i + 1]] : []));
    const [a, b] = [readState(rest[0]), readState(rest[1])];
    // --audit: who wrote Explorer's places, from the first snapshot to now (tools/windows/audit.ps1 turned the trail on)
    const writers = rest.includes('--audit') ? auditedWriters(auditFrom(a.taken), new Date().toISOString()) : undefined;
    const { lines, bad } = report(`${rest[0]} -> ${rest[1]}`, diffStates(a, b), expect, readNoise(noise), writers);
    console.log(lines.join('\n'));
    const r = option('--report');
    if (r) writeFileSync(r, `${lines.join('\n')}\n`);
    return bad.length ? 1 : 0;
  }
  console.error('usage: state.ts snapshot <out.json> | noise <a.json> <b.json> <out.json> | diff <before.json> <after.json> [--expect none|install|uninstalled] [--noise <noise.json>]... [--audit] [--report <file>]');
  return 2;
}

if (import.meta.main) process.exit(main(process.argv.slice(2)));
