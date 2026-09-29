/* What the Windows installer is (N-23, D-148; D-155): electron-builder's
   options for tools/package.ts, kept apart from it so that
   tests/unit/package-config.test.ts reads them without electron-builder or
   a build.  Derived from Hallym MIPS v2.5.0 electron/tools/package.ts (the
   config); the installer's own part is packaging/installer.nsh.

   One file, HallymCircuitStudio-<version>-win-x64-setup.exe (electron-builder
   NSIS, assisted, in Korean), like Hallym MIPS 2.5.0's
   HallymMIPS-<version>-win-x64-setup.exe:
     - two pages: the progress, then the finish page (설치가 완료되었습니다,
       지금 실행하기 ticked); the uninstaller the same (the user's decision,
       D-155: it replaces the one-click installer of D-148).  Nothing is
       asked: no folder, no "for all users"; /S installs silently and
       starts nothing (the lab PCs, CI)
     - the finish pages' band in the app's navy with the university's symbol
       (packaging/*Sidebar.bmp, tools/installer-art.py), the progress bar in
       the app's blue (installer.nsh)
     - per user, never elevated: %LOCALAPPDATA%\Programs\Hallym Circuit Studio
       (packaging/installer.nsh names the folder)
     - a Start menu shortcut "Hallym Circuit Studio"; no desktop shortcut
     - the uninstall entry "Hallym Circuit Studio <version>" under HKCU
     - installing over an earlier version (or the same one) replaces it:
       the same folder, the same entry
     - no auto-update (no publish, no update info, no copy of the installer
       kept), no file association (.circ stays with whatever opens it, the
       original Logisim on the lab PCs); the program starts after the install
       only from the finish page (지금 실행하기)
     - the engine (hcs-engine.jar, hcs-mips.jar) and its bundled Java runtime
       in resources/ (tools/stage-engine.ts)
     - an earlier v1.0.x MSI install is removed silently (installer.nsh)
   Nothing else is written: no settings, nothing in %APPDATA% (the lab-PC
   rule; the app itself: src/main/run-folder.ts). */

import type { Configuration } from 'electron-builder';
import path from 'node:path';

import { exampleResources } from '../src/main/examples.ts';
import { extraResources } from './stage-engine.ts';

export const APP_ID = 'kr.ac.hallym.circuit-studio';
export const PRODUCT_NAME = 'Hallym Circuit Studio';
export const EXECUTABLE_NAME = 'HallymCircuitStudio';
export const PUBLISHER = 'AIAC Lab, Hallym University';
// electron-builder's key for the installed program (UUID v5 of APP_ID in its own namespace;
// tests/unit/package-config.test.ts): the uninstall entry
// HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\<guid> and its install record HKCU\Software\<guid>.
export const APP_GUID = 'eb84d729-7626-52ce-aff8-71eda9d27e59';
// electron-builder's pattern and what it gives for a version (tools/release-assets.ts checks releases by it).
export const SETUP_ARTIFACT = 'HallymCircuitStudio-${version}-win-x64-setup.${ext}';
export const setupExeName = (version: string): string => `HallymCircuitStudio-${version}-win-x64-setup.exe`;
// The per-user install folder (packaging/installer.nsh APP_FILENAME) and the uninstall entry's name.
export const INSTALL_FOLDER = 'Hallym Circuit Studio';
export const uninstallDisplayName = (version: string): string => `${PRODUCT_NAME} ${version}`;

export interface ConfigPaths {
  root: string;            // electron/
  repo: string;            // the repository
  stage: string;           // the staged app (build/package/app)
  engineOut: string;       // the staged engine and runtime (build/package; tools/stage-engine.ts)
  output: string;          // where the installer goes (dist)
  electronVersion: string;
}

export function packageConfig(p: ConfigPaths): Configuration {
  return {
    appId: APP_ID,
    productName: PRODUCT_NAME,
    executableName: EXECUTABLE_NAME,
    electronVersion: p.electronVersion,
    directories: { app: p.stage, output: p.output, buildResources: path.join(p.root, 'packaging') },
    // Only the staged files: everything the program uses is in its bundles.
    files: ['**/*', '!node_modules/**'],
    publish: null,
    asar: true,
    electronLanguages: ['ko', 'en-US'],   // Chromium's own strings: Korean, and its fallback
    npmRebuild: false,
    nodeGypRebuild: false,
    // The engine and its runtime (N-03, N-04; tools/stage-engine.ts); Help › Examples' circuits (D-158).
    extraResources: [...extraResources(p.engineOut), ...exampleResources(p.repo)],
    // The notices next to the executable as well as in About.
    extraFiles: [{ from: path.join(p.repo, 'LICENSE'), to: 'LICENSE.txt' }, { from: path.join(p.repo, 'NOTICE'), to: 'NOTICE.txt' }],
    win: {
      target: [{ target: 'nsis', arch: ['x64'] }],   // the installer only: no zip, no MSI, no portable
      icon: path.join(p.repo, 'assets/hallym/logo/app.ico'),
      signAndEditExecutable: true,                   // the icon and version in the .exe (no code signing: D-148)
    },
    nsis: {
      // Assisted (D-155): the progress, then the finish page; nothing asked (installer.nsh).
      oneClick: false,
      perMachine: false,
      allowToChangeInstallationDirectory: false,
      allowElevation: false,
      packElevateHelper: false,          // no elevate.exe beside the program: it never asks for an administrator
      shortcutName: PRODUCT_NAME,
      createDesktopShortcut: false,
      createStartMenuShortcut: true,
      deleteAppDataOnUninstall: false,   // there is none (the lab-PC rule); a student's files are never the uninstaller's
      runAfterFinish: true,              // the finish page's "지금 실행하기", ticked (nothing under /S)
      // The finish pages' band in the app's navy with the symbol (tools/installer-art.py), not electron-builder's drawing.
      installerSidebar: path.join(p.root, 'packaging/installerSidebar.bmp'),
      uninstallerSidebar: path.join(p.root, 'packaging/uninstallerSidebar.bmp'),
      // No auto-update: no block map beside the installer for differential updates (and the
      // app package compresses as one, a smaller installer).
      differentialPackage: false,
      // The installer's few words in Korean (installer.nsh rewrites electron-builder's
      // Korean ones that put a particle after the program's name, N-20).
      installerLanguages: ['ko_KR'],
      language: '1042',
      include: path.join(p.root, 'packaging/installer.nsh'),
      artifactName: SETUP_ARTIFACT,
      uninstallDisplayName: `${PRODUCT_NAME} \${version}`,
    },
    // No fileAssociations, no protocols: .circ is left to what opens it now (D-122 4, D-148).
    linux: { target: ['dir'], icon: path.join(p.repo, 'assets/hallym/logo/app-256.png'), category: 'Education' },
  };
}
