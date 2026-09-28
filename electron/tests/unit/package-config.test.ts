/* The Windows installer (N-23, D-148): tools/package-config.ts gives
   electron-builder the one-click, per-user NSIS installer the user decided
   on, and packaging/installer.nsh finds the v1.0.x MSI by the UpgradeCode
   jpackage gave it.  Read without electron-builder or a build; the
   installer itself is checked on Windows (CI setup-exe, setup-e2e,
   setup-upgrade). */

import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import { UUID } from 'builder-util-runtime';

import { APP_GUID, APP_ID, INSTALL_FOLDER, packageConfig, PRODUCT_NAME, PUBLISHER, SETUP_ARTIFACT, setupExeName, uninstallDisplayName } from '../../tools/package-config.ts';

const root = path.join(import.meta.dirname, '..', '..');
const repo = path.join(root, '..');
const paths = { root, repo, stage: '/s/app', engineOut: '/s', output: '/s/dist', electronVersion: '44.4.5' };
const config = packageConfig(paths);
const nsh = readFileSync(path.join(root, 'packaging/installer.nsh'), 'utf8');

// java.util.UUID.nameUUIDFromBytes: MD5, version 3, IETF variant (what jpackage uses).
function nameUuid(s: string): string {
  const h = createHash('md5').update(s, 'utf8').digest();
  h[6] = (h[6] & 0x0f) | 0x30;
  h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex').toUpperCase();
  return `{${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}}`;
}

test('one file: the NSIS installer for x64, named HallymCircuitStudio-<version>-win-x64-setup.exe', () => {
  assert.deepEqual(config.win!.target, [{ target: 'nsis', arch: ['x64'] }]);
  assert.equal(config.nsis!.artifactName, SETUP_ARTIFACT);
  const expand = (v: string) => SETUP_ARTIFACT.replace('${version}', v).replace('${ext}', 'exe');
  for (const v of ['2.0.0', '2.0.0-alpha.1']) assert.equal(setupExeName(v), expand(v));
  assert.equal(setupExeName('2.0.0-alpha.1'), 'HallymCircuitStudio-2.0.0-alpha.1-win-x64-setup.exe');
  // No block map for differential updates beside it, no elevate.exe inside it.
  assert.equal(config.nsis!.differentialPackage, false);
  assert.equal(config.nsis!.packElevateHelper, false);
});

test('one click, per user, never elevated; a Start menu shortcut and no desktop shortcut; nothing run after', () => {
  const n = config.nsis!;
  assert.equal(n.oneClick, true);
  assert.equal(n.perMachine, false);
  assert.equal(n.allowElevation, false);
  assert.equal(n.createDesktopShortcut, false);
  assert.equal(n.createStartMenuShortcut, true);
  assert.equal(n.shortcutName, 'Hallym Circuit Studio');
  assert.equal(n.runAfterFinish, false);
  assert.equal(n.deleteAppDataOnUninstall, false);
  assert.equal(n.uninstallDisplayName, 'Hallym Circuit Studio ${version}');
  assert.equal(uninstallDisplayName('2.0.0'), 'Hallym Circuit Studio 2.0.0');
  assert.deepEqual([n.installerLanguages, n.language], [['ko_KR'], '1042']);
  assert.equal(n.include, path.join(root, 'packaging/installer.nsh'));
  assert.ok(existsSync(n.include as string));
  assert.deepEqual([config.appId, config.productName, config.executableName], [APP_ID, PRODUCT_NAME, 'HallymCircuitStudio']);
});

