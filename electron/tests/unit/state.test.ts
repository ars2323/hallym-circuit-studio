/* tools/windows/state.ts (N-23, D-148): the Windows checks' rule for what
   the program and its installer may leave.  The snapshot itself is taken
   on Windows (CI setup-e2e, setup-upgrade); here its parts that are not
   Windows: reading reg.exe's answer, the difference, what counts -- that
   Windows' own noise is measured in a control period and let through only
   at the exact places that changed there, never when a change names this
   program, and that besides it only ALLOWED's few records of any program
   (each at its exact place, kind of change and check) do not count. */

import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { test } from 'node:test';

import { APP_GUID } from '../../tools/package-config.ts';
import {
  ALLOWED, allowedByInstall, auditCoverage, auditFrom, counts, diffStates, judge, measureNoise, nameServices, noiseKey, noiseOf, parseAudit, parseRegQuery, parseTasklist, parseWmicCreated, readNoise, report,
  unexpected, wmicTime, type Change, type NoiseFile, type State, type Writers,
} from '../../tools/windows/state.ts';

const state = (p: Partial<State>): State => ({ files: {}, temp: {}, registry: {}, taken: '', ...p });
const UNINSTALL = `HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\${APP_GUID}`;
const RECORD = `HKCU\\Software\\${APP_GUID}`;
const SHORTCUT = 'APPDATA\\Microsoft\\Windows\\Start Menu\\Programs\\Hallym Circuit Studio.lnk';
const SEARCH = 'LOCALAPPDATA\\Packages\\Microsoft.Windows.Search_cw5n1h2txyewy';
const ch = (where: Change['where'], what: Change['what'], path: string, after = what === 'removed' ? undefined : '1 1'): Change =>
  ({ where, what, path, ...(after === undefined ? {} : { after }), ...(what === 'changed' ? { before: '1 0' } : {}) });
const control = (name: string, changes: Change[]): NoiseFile => ({ name, from: 'a', to: 'b', changes });
const CHECKS = ['none', 'install', 'uninstalled'] as const;

test('reg.exe query: keys, values (the default value in any language), empty data', () => {
  const text = [
    '',
    'HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs',
    '    (기본값)    REG_SZ    ',
    '',
    'HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs\\kr',
    '    last    REG_SZ    /a/b c',
    '    (Default)    REG_SZ    x',
    '    n    REG_DWORD    0x1',
    '',
    'HKEY_LOCAL_MACHINE\\Software\\JavaSoft',
    'End of search: 2 match(es) found.',
  ].join('\r\n');
  assert.deepEqual(parseRegQuery(text), {
    'HKCU\\Software\\JavaSoft\\Prefs': 'key',
    'HKCU\\Software\\JavaSoft\\Prefs :: (Default)': 'REG_SZ',
    'HKCU\\Software\\JavaSoft\\Prefs\\kr': 'key',
    'HKCU\\Software\\JavaSoft\\Prefs\\kr :: last': 'REG_SZ /a/b c',
    'HKCU\\Software\\JavaSoft\\Prefs\\kr :: (Default)': 'REG_SZ x',
    'HKCU\\Software\\JavaSoft\\Prefs\\kr :: n': 'REG_DWORD 0x1',
    'HKLM\\Software\\JavaSoft': 'key',
  });
});

test('the difference: added, removed, changed; a folder is a folder whatever its time', () => {
  const a = state({ files: { 'APPDATA\\x': '1 10', 'APPDATA\\d': 'dir', 'APPDATA\\gone': '2 2' }, registry: { 'HKCU\\Software\\A :: v': 'REG_SZ 1' } });
  const b = state({ files: { 'APPDATA\\x': '1 11', 'APPDATA\\d': 'dir', 'APPDATA\\new': 'dir' }, registry: { 'HKCU\\Software\\A :: v': 'REG_SZ 2' } });
  assert.deepEqual(diffStates(a, b), [
    { where: 'files', what: 'added', path: 'APPDATA\\new', after: 'dir' },
    { where: 'files', what: 'changed', path: 'APPDATA\\x', before: '1 10', after: '1 11' },
    { where: 'files', what: 'removed', path: 'APPDATA\\gone', before: '2 2' },
    { where: 'registry', what: 'changed', path: 'HKCU\\Software\\A :: v', before: 'REG_SZ 1', after: 'REG_SZ 2' },
  ]);
  assert.deepEqual(diffStates(a, a), []);
});

