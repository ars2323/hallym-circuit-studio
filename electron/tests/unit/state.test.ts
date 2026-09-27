/* tools/windows/state.ts (N-23, D-148): the Windows checks' rule for what
   the program and its installer may leave.  The snapshot itself is taken
   on Windows (CI setup-e2e); here its parts that are not Windows: reading
   reg.exe's answer, the difference, what counts. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { APP_GUID } from '../../tools/package-config.ts';
import { allowedByInstall, counts, diffStates, leftAfterUninstall, parseRegQuery, unexpected, type Change, type State } from '../../tools/windows/state.ts';

const state = (p: Partial<State>): State => ({ files: {}, temp: {}, registry: {}, taken: '', ...p });
const UNINSTALL = `HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\${APP_GUID}`;
const RECORD = `HKCU\\Software\\${APP_GUID}`;
const SHORTCUT = 'APPDATA\\Microsoft\\Windows\\Start Menu\\Programs\\Hallym Circuit Studio.lnk';

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

test('a run of the program may change nothing: Java preferences, a settings folder, a run folder left in %TEMP% all count', () => {
  const before = state({ temp: { 'TEMP\\other': 'dir' } });
  const after = state({
    files: { 'APPDATA\\Hallym Circuit Studio': 'dir', 'LOCALAPPDATA\\D3DSCache': 'dir' },
    registry: { 'HKCU\\Software\\JavaSoft\\Prefs\\kr\\ac': 'key' },
    temp: { 'TEMP\\other': 'dir', 'TEMP\\HallymCircuitStudio': 'dir', 'TEMP\\HallymCircuitStudio\\run-1-2': 'dir', 'TEMP\\hsperfdata_runner': 'dir', 'TEMP\\playwright-x': 'dir' },
  });
  const bad = unexpected(diffStates(before, after), 'none').map((c) => c.path);
  assert.deepEqual(bad, ['APPDATA\\Hallym Circuit Studio', 'LOCALAPPDATA\\D3DSCache', 'TEMP\\HallymCircuitStudio', 'TEMP\\HallymCircuitStudio\\run-1-2',
    'TEMP\\hsperfdata_runner', 'HKCU\\Software\\JavaSoft\\Prefs\\kr\\ac']);
  // %TEMP%'s other entries are the test tools' (and Windows'): reported, not counted.
  assert.equal(counts({ where: 'temp', what: 'added', path: 'TEMP\\playwright-x' }), false);
});

test('an install may add exactly its shortcut, its uninstall entry and its install record', () => {
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
  // Windows' own containers are never ours to count.
  assert.deepEqual(changes.filter((c) => !counts(c)).map((c) => c.path), ['LOCALAPPDATA\\Programs', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall']);
  // But not as a run's change, and nothing else: a desktop shortcut, a .circ association, another key, a changed file.
  assert.equal(unexpected(changes, 'none').length, 5);
  const more: Change[] = [
    { where: 'files', what: 'added', path: 'DESKTOP\\Hallym Circuit Studio.lnk', after: '1 1' },
    { where: 'files', what: 'added', path: 'APPDATA\\Microsoft\\Windows\\Start Menu\\Programs\\Hallym Circuit Studio\\Hallym Circuit Studio.lnk', after: '1 1' },
    { where: 'registry', what: 'added', path: 'HKCU\\Software\\Classes\\.circ', after: 'key' },
    { where: 'registry', what: 'added', path: 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{6206F18C-D7FA-366B-98DA-E7980F6083D6}', after: 'key' },
    { where: 'registry', what: 'added', path: 'HKCU\\Software\\kr.ac.hallym.circuit-studio', after: 'key' },
    { where: 'registry', what: 'changed', path: `${UNINSTALL} :: DisplayName`, before: 'a', after: 'b' },
    { where: 'files', what: 'added', path: 'LOCALAPPDATA\\hallym-circuit-studio-updater\\installer.exe', after: '1 1' },
  ];
  for (const c of more) assert.equal(allowedByInstall(c), false, c.path);
});

test('after an uninstall nothing is left but Windows\' own empty containers (an MSI\'s leave keys without values)', () => {
  const before = state({});
  const after = state({
    files: { 'LOCALAPPDATA\\Programs': 'dir' },
    registry: { 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall': 'key', 'HKCU\\Software\\Microsoft\\Installer': 'key', 'HKCU\\Software\\Microsoft\\Installer\\Products': 'key' },
  });
  assert.deepEqual(leftAfterUninstall(diffStates(before, after)), []);
  const product = state({ registry: { 'HKCU\\Software\\Microsoft\\Installer\\Products\\7DCAC541': 'key', 'HKCU\\Software\\Microsoft\\Installer\\Products\\7DCAC541 :: ProductName': 'REG_SZ HallymCircuitStudio' } });
  assert.deepEqual(leftAfterUninstall(diffStates(before, product)).map((c) => c.path), ['HKCU\\Software\\Microsoft\\Installer\\Products\\7DCAC541 :: ProductName']);
  // A file left in the programs folder, or the folder when it is not a folder, counts.
  assert.equal(leftAfterUninstall(diffStates(before, state({ files: { 'LOCALAPPDATA\\Programs\\Hallym Circuit Studio\\x.dll': '1 1' } }))).length, 1);
});