test('no auto-update, no file association, no protocol', () => {
  assert.equal(config.publish, null);
  assert.equal(config.fileAssociations, undefined);
  assert.equal(config.protocols, undefined);
  assert.equal((config.win as Record<string, unknown>).fileAssociations, undefined);
  // The installer adds no association of its own either, and writes no registry value itself.
  assert.doesNotMatch(nsh, /APP_ASSOCIATE|registerFileAssociations|WriteReg|\.circ"/);
  assert.doesNotMatch(nsh, /CreateShortCut/);
  // The copy electron-builder keeps for electron-updater is removed.
  assert.match(nsh, /Delete "\$LOCALAPPDATA\\\$\{APP_INSTALLER_STORE_FILE\}"/);
  assert.match(nsh, /RMDir "\$LOCALAPPDATA\\hallym-circuit-studio-updater"/);
});

test('the engine, its runtime and the notices go in; the icon is the repository\'s', () => {
  assert.deepEqual(config.extraResources, [{ from: path.join('/s', 'engine'), to: 'engine' }, { from: path.join('/s', 'runtime'), to: 'runtime' }]);
  assert.deepEqual(config.extraFiles, [{ from: path.join(repo, 'LICENSE'), to: 'LICENSE.txt' }, { from: path.join(repo, 'NOTICE'), to: 'NOTICE.txt' }]);
  assert.equal((config.win as { icon: string }).icon, path.join(repo, 'assets/hallym/logo/app.ico'));
  assert.ok(existsSync(path.join(repo, 'assets/hallym/logo/app.ico')));
  assert.deepEqual(config.directories, { app: '/s/app', output: '/s/dist', buildResources: path.join(root, 'packaging') });
});

test('the install folder is %LOCALAPPDATA%\\Programs\\Hallym Circuit Studio (installer.nsh names it)', () => {
  assert.equal(INSTALL_FOLDER, 'Hallym Circuit Studio');
  assert.match(nsh, /^!undef APP_FILENAME\n!define APP_FILENAME "Hallym Circuit Studio"$/m);
});

test('APP_GUID is electron-builder\'s key for the program: UUID v5 of the app id in its namespace', () => {
  assert.equal(UUID.v5(APP_ID, UUID.parse('50e065bc-3134-11e6-9bab-38c9862bdaf3')), APP_GUID);
});

test('the v1.0.x MSI: the UpgradeCode jpackage derived from its vendor and name, the same the published 1.0.2 MSI carries', () => {
  // tools/package-windows.ps1 at v1.0.0-v1.0.2: --name HallymCircuitStudio --vendor "AIAC Lab, Hallym University".
  assert.equal(PUBLISHER, 'AIAC Lab, Hallym University');
  const upgrade = nameUuid(`UpgradeCode/${PUBLISHER}/HallymCircuitStudio`);
  assert.equal(upgrade, '{6206F18C-D7FA-366B-98DA-E7980F6083D6}');
  // The same derivation gives the ProductCode read from the published 1.0.2 MSI's Property table
  // (hallym-circuit-studio-1.0.2-windows.msi, v1.0.2 release): so vendor and name are right.
  assert.equal(nameUuid(`ProductCode/${PUBLISHER}/HallymCircuitStudio/1.0.2`), '{145CACD7-ADE5-3DF4-8496-7C4ACF95DF62}');
  assert.match(nsh, new RegExp(`^!define HCS_V1_MSI_UPGRADE_CODE "${upgrade.replace(/[{}]/g, '\\$&')}"$`, 'm'));
  assert.match(nsh, /^!define HCS_V1_MSI_NAME "HallymCircuitStudio"$/m);
});

test('the v1.0.x MSI is found by its UpgradeCode and removed silently, after the new program is in place', () => {
  assert.match(nsh, /System::Call 'msi::MsiEnumRelatedProductsW\(w "\$\{HCS_V1_MSI_UPGRADE_CODE\}", i 0, i R0, w \.R1\) i \.R2'/);
  assert.match(nsh, /ExecWait '"\$SYSDIR\\msiexec\.exe" \/x \$R1 \/qn \/norestart' \$R3/);
  // Removed, already gone, or removed with a restart to finish: all fine; the rest is a notice (silent under /S).
  for (const code of ['0', '1605', '3010', '1641']) assert.match(nsh, new RegExp(`\\$R3 == ${code}\\b`));
  assert.match(nsh, /MessageBox MB_OK\|MB_ICONINFORMATION "[^"]+" \/SD IDOK/);
  // Bounded: a product that will not go does not keep the installer looping.
  assert.match(nsh, /\$\{LoopUntil\} \$R5 >= 16/);
  // In customInstall: after installApplicationFiles (installSection.nsh), so a failure leaves the new one installed.
  const custom = /!macro customInstall\n([\s\S]*?)!macroend/.exec(nsh)?.[1] ?? '';
  assert.match(custom, /!insertmacro hcsRemoveV1Msi/);
  const section = readFileSync(path.join(root, 'node_modules/app-builder-lib/templates/nsis/installSection.nsh'), 'utf8');
  assert.ok(section.indexOf('!insertmacro installApplicationFiles') < section.indexOf('!insertmacro customInstall'));
});

test('the uninstaller\'s own copy in %TEMP% is removed once it has ended -- only that folder, never the install folder, no shell', () => {
  const m = /!macro hcsRemoveUninstallerCopy\n([\s\S]*?)!macroend/.exec(nsh)?.[1] ?? '';
  assert.match(m, /\$\{GetFileName\} "\$EXEDIR" \$R0/);
  // Only a copy NSIS made (%TEMP%\~nsu<X>.tmp); an install over an earlier version runs the uninstaller in place.
  assert.match(m, /\$\{If\} \$R1 == "~nsu"\n\s+\$\{AndIf\} \$R2 == "\.tmp"\n\s+\$\{AndIf\} "\$EXEDIR" != "\$INSTDIR"\n/);
  assert.match(m, /StrCpy \$R1 \$R0 4\n\s+StrCpy \$R2 \$R0 "" -4\n/);
  // Waits for the copy to end (its exe is in use until then), then the folder goes.
  assert.match(m, /for \/l %i in \(1,1,120\) do @if exist "\$EXEDIR\\" \(rd \/s \/q "\$EXEDIR" 2>nul & ping -n 2 127\.0\.0\.1 >nul\)/);
  // CreateProcess with CREATE_NO_WINDOW: no console window, no ShellExecute (no jump list entry).
  assert.match(m, /kernel32::CreateProcessW\(p 0, w R3, p 0, p 0, i 0, i 0x08000000, p 0, p 0, p R4, p R5\) i \.R6/);
  assert.doesNotMatch(m, /ExecShell|\bExec\b|ExecWait|nsExec/);
  assert.match(nsh, /!macro customUnInstall\n\s+!insertmacro hcsRemoveUninstallerCopy\n!macroend/);
});

test('the installer\'s words: Korean, no particle right after a name, no "하면 됩니다", no "한림" (N-20)', () => {
  const words = [...nsh.matchAll(/LangString (\w+) 1042 "([^"]*)"/g)].map((m) => [m[1], m[2]] as const);
  const box = [...nsh.matchAll(/MessageBox [^"]*"([^"]*)"/g)].map((m) => ['notice', m[1]] as const);
  assert.deepEqual(words.map(([id]) => id).sort(),
    ['appCannotBeClosed', 'appClosing', 'appRunning', 'areYouSureToUninstall', 'decompressionFailed', 'installing', 'uninstallFailed']);
  for (const [id, text] of [...words, ...box]) {
    assert.match(text, /[가-힣]/, `${id}: Korean`);
    // A name (the program's, an English word, a define) followed at once by a particle.
    assert.doesNotMatch(text, /(\}|[A-Za-z0-9)])(을|를|이|가|은|는|의|에|로|으로|와|과|\(을\)|\(이\))(?![A-Za-z])/, `${id}: a particle after a name: ${text}`);
    assert.doesNotMatch(text, /하면 됩니다|한림/, `${id}: ${text}`);
  }
  // Every name comes after a colon, at the end of its line.
  for (const [id, text] of [...words, ...box]) {
    for (const name of text.match(/\$\{(PRODUCT_NAME|HCS_V1_MSI_NAME)\}/g) ?? []) {
      assert.match(text, new RegExp(`: \\${name.slice(0, 1)}\\${name.slice(1)}(\\$\\\\r\\$\\\\n|$)`), `${id}: ${name} after a colon`);
    }
  }
  // They replace electron-builder's after its own messages: customHeader, with only warning 6030 (set twice) allowed there.
  assert.match(nsh, /!macro customHeader\n\s+!pragma warning push\n\s+!pragma warning disable 6030\n/);
});
