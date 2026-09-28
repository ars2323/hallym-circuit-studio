/* tools/windows/state.ts (N-23, D-148): the Windows checks' rule for what
   the program and its installer may leave.  The snapshot itself is taken
   on Windows (CI setup-e2e, setup-upgrade); here its parts that are not
   Windows: reading reg.exe's answer, the difference, what counts -- and
   that what Windows and the test tools change is let through only at the
   exact place, kind of change and check it was seen in on the runner. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { APP_GUID } from '../../tools/package-config.ts';
import { allowedByInstall, counts, diffStates, KNOWN, notOurs, parseRegQuery, unexpected, type Change, type Expect, type State } from '../../tools/windows/state.ts';

const state = (p: Partial<State>): State => ({ files: {}, temp: {}, registry: {}, taken: '', ...p });
const UNINSTALL = `HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\${APP_GUID}`;
const RECORD = `HKCU\\Software\\${APP_GUID}`;
const SHORTCUT = 'APPDATA\\Microsoft\\Windows\\Start Menu\\Programs\\Hallym Circuit Studio.lnk';
const SEARCH = 'LOCALAPPDATA\\Packages\\Microsoft.Windows.Search_cw5n1h2txyewy';
const ch = (where: Change['where'], what: Change['what'], path: string, after = what === 'removed' ? undefined : '1 1'): Change =>
  ({ where, what, path, ...(after === undefined ? {} : { after }), ...(what === 'changed' ? { before: '1 0' } : {}) });

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
  // Windows' spelling word lists, and only empty: a written list counts.
  assert.equal(counts(ch('files', 'added', 'APPDATA\\Microsoft\\Spelling\\en-US\\default.dic', '2 1'), 'none'), false);
  assert.equal(counts(ch('files', 'added', 'APPDATA\\Microsoft\\Spelling\\ko-KR\\default.dic', '2 1'), 'none'), false);
  assert.equal(counts(ch('files', 'added', 'APPDATA\\Microsoft\\Spelling\\en-US\\default.dic', '14 1'), 'none'), true);
  assert.equal(counts(ch('files', 'added', 'APPDATA\\Microsoft\\Spelling\\en-US\\other.dic', '2 1'), 'none'), true);
  assert.equal(counts(ch('files', 'added', 'APPDATA\\Microsoft\\Spelling\\en-US', 'dir'), 'none'), false);
  // Playwright's own folder per launch.
  assert.equal(counts(ch('temp', 'added', 'TEMP\\playwright-artifacts-Zpo2Y7'), 'none'), false);
  assert.equal(counts(ch('temp', 'added', 'TEMP\\playwright-artifacts-Zpo2Y7\\trace'), 'none'), false);
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
  // Windows' own empty containers are not the installer's.
  assert.deepEqual(changes.filter((c) => !counts(c, 'install')).map((c) => c.path), ['LOCALAPPDATA\\Programs', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall']);
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
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Installer\\Products\\7DCAC541 :: ProductName', 'REG_SZ HallymCircuitStudio'),
    ch('files', 'added', 'LOCALAPPDATA\\Programs\\Hallym Circuit Studio\\x.dll'),
    ch('registry', 'added', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall :: x', 'REG_SZ y'),
    ch('files', 'added', 'LOCALAPPDATA\\Programs', '1 1'),   // a file, not Windows' folder
  ]) assert.equal(counts(c, 'uninstalled'), true, c.path);
});

test('what Windows did on the runner is let through at its exact place, kind of change and checks only; its neighbours count', () => {
  const seen: [Change, Expect[]][] = [
    [ch('files', 'added', `${SEARCH}\\LocalState\\AppIconCache\\100\\Microsoft_AutoGenerated_{0423BC28-B989-92D0-56C0-F931E344705B}`), ['install', 'uninstalled']],
    [ch('files', 'added', `${SEARCH}\\LocalState\\ConstraintIndex\\Apps_{545dcc93-6300-4c2e-a114-a1980257518b}\\Apps.index`), ['install', 'uninstalled']],
    [ch('files', 'removed', `${SEARCH}\\LocalState\\ConstraintIndex\\Apps_{4c36af59-b719-4e60-b916-9d1bc558edcb}`), ['install', 'uninstalled']],
    [ch('files', 'added', `${SEARCH}\\LocalState\\DeviceSearchCache\\AppCache134350269917555399.txt`), ['install', 'uninstalled']],
    [ch('files', 'changed', `${SEARCH}\\Settings\\settings.dat.LOG2`), ['install', 'uninstalled']],
    [ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Windows\\Caches\\{3DA71D5A-20CC-432F-A115-DFE92379E91F}.3.ver0x0000000000000007.db'), ['install', 'uninstalled']],
    [ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\WebCache\\WebCacheV01.dat'), ['install', 'uninstalled']],
    [ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\Notifications\\wpndatabase.db-wal'), ['install', 'uninstalled']],
    [ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\UsrClass.dat.LOG1'), ['none', 'install', 'uninstalled']],
    [ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\PowerShell\\StartupProfileData-NonInteractive'), ['install', 'uninstalled']],
    [ch('files', 'added', 'APPDATA\\Microsoft\\Windows\\Themes\\CachedFiles\\CachedImage_1920_1080_POS4.jpg'), ['none', 'install', 'uninstalled']],
    [ch('registry', 'added', 'HKCU\\Software\\Policies\\Microsoft\\SystemCertificates\\TrustedPublisher\\CRLs', 'key'), ['install', 'uninstalled']],
    [ch('temp', 'added', 'TEMP\\playwright-transform-cache\\a.js'), ['install', 'uninstalled']],
  ];
  for (const [c, where] of seen) {
    for (const e of ['none', 'install', 'uninstalled'] as const) assert.equal(counts(c, e), !where.includes(e), `${c.what} ${c.path} (${e})`);
  }
  // Their neighbours, another kind of change, or a value where only an empty key was seen: counted.
  for (const c of [
    ch('files', 'added', `${SEARCH}\\LocalState\\Other\\x`),
    ch('files', 'added', 'LOCALAPPDATA\\Packages\\Some.Other.App_1234\\LocalState\\x'),
    ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Windows\\Caches\\cache.db'),
    ch('files', 'added', 'LOCALAPPDATA\\Microsoft\\Windows\\WebCache\\WebCacheV01.dat'),
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\WebCache\\Other.dat'),
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\Notifications\\wpndatabase.db'),
    ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\Explorer\\iconcache_32.db'),
    ch('files', 'added', 'APPDATA\\Microsoft\\Windows\\Themes\\TranscodedWallpaper'),
    ch('registry', 'added', 'HKCU\\Software\\Policies\\Microsoft\\SystemCertificates\\TrustedPublisher\\Certificates\\ABCD', 'key'),
    ch('registry', 'added', 'HKCU\\Software\\Policies\\Microsoft\\SystemCertificates\\TrustedPublisher :: x', 'REG_SZ y'),
  ]) {
    for (const e of ['none', 'install', 'uninstalled'] as const) assert.equal(counts(c, e), true, `${c.what} ${c.path} (${e})`);
  }
  // Every entry says why.
  for (const k of KNOWN) assert.ok(k.why.length > 20, k.path.source);
  assert.equal(notOurs(ch('files', 'changed', 'LOCALAPPDATA\\Microsoft\\Windows\\UsrClass.dat.LOG2')), 'the HKCU\\Software\\Classes hive\'s own log (the registry is compared key by key)');
});