test('a run of the program may change nothing: anywhere in %APPDATA%, %LOCALAPPDATA%, %TEMP% or HKCU\\Software, Microsoft\'s keys included', () => {
  const counted: Change[] = [
    ch('files', 'added', 'APPDATA\\Hallym Circuit Studio'),
    ch('files', 'added', 'LOCALAPPDATA\\D3DSCache\\x'),
    ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Windows\\INetCache\\x'),
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\WebCache\\WebCacheV01.dat'),   // an install's, not a run's
    ch('files', 'added', `${SEARCH}\\LocalState\\AppIconCache\\100\\x`),                  // likewise
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\PowerShell\\StartupProfileData-NonInteractive'),
    ch('files', 'added', 'APPDATA\\Microsoft\\Windows\\Recent\\demo-datapath.circ.lnk'),
    ch('files', 'added', 'APPDATA\\Microsoft\\Windows\\Recent\\AutomaticDestinations\\73d6a8f0346f297b.automaticDestinations-ms'),
    ch('temp', 'added', 'TEMP\\HallymCircuitStudio'),
    ch('temp', 'added', 'TEMP\\HallymCircuitStudio\\run-1-2'),
    ch('temp', 'added', 'TEMP\\scoped_dir1234_567'),
    ch('temp', 'added', 'TEMP\\hsperfdata_runneradmin\\4321'),
    ch('temp', 'changed', 'TEMP\\other\\log.txt'),
    ch('temp', 'added', 'TEMP\\playwright-transform-cache\\x'),                          // an install's (the test compiled before)
    ch('registry', 'added', 'HKCU\\Software\\JavaSoft\\Prefs\\kr\\ac', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Direct3D\\MostRecentApplication :: Name', 'REG_SZ HallymCircuitStudio.exe'),
    ch('registry', 'added', 'HKCU\\Software\\Classes\\.circ', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Policies\\Microsoft\\SystemCertificates\\TrustedPublisher', 'key'),
  ];
  assert.deepEqual(unexpected(counted, 'none').map((c) => c.path), counted.map((c) => c.path));
  // Windows' spelling word lists (the program keeps Chromium from opening the Windows spell checker: D-148 13).
  for (const c of [ch('files', 'added', 'APPDATA\\Microsoft\\Spelling\\en-US\\default.dic', '2 1'), ch('files', 'added', 'APPDATA\\Microsoft\\Spelling', 'dir')]) {
    for (const e of CHECKS) assert.equal(counts(c, e), true, `${c.path} (${e})`);
  }
  assert.equal(counts(ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Spelling\\Options', 'key'), 'none'), true);
  // Microsoft's account service (stopped on the runner, D-148 12): counted.
  assert.equal(counts(ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Credentials\\DFBE70A7E5CC19A398EBF1B96859CE5D'), 'none'), true);
  // Playwright's own folder per launch, empty.
  assert.equal(counts(ch('temp', 'added', 'TEMP\\playwright-artifacts-Zpo2Y7', 'dir'), 'none'), false);
  assert.equal(counts(ch('temp', 'added', 'TEMP\\playwright-artifacts-Zpo2Y7', '1 1'), 'none'), true);           // a file of that name
  assert.equal(counts(ch('temp', 'added', 'TEMP\\playwright-artifacts-Zpo2Y7\\trace', 'dir'), 'none'), true);   // something in it
});

test('an install may add exactly its shortcut, its uninstall entry and electron-builder\'s install record', () => {
  const before = state({});
  const after = state({
    files: { [SHORTCUT]: '1500 1', 'LOCALAPPDATA\\Programs': 'dir' },
    registry: {
      [UNINSTALL]: 'key', [`${UNINSTALL} :: DisplayName`]: 'REG_SZ Hallym Circuit Studio 2.0.0', [RECORD]: 'key', [`${RECORD} :: InstallLocation`]: 'REG_SZ x',
      'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall': 'key',
    },
  });
  const changes = diffStates(before, after);
  assert.deepEqual(unexpected(changes, 'install'), []);
  // The installer's own four places; Windows' own empty containers are not the installer's.
  assert.deepEqual(changes.filter((c) => judge(c, 'install').kind === 'ok').map((c) => c.path).filter((p) => p !== SHORTCUT).every((p) => p.startsWith(UNINSTALL) || p.startsWith(RECORD)), true);
  assert.deepEqual(changes.filter((c) => judge(c, 'install').kind === 'allowed').map((c) => c.path), ['LOCALAPPDATA\\Programs', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall']);
  // None of it may come from a run of the program.
  assert.equal(unexpected(changes, 'none').length, changes.length);
  const more: Change[] = [
    ch('files', 'added', 'DESKTOP\\Hallym Circuit Studio.lnk'),
    ch('files', 'added', 'APPDATA\\Microsoft\\Windows\\Start Menu\\Programs\\Hallym Circuit Studio\\Hallym Circuit Studio.lnk'),
    ch('registry', 'added', 'HKCU\\Software\\Classes\\.circ', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{6206F18C-D7FA-366B-98DA-E7980F6083D6}', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\kr.ac.hallym.circuit-studio', 'key'),
    ch('registry', 'changed', `${UNINSTALL} :: DisplayName`, 'b'),
    ch('files', 'added', 'LOCALAPPDATA\\hallym-circuit-studio-updater\\installer.exe'),
  ];
  for (const c of more) {
    assert.equal(allowedByInstall(c), false, c.path);
    assert.equal(counts(c, 'install'), true, c.path);
  }
});

test('after an uninstall nothing is left: the uninstaller\'s copy in %TEMP%, an MSI product\'s values, a file in the programs folder all count', () => {
  const before = state({});
  const after = state({
    files: { 'LOCALAPPDATA\\Programs': 'dir' },
    registry: { 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall': 'key', 'HKCU\\Software\\Microsoft\\Installer': 'key', 'HKCU\\Software\\Microsoft\\Installer\\Products': 'key' },
  });
  assert.deepEqual(unexpected(diffStates(before, after), 'uninstalled'), []);
  for (const c of [
    ch('temp', 'added', 'TEMP\\~nsuA.tmp', 'dir'),
    ch('temp', 'added', 'TEMP\\~nsuA.tmp\\Un_A.exe'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Installer\\Products\\7DCAC541 :: Language', 'REG_DWORD 0x412'),
    ch('files', 'added', 'LOCALAPPDATA\\Programs\\Hallym Circuit Studio\\x.dll'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall :: x', 'REG_SZ y'),
    ch('files', 'added', 'LOCALAPPDATA\\Programs', '1 1'),   // a file, not Windows' folder
  ]) assert.equal(counts(c, 'uninstalled'), true, c.path);
});

test('any check: only the records Windows keeps of any program that starts, at their exact place, kind and data; the rest is measured', () => {
  const kept: Change[] = [
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\UsrClass.dat.LOG1'),
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\UsrClass.dat'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Spelling', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\WinTrust\\Trust Providers\\Software Publishing', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\WinTrust\\Trust Providers\\Software Publishing :: State', 'REG_DWORD 0x23c00'),
    ch('temp', 'added', 'TEMP\\playwright-artifacts-Zpo2Y7', 'dir'),
    ch('files', 'changed', `${SEARCH}\\LocalState\\AppIconCache\\100\\kr_ac_hallym_circuit-studio`),
    ch('files', 'changed', `${SEARCH}\\Settings\\settings.dat.LOG2`),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings :: QuietHoursTelemetryLastRun', 'REG_BINARY 7736BA6A00000000'),
    ch('registry', 'changed', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings :: QuietHoursTelemetryLastRun', 'REG_BINARY E035BA6A00000000'),
  ];
  for (const c of kept) for (const e of CHECKS) assert.equal(counts(c, e), false, `${c.what} ${c.path} (${e})`);
  // Windows' own, but not only when a program starts: counted unless a control period measured it (not listed).
  for (const c of [
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\Notifications\\wpndatabase.db-wal'),
    ch('files', 'added', 'APPDATA\\Microsoft\\Windows\\Themes\\CachedFiles\\CachedImage_1920_1080_POS4.jpg'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\RunNotification :: SecurityHealth', 'REG_DWORD 0x0'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings\\Windows.SystemToast.StartupApp', 'key'),
    ch('registry', 'removed', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\WinTrust'),
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\WebCache\\WebCacheV01.dat'),
  ]) assert.equal(counts(c, 'none'), true, `${c.what} ${c.path}`);
  // Their neighbours count.
  for (const c of [
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\UsrClass.dat.LOG3'),
    ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Windows\\UsrClass.dat.LOG1'),
    ch('files', 'removed', 'LOCALAPPDATA\\Microsoft\\Windows\\UsrClass.dat'),
    ch('files', 'changed', `${SEARCH}\\Settings\\settings.dat`),
    ch('files', 'added', `${SEARCH}\\Settings\\settings.dat.LOG3`),
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\Explorer\\iconcache_32.db'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Spelling\\Dictionaries', 'key'),                // a language opened
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Spelling :: x', 'REG_SZ y'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\WinTrust\\Trust Providers\\Software Publishing :: State', 'REG_DWORD 0x0'),
    ch('registry', 'changed', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Search :: InstalledWin32AppsRevision', 'REG_SZ {x}'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\RestartManager', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings :: QuietHoursTelemetryLastRun', 'REG_SZ C:\\\\x'),                                  // not a time
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings :: x', 'REG_BINARY 7736BA6A00000000'),
    ch('files', 'added', 'APPDATA\\Microsoft\\Windows\\Recent\\AutomaticDestinations\\13d33cf42d4c3237.automaticDestinations-ms'),
    ch('files', 'added', `${SEARCH}\\LocalState\\AppIconCache\\100\\Chrome`),
    ch('files', 'removed', `${SEARCH}\\LocalState\\AppIconCache\\100\\kr_ac_hallym_circuit-studio`),
    ch('temp', 'added', 'TEMP\\playwright-transform-cache\\a.js'),
    ch('temp', 'added', 'TEMP\\playwright-artifactsZ', 'dir'),
  ]) assert.equal(counts(c, 'none'), true, `${c.what} ${c.path}`);
  // Every entry says why.
  for (const k of ALLOWED) assert.ok(k.why.length > 20, k.path.source);
});

test('Windows\' noise, measured: a place that changed in a control period does not count in the checked period, exactly that place, in any check', () => {
  const WEBCACHE = 'LOCALAPPDATA\\Microsoft\\Windows\\WebCache\\WebCacheV01.dat';
  const RUN = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\RunNotification :: SecurityHealth';
  const WALLPAPER = 'APPDATA\\Microsoft\\Windows\\Themes\\CachedFiles\\CachedImage_1920_1080_POS4.jpg';
  const AGENT = 'HKCU\\Software\\Vendor\\Agent :: LastRun';
  const a = state({ files: { [WEBCACHE]: '24576 5' }, registry: { [AGENT]: 'REG_SZ 1' }, taken: 't1' });
  const b = state({ files: { [WEBCACHE]: '49152 5', [WALLPAPER]: '124996 7' }, registry: { [RUN]: 'REG_DWORD 0x0' }, taken: 't2' });
  const measured = measureNoise('first-run', a, b);
  assert.deepEqual({ ...measured, changes: measured.changes.map((c) => `${c.what} ${c.path}`) },
    { name: 'first-run', from: 't1', to: 't2', changes: [`added ${WALLPAPER}`, `changed ${WEBCACHE}`, `added ${RUN}`, `removed ${AGENT}`] });
  const noise = noiseOf([measured]);
  // In both the control period and the run: not counted, and said so -- whatever the kind of change in the run.
  for (const c of [ch('files', 'changed', WEBCACHE), ch('files', 'removed', WEBCACHE), ch('registry', 'changed', RUN, 'REG_DWORD 0x3')]) {
    assert.equal(counts(c, 'none'), true, `${c.path} without the noise`);
    assert.deepEqual(judge(c, 'none', noise), { kind: 'noise', control: 'first-run' }, c.path);
  }
  for (const c of [ch('files', 'changed', WALLPAPER), ch('registry', 'added', AGENT, 'REG_SZ 2')]) {
    for (const e of CHECKS) {
      assert.equal(counts(c, e), true, `${c.path} without the noise (${e})`);
      assert.deepEqual(judge(c, e, noise), { kind: 'noise', control: 'first-run' }, `${c.path} (${e})`);
    }
  }
  // Only there: its folder, its neighbours, another value of that key, the key itself, the same path in another place count.
  for (const c of [
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\WebCache\\WebCacheV01.jfm'),
    ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Windows\\WebCache\\x'),
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\WebCache', '1 1'),
    ch('files', 'added', 'APPDATA\\Microsoft\\Windows\\Themes\\CachedFiles\\CachedImage_1920_1080_POS5.jpg'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\RunNotification :: AzureArcSetup', 'REG_DWORD 0x0'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\RunNotification', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Vendor\\Agent', 'key'),
    ch('temp', 'changed', WEBCACHE),
  ]) assert.equal(counts(c, 'none', noise), true, `${c.what} ${c.where} ${c.path}`);
  assert.deepEqual(unexpected([ch('files', 'changed', WEBCACHE), ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\WebCache\\WebCacheV01.jfm')], 'none', noise).map((c) => c.path),
    ['LOCALAPPDATA\\Microsoft\\Windows\\WebCache\\WebCacheV01.jfm']);
});

test('noise never covers a change that names this program, nor a new file in %APPDATA%, %LOCALAPPDATA% or %TEMP% at a place it did not measure', () => {
  // Places Windows changed by itself in the control period -- some naming the program: a MuiCache entry of its exe, its temp folder.
  const MUI = 'HKCU\\Software\\Classes\\Local Settings\\MuiCache\\281\\52C64B7E';
  const EXE = `${MUI} :: C:\\Users\\u\\AppData\\Local\\Programs\\Hallym Circuit Studio\\HallymCircuitStudio.exe.FriendlyAppName`;
  const JUMP = 'APPDATA\\Microsoft\\Windows\\Recent\\CustomDestinations\\x.customDestinations-ms';
  const WPN = 'LOCALAPPDATA\\Microsoft\\Windows\\Notifications\\wpndatabase.db-wal';
  const noise = noiseOf([control('first-run', [
    ch('registry', 'added', EXE, 'REG_SZ Hallym Circuit Studio'),
    ch('registry', 'added', `${MUI} :: x`, 'REG_SZ y'),
    ch('files', 'changed', JUMP),
    ch('registry', 'added', `${RECORD} :: x`, 'REG_SZ y'),
    ch('temp', 'added', 'TEMP\\HallymCircuitStudio', 'dir'),
    ch('files', 'changed', WPN),
  ])]);
  // Named -- by its name, in its data, by electron-builder's key for it: counted, in every check.
  for (const c of [
    ch('registry', 'added', EXE, 'REG_SZ Hallym Circuit Studio'),
    ch('registry', 'changed', `${MUI} :: x`, 'REG_SZ kr.ac.hallym.circuit-studio'),
    ch('registry', 'added', `${RECORD} :: x`, 'REG_SZ y'),
    ch('temp', 'added', 'TEMP\\HallymCircuitStudio', 'dir'),
    ch('files', 'changed', JUMP, '1 1 C:\\Hallym'),
  ]) {
    for (const e of ['none', 'uninstalled'] as const) assert.equal(counts(c, e, noise), true, `${c.path} ${c.after} (${e})`);
  }
  // The same places, not naming it: Windows' noise.
  assert.equal(counts(ch('registry', 'changed', `${MUI} :: x`, 'REG_SZ other'), 'none', noise), false);
  assert.equal(counts(ch('files', 'changed', JUMP), 'none', noise), false);
  assert.equal(counts(ch('files', 'changed', WPN), 'none', noise), false);
  // A new file or folder in %APPDATA%, %LOCALAPPDATA% or %TEMP% at a place the control period did not change: counted.
  for (const c of [
    ch('files', 'added', 'APPDATA\\x\\settings.json'),
    ch('files', 'added', 'APPDATA\\Microsoft\\Windows\\Notifications\\wpndatabase.db-wal'),   // another root than the noise's
    ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Windows\\Explorer\\wpndatabase.db-wal'),
    ch('files', 'added', 'LOCALAPPDATA\\electron\\Cache', 'dir'),
    ch('temp', 'added', 'TEMP\\scoped_dir1234_567', 'dir'),
    ch('temp', 'added', 'TEMP\\x.tmp'),
  ]) {
    for (const e of CHECKS) assert.equal(counts(c, e, noise), true, `${c.path} (${e})`);
  }
  // The installer's own in an install check stay the installer's.
  assert.deepEqual(judge(ch('registry', 'added', `${RECORD} :: x`, 'REG_SZ y'), 'install', noise), { kind: 'ok' });
});

test('noise per phase: a check is excused only by the control period(s) it is given -- one named file each, never the other phases\' -- and the report says what let a change through', () => {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'noise-'));
  try {
    const x = ch('files', 'changed', 'LOCALAPPDATA\\x');
    const y = ch('registry', 'changed', 'HKCU\\Software\\Vendor\\y :: v', 'REG_SZ 2');
    const late = ch('files', 'added', 'LOCALAPPDATA\\Vendor\\late.db');   // seen only in a control period after the run
    writeFileSync(path.join(dir, 'noise-clean.json'), JSON.stringify(control('clean', [x])));
    writeFileSync(path.join(dir, 'noise-first-run.json'), JSON.stringify(control('first-run', [x, y])));
    writeFileSync(path.join(dir, 'noise-uninstall.json'), JSON.stringify(control('uninstall', [late])));
    // The run's check: its own control period only; what a later control period saw still counts.
    const run = readNoise([path.join(dir, 'noise-first-run.json')]);
    assert.deepEqual(run.map((f) => f.name), ['first-run']);
    assert.deepEqual(unexpected([x, y, late], 'none', noiseOf(run)).map((c) => c.path), ['LOCALAPPDATA\\Vendor\\late.db']);
    // The install's check: the clean control period's; the run's place y counts there.
    assert.deepEqual(unexpected([x, y], 'install', noiseOf(readNoise([path.join(dir, 'noise-clean.json')]))).map((c) => c.path), ['HKCU\\Software\\Vendor\\y :: v']);
    // Given several, their union, each place with the first that saw it.
    assert.deepEqual([...noiseOf(readNoise([path.join(dir, 'noise-clean.json'), path.join(dir, 'noise-first-run.json')]))],
      [['files\tLOCALAPPDATA\\x', 'clean'], ['registry\tHKCU\\Software\\Vendor\\y :: v', 'first-run']]);
    // A control period not measured is an error, not "no noise".
    assert.throws(() => readNoise([path.join(dir, 'noise-again.json')]));
    const changes = [x, y, ch('files', 'changed', 'APPDATA\\z'), ch('temp', 'added', 'TEMP\\playwright-artifacts-Q1', 'dir'), ch('registry', 'added', RECORD, 'key')];
    const r = report('first-run', changes, 'install', run);
    assert.deepEqual(r.bad.map((c) => c.path), ['APPDATA\\z']);
    assert.equal(r.lines[0], 'first-run (expect install): 5 difference(s), 1 not allowed; Windows\' own noise: 2 place(s) in 1 control period(s) (first-run)');
    assert.deepEqual(r.lines.slice(1).map((l) => l.split(' ')[0]), ['noise', 'noise', 'FAIL', 'info', 'ok']);
    assert.match(r.lines[1], /changed by Windows itself in the control period before first-run$/);
    assert.match(r.lines[4], /-- Playwright \(the test tool\)/);
    assert.equal(report('second-run', [], 'none').lines[0], 'second-run (expect none): 0 difference(s), 0 not allowed; Windows\' own noise: 0 place(s) in 0 control period(s)');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('an install or uninstall: Windows\' own stores do not count, the installer\'s places in them and anything naming this program do', () => {
  const windows: Change[] = [
    ch('files', 'added', `${SEARCH}\\LocalState\\AppIconCache\\100\\Microsoft_AutoGenerated_{0423BC28-B989-92D0-56C0-F931E344705B}`),
    ch('files', 'added', `${SEARCH}\\LocalState\\DeviceSearchCache\\AppCache134350408112378591.txt.~tmp`),
    ch('files', 'removed', `${SEARCH}\\LocalState\\ConstraintIndex\\Apps_{4c36af59-b719-4e60-b916-9d1bc558edcb}`),
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\WebCache\\WebCacheV01.dat'),
    ch('files', 'added', 'APPDATA\\Microsoft\\Windows\\Recent\\AutomaticDestinations\\13d33cf42d4c3237.automaticDestinations-ms'),
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\PowerShell\\StartupProfileData-NonInteractive'),
    ch('temp', 'added', 'TEMP\\playwright-transform-cache\\a.js'),
    ch('registry', 'added', 'HKCU\\Software\\Classes\\Local Settings\\MuiCache\\281\\52C64B7E :: C:\\Windows\\system32,@elscore.dll,-1', 'REG_SZ Microsoft Language Detection'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.aif\\OpenWithProgids :: WMP11.AssocFile.AIFF', 'REG_NONE'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Security and Maintenance\\Checks\\{852FB1F8-5CC6-4567-9C0E-7C330F8807C2}.check.100 :: CheckSetting', 'REG_BINARY 23'),
    ch('registry', 'changed', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\CloudStore\\Store\\Cache\\DefaultAccount\\x\\Current :: Data', 'REG_BINARY 00'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\RestartManager', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Installer\\Products', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Policies\\Microsoft\\SystemCertificates\\TrustedPublisher\\CRLs', 'key'),
  ];
  for (const c of windows) {
    for (const e of ['install', 'uninstalled'] as const) assert.equal(counts(c, e), false, `${c.what} ${c.path} (${e})`);
    assert.equal(counts(c, 'none'), true, `${c.what} ${c.path} (a run)`);
  }
  // The installer's places in them, or anything naming this program: counted.
  for (const c of [
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{6206F18C-D7FA-366B-98DA-E7980F6083D6}', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall :: x', 'REG_SZ y'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run :: Tray', 'REG_SZ x.exe'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\RunOnce :: x', 'REG_SZ y'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\x.exe', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.circ\\OpenWithProgids :: x', 'REG_NONE'),
    ch('registry', 'added', 'HKCU\\Software\\Classes\\.circ', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Installer\\Products\\7DCAC541 :: Language', 'REG_DWORD 0x412'),
    ch('registry', 'added', 'HKCU\\Software\\Classes\\Applications\\HallymCircuitStudio.exe', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings\\kr.ac.hallym.circuit-studio', 'key'),
    ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Windows\\Explorer\\iconcache_32.db'),
    ch('files', 'added', 'LOCALAPPDATA\\Packages\\Some.Other.App_1234\\LocalState\\x'),
    ch('files', 'added', 'APPDATA\\Microsoft\\Windows\\Recent\\x.lnk'),
    ch('registry', 'added', 'HKCU\\Software\\Policies\\Microsoft\\SystemCertificates\\TrustedPublisher :: x', 'REG_SZ y'),
  ]) {
    for (const e of ['install', 'uninstalled'] as const) assert.equal(counts(c, e), true, `${c.what} ${c.path} (${e})`);
  }
});

test('a change that names this program is never Windows\' own -- but for Windows\' records of every program, said so', () => {
  const MUI = 'HKCU\\Software\\Classes\\Local Settings\\MuiCache\\281\\52C64B7E';
  assert.equal(counts(ch('registry', 'added', `${MUI} :: C:\\Windows\\system32,@elscore.dll,-1`, 'REG_SZ Microsoft Language Detection'), 'install'), false);
  assert.equal(counts(ch('registry', 'added', `${MUI} :: C:\\Users\\u\\AppData\\Local\\Programs\\Hallym Circuit Studio\\HallymCircuitStudio.exe.FriendlyAppName`, 'REG_SZ Hallym Circuit Studio'), 'install'), true);
  assert.equal(counts(ch('registry', 'added', `${MUI} :: x`, 'REG_SZ kr.ac.hallym.circuit-studio'), 'install'), true);
  assert.equal(counts(ch('registry', 'added', `${MUI} :: x`, `REG_SZ ${APP_GUID}`), 'install'), true);
  // Explorer's open-with lists: Windows Media Player's are Windows', .circ never is.
  const EXTS = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts';
  assert.equal(counts(ch('registry', 'added', `${EXTS}\\.aif\\OpenWithProgids :: WMP11.AssocFile.AIFF`, 'REG_NONE'), 'install'), false);
  for (const p of [`${EXTS}\\.circ`, `${EXTS}\\.circ\\OpenWithProgids`, `${EXTS}\\.circ\\OpenWithProgids :: x`, `${EXTS}\\.circ\\UserChoice`]) {
    assert.equal(counts(ch('registry', 'added', p, 'key'), 'install'), true, p);
  }
  // Windows' records that do name it, by its app id, each at its exact place (D-148 12).
  const UA = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\UserAssist\\{CEBFF5CD-ACE2-4F4F-9178-9926F41749EA}\\Count';
  for (const e of CHECKS) {
    assert.equal(counts(ch('registry', 'added', `${UA} :: xe.np.unyylz.pvephvg-fghqvb`, 'REG_BINARY 00'), e), false, e);
  }
  assert.equal(counts(ch('registry', 'added', `${UA} :: P:\\Hfref\\h\\NccQngn\\Ybpny\\Cebtenzf\\Unyylz Pvephvg Fghqvb\\UnyylzPvephvgFghqvb.rkr`, 'REG_BINARY 00'), 'uninstalled'), true);
  // The guided installer's own counter (D-155): its setup exe, wherever the student saved it, in the install checks only.
  const SETUP_UA = `${UA} :: Q:\\n\\unyylz-pvephvg-fghqvb\\ryrpgeba\\frghc\\UnyylzPvephvgFghqvb-2.0.0-nycun.0-jva-k64-frghc.rkr`;
  for (const e of ['install', 'uninstalled'] as const) assert.equal(counts(ch('registry', 'added', SETUP_UA, 'REG_BINARY 00'), e), false, e);
  assert.equal(counts(ch('registry', 'added', `${UA} :: P:\\Hfref\\h\\Qbjaybnqf\\UnyylzPvephvgFghqvb-2.1.0-jva-k64-frghc.rkr`, 'REG_BINARY 00'), 'install'), false);
  assert.equal(counts(ch('registry', 'added', SETUP_UA, 'REG_BINARY 00'), 'none'), true);  // a run of the program: counts
  assert.equal(counts(ch('registry', 'added', `${UA} :: P:\\Hfref\\h\\Qbjaybnqf\\UnyylzPvephvgFghqvb-2.1.0-jva-k64-frghc.rkr.ync`, 'REG_BINARY 00'), 'install'), true);
  assert.equal(counts(ch('files', 'added', 'APPDATA\\HallymCircuitStudio-2.0.0-win-x64-setup.exe'), 'install'), true);
  const ICON = 'LOCALAPPDATA\\Packages\\Microsoft.Windows.Search_cw5n1h2txyewy\\LocalState\\AppIconCache\\100\\kr_ac_hallym_circuit-studio';
  for (const e of CHECKS) assert.equal(counts(ch('files', 'added', ICON), e), false, e);
  const V1_ICON = 'LOCALAPPDATA\\Packages\\Microsoft.Windows.Search_cw5n1h2txyewy\\LocalState\\AppIconCache\\100\\C__Users_u_AppData_Local_HallymCircuitStudio_HallymCircuitStudio_exe';
  assert.equal(counts(ch('files', 'added', V1_ICON), 'uninstalled'), false);
  assert.equal(counts(ch('files', 'added', V1_ICON), 'none'), true);
  const SHC = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\UFH\\SHC :: 88';
  assert.equal(counts(ch('registry', 'added', SHC, 'REG_MULTI_SZ C:\\...\\Hallym Circuit Studio\\HallymCircuitStudio.lnk'), 'uninstalled'), false);
  assert.equal(counts(ch('registry', 'added', SHC, 'REG_MULTI_SZ x'), 'none'), true);
  // Nowhere else: a jump list, a Search icon or a Start menu cache entry named after it counts.
  assert.equal(counts(ch('files', 'added', 'LOCALAPPDATA\\Packages\\Microsoft.Windows.Search_cw5n1h2txyewy\\LocalState\\AppIconCache\\100\\hallym_other'), 'install'), true);
  assert.equal(counts(ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\RunNotification :: HallymCircuitStudio', 'REG_DWORD 0x0'), 'install'), true);
  assert.deepEqual(judge(ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\UsrClass.dat.LOG2')), { kind: 'allowed', why: 'the files of the HKCU\\Software\\Classes hive (the registry itself is compared key by key)' });
});

// ---- Explorer's caches and counters: the audit trail's word (D-164) ----------------------------------------

const EXPLORER_EXE = 'C:\\Windows\\explorer.exe';
const SVCHOST = 'C:\\Windows\\System32\\svchost.exe';
const WPN = `${SVCHOST} [WpnUserService_94bc1]`;
const OURS_EXE = 'C:\\Users\\runneradmin\\AppData\\Local\\Programs\\Hallym Circuit Studio\\HallymCircuitStudio.exe';
const COUNT = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\UserAssist\\{CEBFF5CD-ACE2-4F4F-9178-9926F41749EA}\\Count';
const written = (by: Record<string, string[]>): Writers => new Map(Object.entries(by).map(([k, v]) => [k, new Set(v)]));
// Exactly the places seen written in CI (runs 36420748421, 36499792661, 36509365847, 36511780078).
const EXPLORERS: Change[] = [
  ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\Explorer\\iconcache_idx.db'),            // run 36420748421, the MSI-to-setup install
  ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Windows\\Caches\\{3DA71D5A-20CC-432F-A115-DFE92379E91F}.3.ver0x0000000000000007.db'),
  ch('files', 'removed', 'LOCALAPPDATA\\Microsoft\\Windows\\Caches\\{3DA71D5A-20CC-432F-A115-DFE92379E91F}.3.ver0x0000000000000006.db'),  // run 36499792661, a first run
  ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\Caches\\cversions.3.db'),
  ch('registry', 'changed', `${COUNT} :: HRZR_PGYFRFFVBA`, 'REG_BINARY 0000'),                         // run 36420748421, setup-e2e's recovery run
];

test('Explorer\'s caches and counters (D-164): through only when the audit trail has them written in the period, by Explorer or a named service only', () => {
  const by = (image: string[]) => written(Object.fromEntries(EXPLORERS.map((c) => [noiseKey(c), image])));
  for (const c of EXPLORERS) {
    // (an install's and an uninstall's own rule already takes HKCU\\Software\\Microsoft as Windows' stores: D-148 12)
    for (const e of c.where === 'registry' ? ['none'] as const : CHECKS) {
      const at = `${c.what} ${c.path} (${e})`;
      assert.equal(counts(c, e), true, `${at}: no audit trail`);
      assert.equal(counts(c, e, undefined, written({})), true, `${at}: not written, as the trail says`);
      assert.equal(counts(c, e, undefined, by([])), true, `${at}: written by no one known`);
      assert.equal(counts(c, e, undefined, by([EXPLORER_EXE])), false, `${at}: Explorer's`);
      assert.equal(counts(c, e, undefined, by(['c:\\windows\\EXPLORER.EXE'])), false, `${at}: Explorer's, in any case`);
      assert.equal(counts(c, e, undefined, by([EXPLORER_EXE, WPN])), false, `${at}: Explorer's and a named service's`);
      assert.equal(counts(c, e, undefined, by([`${SVCHOST} [CDPUserSvc_1a2b,WpnUserService_1a2b]`])), false, `${at}: two named services`);
      assert.equal(counts(c, e, undefined, by([EXPLORER_EXE, OURS_EXE])), true, `${at}: the program wrote it too`);
      for (const other of [OURS_EXE, 'C:\\Windows\\System32\\msiexec.exe', 'C:\\Users\\u\\AppData\\Local\\Temp\\~nsu1.tmp\\Un_A.exe', 'C:\\x\\Windows\\explorer.exe',
        SVCHOST, `${SVCHOST} []`, `${SVCHOST} [N/A]`, `${SVCHOST} [ended]`, `${SVCHOST} [pid 12 not named]`, `${SVCHOST} #12@2026-09-28T12:00:00Z`,
        'C:\\Windows\\Temp\\svchost.exe [WpnUserService_1]', `${SVCHOST} [x] ${OURS_EXE}`])
        assert.equal(counts(c, e, undefined, by([other])), true, `${at}: ${other}`);
    }
  }
  // Only what was seen: not the rest of Explorer's caches, other GUIDs' versioned caches, other UserAssist values or GUIDs,
  // even written by Explorer.
  for (const c of [
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\Explorer\\iconcache_32.db'),
    ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Windows\\Explorer\\thumbcache_256.db'),
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\Explorer\\iconcache_idx.db.bak'),
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\Explorer\\ExplorerStartupLog.etl'),
    ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Windows\\Caches\\{AFBF9F1A-8EE8-4C77-AF34-C647E37CA0D9}.1.ver0x0000000000000001.db'),
    ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Windows\\Caches\\hallym.db'),
    ch('registry', 'changed', `${COUNT} :: HRZR_PGYFRFFVBAK`, 'REG_BINARY 00'),
    ch('registry', 'changed', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\UserAssist\\{F4E57C4B-2036-45F0-A9AB-443BCFE33D9F}\\Count :: HRZR_PGYFRFFVBA', 'REG_BINARY 00'),
    ch('registry', 'changed', `${COUNT} :: {6Q809377-6NS0-444O-8957-N3773S02200R}\\Unyylz\\UnyylzPvephvgFghqvb.rkr`, 'REG_BINARY 00'),
  ]) for (const e of c.where === 'registry' ? ['none'] as const : CHECKS) assert.equal(counts(c, e, undefined, written({ [noiseKey(c)]: [EXPLORER_EXE] })), true, `${c.what} ${c.path} (${e})`);
  // Explorer's word for one place is not for another; a change naming this program counts even at such a place.
  const one = written({ [noiseKey(EXPLORERS[0])]: [EXPLORER_EXE] });
  assert.equal(counts(EXPLORERS[0], 'none', undefined, one), false);
  for (const c of EXPLORERS.slice(1)) assert.equal(counts(c, 'none', undefined, one), true, c.path);
  const naming = { ...EXPLORERS[4], after: 'REG_SZ C:\\Program Files\\Hallym Circuit Studio' };
  assert.equal(counts(naming, 'none', undefined, written({ [noiseKey(naming)]: [EXPLORER_EXE] })), true);
  // The report says who wrote it.
  const { lines, bad } = report('t', [EXPLORERS[0]], 'none', [], one);
  assert.deepEqual(bad, []);
  assert.match(lines[0], /audit trail: 1 place\(s\) written/);
  assert.match(lines[1], /^info .*iconcache_idx\.db.*\[written by C:\\Windows\\explorer\.exe\]/);
  const r = report('t', [EXPLORERS[0]], 'none', [], written({ [noiseKey(EXPLORERS[0])]: [OURS_EXE] }));
  assert.match(r.lines[1], /^FAIL .*\[written by .*HallymCircuitStudio\.exe\]/);
});

test('Explorer\'s session record with its use counter for this program (D-168): through in a run check only when that counter changed in the same check', () => {
  const session = ch('registry', 'changed', `${COUNT} :: HRZR_PGYFRFFVBA`, 'REG_BINARY 000000001000000019000000');
  const counter = ch('registry', 'added', `${COUNT} :: xe.np.unyylz.pvephvg-fghqvb`, 'REG_BINARY 0000000000000000');
  // as seen in setup-e2e's first and recovery runs: both together, the trail having only the counter
  assert.deepEqual(unexpected([counter, session], 'none'), []);
  assert.deepEqual(report('t', [counter, session], 'none').bad, []);
  // alone (no use of this program counted), or beside another value, it counts as before
  assert.deepEqual(unexpected([session], 'none'), [session]);
  const other = ch('registry', 'added', `${COUNT} :: HRZR_PGYPHNPbhag:pgbe`, 'REG_BINARY 00');
  assert.deepEqual(unexpected([other, session], 'none'), [other, session]);
  // only a change, only binary data, only that GUID's value
  assert.equal(counts({ ...session, what: 'added' }, 'none', undefined, undefined, [counter]), true);
  assert.equal(counts({ ...session, after: 'REG_SZ x' }, 'none', undefined, undefined, [counter]), true);
  assert.equal(counts({ ...session, path: session.path.replace('CEBFF5CD', 'F4E57C4B') }, 'none', undefined, undefined, [counter]), true);
  // naming this program in its data: never Windows' own
  assert.equal(counts({ ...session, after: 'REG_BINARY 00 Unyylz' }, 'none', undefined, undefined, [counter]), true);
});

test('the audit trail read: file writes and deletes (4663), registry values set (4657), with their programs, as the snapshots\' places', () => {
  const env = { LOCALAPPDATA: 'C:\\Users\\runneradmin\\AppData\\Local', APPDATA: 'C:\\Users\\runneradmin\\AppData\\Roaming' };
  const ev = (id: number, data: Record<string, string>, at = '2026-09-28T12:29:05.1000000Z') =>
    `<Event xmlns='http://schemas.microsoft.com/win/2004/08/events/event'><System><Provider Name='Microsoft-Windows-Security-Auditing'/><EventID>${id}</EventID><TimeCreated SystemTime='${at}'/></System><EventData>${
      Object.entries(data).map(([k, v]) => `<Data Name='${k}'>${v}</Data>`).join('')}</EventData></Event>`;
  const L = env.LOCALAPPDATA;
  const CACHE = `${L}\\Microsoft\\Windows\\Caches\\{3DA71D5A-20CC-432F-A115-DFE92379E91F}.3.ver0x0000000000000006.db`;
  const xml = [
    ev(4663, { ObjectType: 'File', ObjectName: `${L}\\Microsoft\\Windows\\Explorer\\iconcache_idx.db`, AccessMask: '0x2', ProcessName: EXPLORER_EXE }),
    ev(4663, { ObjectType: 'File', ObjectName: CACHE, AccessMask: '0x10000', ProcessName: EXPLORER_EXE }),
    ev(4663, { ObjectType: 'File', ObjectName: CACHE, AccessMask: '0x2', ProcessName: SVCHOST, ProcessId: '0x4d8' }),
    ev(4663, { ObjectType: 'File', ObjectName: `${L}\\Microsoft\\Windows\\Explorer\\iconcache_32.db`, AccessMask: '0x1', ProcessName: OURS_EXE }),          // read: not a write
    ev(4663, { ObjectType: 'File', ObjectName: `${L}\\Microsoft\\Windows\\Explorer\\iconcache_idx.db`, AccessMask: '0x4', ProcessName: 'C:\\a&amp;b\\x.exe' }),
    ev(4663, { ObjectType: 'Key', ObjectName: '\\REGISTRY\\USER\\S-1-5-21-1-2-3-500\\Software\\x', AccessMask: '0x2', ProcessName: EXPLORER_EXE }),   // a key: not a file
    ev(4663, { ObjectType: 'File', ObjectName: 'C:\\Windows\\Temp\\x', AccessMask: '0x2', ProcessName: EXPLORER_EXE }),                                     // not a snapshot's place
    ev(4657, { ObjectName: '\\REGISTRY\\USER\\S-1-5-21-1-2-3-500\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\UserAssist\\{CEBFF5CD-ACE2-4F4F-9178-9926F41749EA}\\Count', ObjectValueName: 'HRZR_PGYFRFFVBA', ProcessName: EXPLORER_EXE }),
    ev(4657, { ObjectName: '\\REGISTRY\\USER\\S-1-5-21-1-2-3-500_Classes\\x', ObjectValueName: 'y', ProcessName: EXPLORER_EXE }),                        // Classes: not audited
    ev(4624, { ObjectName: `${L}\\y`, ProcessName: EXPLORER_EXE }),
  ].join('\r\n');
  const w = parseAudit(xml, env);
  assert.deepEqual([...w.entries()].map(([k, v]) => [k, [...v]]), [
    [noiseKey(EXPLORERS[0]), [EXPLORER_EXE, 'C:\\a&b\\x.exe']],
    [noiseKey(EXPLORERS[2]), [EXPLORER_EXE, `${SVCHOST} #1240@2026-09-28T12:29:05.1000000Z`]],
    [noiseKey(EXPLORERS[4]), [EXPLORER_EXE]],
  ]);
  // A service host named by its services -- only when it is running, started before the write, with real service names.
  const services = parseTasklist('"svchost.exe","1240","CDPUserSvc_5e1f2,WpnUserService_5e1f2"\r\n"explorer.exe","3012","N/A"\r\n"svchost.exe","77","N/A"\r\n');
  assert.deepEqual([...services], [[1240, 'CDPUserSvc_5e1f2,WpnUserService_5e1f2'], [3012, 'N/A'], [77, 'N/A']]);
  const created = parseWmicCreated('\r\r\n\r\r\nCreationDate=20260928212500.500000+540\r\r\nProcessId=1240\r\r\n\r\r\n\r\r\nCreationDate=20260928120000.000000+000\r\r\nProcessId=77\r\r\n\r\r\n');
  assert.deepEqual([...created], [[1240, '2026-09-28T12:25:00.500Z'], [77, '2026-09-28T12:00:00.000Z']]);
  assert.equal(wmicTime('20260928212500.500000-060'), '2026-09-28T22:25:00.500Z');
  assert.equal(wmicTime('x'), undefined);
  const svcOf = (s: Map<number, string>, c: Map<number, string>) => [...nameServices(w, s, c).get(noiseKey(EXPLORERS[2]))!];
  assert.deepEqual(svcOf(services, created), [EXPLORER_EXE, `${SVCHOST} [CDPUserSvc_5e1f2,WpnUserService_5e1f2]`]);
  assert.deepEqual(svcOf(new Map(), created), [EXPLORER_EXE, `${SVCHOST} [pid 1240 not named]`]);                                   // ended
  assert.deepEqual(svcOf(new Map([[1240, 'N/A']]), created), [EXPLORER_EXE, `${SVCHOST} [pid 1240 not named]`]);
  assert.deepEqual(svcOf(new Map([[1240, '']]), created), [EXPLORER_EXE, `${SVCHOST} [pid 1240 not named]`]);
  assert.deepEqual(svcOf(services, new Map()), [EXPLORER_EXE, `${SVCHOST} [pid 1240 not named]`]);                                   // start unknown
  assert.deepEqual(svcOf(services, new Map([[1240, '2026-09-28T12:29:06.000Z']])), [EXPLORER_EXE, `${SVCHOST} [pid 1240 not named]`]);   // the id used again
  const named = nameServices(w, services, created);
  // So what it read lets the observed places through, but not the one with a stranger among its writers, nor an unnamed host.
  assert.equal(counts(EXPLORERS[0], 'install', undefined, named), true);
  assert.equal(counts(EXPLORERS[2], 'none', undefined, named), false);
  assert.equal(counts(EXPLORERS[2], 'none', undefined, w), true);                                                                   // not yet named
  assert.equal(counts(EXPLORERS[2], 'none', undefined, nameServices(w, new Map(), created)), true);                                // ended
  assert.equal(counts(EXPLORERS[4], 'none', undefined, named), false);
  assert.equal(auditFrom('2026-09-28T12:30:00.000Z'), '2026-09-28T12:29:00.000Z');
});

test('the audit trail must hold the whole window: its oldest event no newer than the start, the log not full', () => {
  const from = '2026-09-28T12:29:00.000Z';
  assert.doesNotThrow(() => auditCoverage(from, '2026-09-28T11:00:00.0000000Z', 20 << 20, 256 << 20));
  assert.doesNotThrow(() => auditCoverage(from, from, 1, 256 << 20));
  assert.throws(() => auditCoverage(from, '2026-09-28T12:29:00.5000000Z', 20 << 20, 256 << 20), /newer than the window's start/);
  assert.throws(() => auditCoverage(from, undefined, 20 << 20, 256 << 20), /no events/);
  assert.throws(() => auditCoverage(from, '2026-09-28T11:00:00Z', 256 << 20, 256 << 20), /full/);
  assert.throws(() => auditCoverage(from, '2026-09-28T11:00:00Z', 250 << 20, 256 << 20), /full/);
  assert.throws(() => auditCoverage(from, '2026-09-28T11:00:00Z', NaN, 256 << 20), /size unknown/);
  assert.throws(() => auditCoverage(from, '2026-09-28T11:00:00Z', 1, NaN), /size unknown/);
});

test('Explorer\'s session record beside the counter (D-168 11): only Windows\' own writers, the counter added or changed as binary, a run check only', () => {
  const session = ch('registry', 'changed', `${COUNT} :: HRZR_PGYFRFFVBA`, 'REG_BINARY 000000001000000019000000');
  const counter = ch('registry', 'added', `${COUNT} :: xe.np.unyylz.pvephvg-fghqvb`, 'REG_BINARY 0000000000000000');
  const pair = [counter, session];
  const wrote = (who: Record<string, string[]>) => written(Object.fromEntries(Object.entries(who).map(([k, v]) => [k === 's' ? noiseKey(session) : noiseKey(counter), v])));
  // through: no writer recorded, or Explorer / a named service only
  assert.deepEqual(unexpected(pair, 'none'), []);
  assert.deepEqual(unexpected(pair, 'none', undefined, wrote({ c: [EXPLORER_EXE] })), []);
  assert.deepEqual(unexpected(pair, 'none', undefined, wrote({ s: [EXPLORER_EXE], c: [WPN] })), []);
  // any other writer of either value: counts
  for (const other of [OURS_EXE, 'C:\\Users\\u\\AppData\\Local\\Temp\\~nsu1.tmp\\Un_A.exe', 'C:\\Windows\\System32\\msiexec.exe',
    'C:\\Users\\u\\Downloads\\HallymCircuitStudio-2.0.0-win-x64-setup.exe', `${SVCHOST} [pid 12 not named]`, `${SVCHOST} [ended]`]) {
    assert.deepEqual(unexpected(pair, 'none', undefined, wrote({ s: [EXPLORER_EXE, other] })), [session], `session record by ${other}`);
    const bad = unexpected(pair, 'none', undefined, wrote({ c: [other] }));
    assert.ok(bad.includes(session) && bad.includes(counter), `counter by ${other}: ${bad.map((c) => c.path).join(', ')}`);
  }
  // the counter removed, or not binary: no pair
  assert.deepEqual(unexpected([{ ...counter, what: 'removed', before: 'REG_BINARY 00' }, session], 'none').includes(session), true);
  assert.deepEqual(unexpected([{ ...counter, after: 'REG_SZ 1' }, session], 'none').includes(session), true);
  // an install or an uninstall check does not use this rule (its Windows-store rule is its own: D-148 12)
  for (const e of ['install', 'uninstalled'] as const) {
    const r = judge(session, e, undefined, undefined, pair);
    assert.ok(r.kind !== 'allowed' || !/D-168/.test(r.why), `${e}: ${JSON.stringify(r)}`);
  }
});
