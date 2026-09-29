/* The Windows installer (N-23, D-148; D-155): tools/package-config.ts gives
   electron-builder the assisted, per-user NSIS installer the user decided
   on (Hallym MIPS 2.5.0's: the progress, then the finish page with 지금
   실행하기), packaging/installer.nsh gives it its pages, words and colours,
   and finds the v1.0.x MSI by the UpgradeCode jpackage gave it.  Read without electron-builder or a build; the
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

test('assisted, per user, never elevated, no folder to choose; a Start menu shortcut and no desktop shortcut; 지금 실행하기 on the finish page', () => {
  const n = config.nsis!;
  assert.equal(n.oneClick, false);
  assert.equal(n.perMachine, false);
  assert.equal(n.allowElevation, false);
  assert.equal(n.allowToChangeInstallationDirectory, false);
  assert.equal(n.createDesktopShortcut, false);
  assert.equal(n.createStartMenuShortcut, true);
  assert.equal(n.shortcutName, 'Hallym Circuit Studio');
  assert.equal(n.runAfterFinish, true);
  // The finish pages' band: the committed pictures (tests/unit/installer-art.test.ts reads them).
  assert.equal(n.installerSidebar, path.join(root, 'packaging/installerSidebar.bmp'));
  assert.equal(n.uninstallerSidebar, path.join(root, 'packaging/uninstallerSidebar.bmp'));
  for (const f of [n.installerSidebar, n.uninstallerSidebar]) assert.ok(existsSync(f as string));
  assert.equal(n.license, undefined); // no licence page
  assert.equal(n.multiLanguageInstaller, undefined); // one language: no language dialog
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
  assert.deepEqual(config.extraResources, [{ from: path.join('/s', 'engine'), to: 'engine' }, { from: path.join('/s', 'runtime'), to: 'runtime' },
    ...['adder-1bit.circ', 'ripple-carry-4bit.circ', 'counter-4bit.circ', 'demo-datapath.circ', 'console-demo.circ', 'stack-demo.circ'].map((n) => ({ from: path.join(repo, 'tests/circ', n), to: `examples/${n}` }))]);
  for (const r of config.extraResources as { from: string }[]) if (r.from.endsWith('.circ')) assert.ok(existsSync(r.from), r.from);   // Help › Examples (D-158)
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
  // Started in the system folder, not the uninstaller's current one (the install folder, which must go).
  assert.match(m, /kernel32::CreateProcessW\(p 0, w R3, p 0, p 0, i 0, i 0x08000000, p 0, w "\$SYSDIR", p R4, p R5\) i \.R6/);
  assert.doesNotMatch(m, /ExecShell|\bExec\b|ExecWait|nsExec/);
  assert.match(nsh, /!macro customUnInstall\n\s+!insertmacro hcsRemoveUninstallerCopy\n!macroend/);
});

test('the pages (as Hallym MIPS 2.5.0): only this user, the progress, then the finish page with 지금 실행하기 ticked; the uninstaller the same', () => {
  const macro = (name: string) => new RegExp(`!macro ${name}\n([\\s\\S]*?)!macroend`).exec(nsh)?.[1] ?? '';
  // "For all users or only me" answered before it shows: this user (no administrator).
  assert.match(macro('customInstallMode'), /^\s+StrCpy \$isForceCurrentInstall "1"\n$/);
  // The finish page: its title and words, 지금 실행하기 (MUI ticks it), which opens the Start menu shortcut as the user.
  const finish = macro('customFinishPage');
  assert.match(finish, /!define MUI_FINISHPAGE_TITLE "설치가 완료되었습니다"/);
  assert.match(finish, /!define MUI_FINISHPAGE_TEXT "Hallym Circuit Studio 설치를 마쳤습니다\./);
  assert.match(finish, /!define MUI_FINISHPAGE_RUN\n/);
  assert.match(finish, /!define MUI_FINISHPAGE_RUN_TEXT "지금 실행하기"/);
  assert.doesNotMatch(finish, /MUI_FINISHPAGE_RUN_NOTCHECKED/);
  assert.match(finish, /\$\{StdUtils\.ExecShellAsUser\} \$0 "\$launchLink" "open" ""/);
  assert.match(finish, /!insertmacro MUI_PAGE_FINISH\n$/);
  // The progress page's head, and the uninstaller's pages: the progress, then its finish page.
  assert.match(macro('customPageAfterChangeDir'), /MUI_PAGE_HEADER_TEXT "설치하는 중"/);
  assert.match(macro('customUnWelcomePage'), /MUI_PAGE_HEADER_TEXT "제거하는 중"/);
  assert.doesNotMatch(macro('customUnWelcomePage'), /MUI_UNPAGE_WELCOME/);
  assert.match(macro('customUninstallPage'), /MUI_FINISHPAGE_TITLE "제거가 끝났습니다"/);
  // electron-builder's assisted template: no welcome or licence page of its own, the install-mode page, then these.
  const assisted = readFileSync(path.join(root, 'node_modules/app-builder-lib/templates/nsis/assistedInstaller.nsh'), 'utf8');
  const pages = assisted.slice(0, assisted.indexOf('!else'));
  assert.ok(pages.indexOf('customPageAfterChangeDir') < pages.indexOf('MUI_PAGE_INSTFILES'));
  assert.ok(pages.indexOf('MUI_PAGE_INSTFILES') < pages.indexOf('customFinishPage'));
  assert.match(pages, /!ifmacrodef customWelcomePage/); // no welcome page unless one is given (none is)
  assert.doesNotMatch(nsh, /!macro (customWelcomePage|licensePage)\b/);
});

test('the progress bar in the app\'s blue (#0055A5, shared.css --blue) on a pale track, set on both progress pages', () => {
  const css = readFileSync(path.join(root, 'src/renderer/shared/shared.css'), 'utf8');
  const blue = /--blue: #([0-9a-f]{6})/i.exec(css)![1].toLowerCase();
  assert.equal(blue, '0055a5');
  // COLORREF is 0x00BBGGRR.
  const colorref = `0x${blue.slice(4, 6)}${blue.slice(2, 4)}${blue.slice(0, 2)}`.toUpperCase().replace('0X', '0x');
  assert.match(nsh, new RegExp(`^!define HCS_BAR_COLOUR ${colorref}$`, 'm'));
  const bar = /!macro HcsProgressBar\n([\s\S]*?)!macroend/.exec(nsh)?.[1] ?? '';
  assert.match(bar, /GetDlgItem \$0 \$0 1004\n/);                      // the instfiles page's progress bar
  assert.match(bar, /uxtheme::SetWindowTheme\(p r0, w "", w ""\)/);     // no visual style: it takes colours
  assert.match(bar, /SendMessage \$0 0x409 0 \$\{HCS_BAR_COLOUR\}/);      // PBM_SETBARCOLOR
  assert.match(bar, /SendMessage \$0 0x2001 0 \$\{HCS_BAR_TRACK\}/);      // PBM_SETBKCOLOR
  assert.match(nsh, /Function HcsProgressColour\n\s+!insertmacro HcsProgressBar/);
  assert.match(nsh, /Function un\.HcsProgressColour\n\s+!insertmacro HcsProgressBar/);
  assert.match(nsh, /MUI_PAGE_CUSTOMFUNCTION_SHOW HcsProgressColour/);
  assert.match(nsh, /MUI_PAGE_CUSTOMFUNCTION_SHOW un\.HcsProgressColour/);
});

test('the installer\'s words: Korean, no particle right after a name, no "하면 됩니다", no "한림" (N-20)', () => {
  const words = [...nsh.matchAll(/LangString (\w+) 1042 "([^"]*)"/g)].map((m) => [m[1], m[2]] as const);
  const box = [...nsh.matchAll(/MessageBox [^"]*"([^"]*)"/g)].map((m) => ['notice', m[1]] as const);
  // The pages' words (MUI defines, D-155): the same rules; the program's name there is followed by a noun, never a particle.
  const pages = [...nsh.matchAll(/!define (MUI_\w+) "([^"]*)"/g)].filter((m) => !/FUNCTION/.test(m[1])).map((m) => [m[1], m[2]] as const);
  assert.deepEqual(pages.map(([id]) => id).sort(), ['MUI_FINISHPAGE_RUN_TEXT', 'MUI_FINISHPAGE_TEXT', 'MUI_FINISHPAGE_TEXT', 'MUI_FINISHPAGE_TITLE',
    'MUI_FINISHPAGE_TITLE', 'MUI_PAGE_HEADER_SUBTEXT', 'MUI_PAGE_HEADER_SUBTEXT', 'MUI_PAGE_HEADER_TEXT', 'MUI_PAGE_HEADER_TEXT']);
  for (const [id, text] of pages) {
    assert.match(text, /[가-힣]/, `${id}: Korean`);
    assert.doesNotMatch(text, /(Studio|[A-Za-z0-9)])(을|를|이|가|은|는|의|에|로|으로|와|과|\(을\)|\(이\))(?![A-Za-z])/, `${id}: a particle after a name: ${text}`);
    assert.doesNotMatch(text, /하면 됩니다|한림/, `${id}: ${text}`);
  }
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

test('the guided install and uninstall (the students\' default) are compared as the /S ones are: a control period each, the four writes, then nothing (D-155)', () => {
  const ui = readFileSync(path.join(root, 'tools/windows/check-installer-ui.ps1'), 'utf8');
  const at = (s: string) => { const i = ui.indexOf(s); assert.ok(i >= 0, s); return i; };
  // The order: control before the install, the install, its comparison; control before the uninstall, the uninstall, its comparison.
  const order = [
    "Control 'ui-install' 45 'ui-before'",
    '$p = Launch (Resolve-Path $Setup).Path',
    "ProgramGone 'the program closed'",
    "StateDiff 'ui-before' 'ui-installed' 'install' 'ui-install'",
    "Control 'ui-uninstall' 20 'ui-pre-uninstall'",
    '$u = Launch $exe $uargs',
    "StateDiff 'ui-before' 'ui-uninstalled' 'uninstalled' 'ui-uninstall'",
  ].map(at);
  assert.deepEqual([...order].sort((a, b) => a - b), order);
  // The same comparison as check-install.ps1 (state.ts diff with that phase's noise only), programs started without the shell.
  assert.match(ui, /node tools\/windows\/state\.ts diff .* --expect \$expect --noise \(Join-Path \$ReportFull "noise-\$control\.json"\)/);
  assert.match(ui, /\$si\.UseShellExecute = \$false/);
  assert.doesNotMatch(ui, /Start-Process/);
  // Add-Type (which writes build files in %TEMP%) only before the first control period.
  const lastAddType = ui.lastIndexOf('Add-Type');
  assert.ok(lastAddType < order[0], 'Add-Type after a control period');
});
