/* Packages the app with electron-builder (derived from Hallym MIPS v2.3.0
   electron/tools/package.ts).  A skeleton: the real installer, its checks
   against earlier installs and its CI job are item N-23; the bundled Java
   runtime is N-04.

     node tools/package.ts            Windows: the NSIS installer, one file
     node tools/package.ts --dir      this platform, unpacked only (a check)

   1. Stages build/package/app/: the main process bundled by esbuild
      (HCS_BUNDLE defined: src/main/paths.ts then looks next to the bundle),
      the window, its assets, the university's marks and characters (the
      repository's originals, copied byte for byte), and the notices.  No
      node_modules: everything is bundled.
   2. Runs electron-builder on it.  The engine (engine/engine.jar) and its
      runtime (runtime/) go into resources/ once N-03 and N-04 make them;
      src/main/engine-locate.ts looks for them there.

   Like Hallym MIPS: per user, one click, no elevation, no desktop shortcut,
   no file association yet (.circ is N-23's to decide), nothing kept in
   %APPDATA% (the lab-PC rule: src/main/run-folder.ts). */

import { build as electronBuild, type Configuration } from 'electron-builder';
import * as esbuild from 'esbuild';
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { LICENSE_SOURCES } from '../src/main/paths.ts';
import { nodeOptions, rendererOptions, writeThirdParty } from './build-ui.ts';

const root = path.join(import.meta.dirname, '..');
const repo = path.join(root, '..');
const pkg = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'));
const electronVersion = JSON.parse(readFileSync(path.join(root, 'node_modules/electron/package.json'), 'utf8')).version;
const stage = path.join(root, 'build/package/app');
const at = (...p: string[]) => path.join(stage, ...p);
const dirOnly = process.argv.includes('--dir');

export const APP_ID = 'kr.ac.hallym.circuit-studio';
// The marks and characters the window shows, from the page (renderer/app/): renderer/hallym/.
export const PACKAGED_HALLYM = '../hallym';

async function stageApp(): Promise<void> {
  rmSync(stage, { recursive: true, force: true });
  mkdirSync(stage, { recursive: true });
  const define = { 'process.env.HCS_BUNDLE': '"1"', 'process.env.HCS_VERSION': JSON.stringify(pkg.version) };
  const main = await esbuild.build({ ...nodeOptions('src/main/main.ts', at('main.js')), define });
  const ui = await esbuild.build({ ...rendererOptions(PACKAGED_HALLYM), outfile: at('renderer/app/app.js'), sourcemap: false });
  await writeThirdParty([ui.metafile!, main.metafile!]);

  const html = readFileSync(path.join(root, 'src/renderer/app/index.html'), 'utf8');
  const packagedHtml = html.replace('src="../../../build/renderer/app.js"', 'src="app.js"');
  if (packagedHtml === html) throw new Error('index.html: the script tag to rewrite was not found');
  writeFileSync(at('renderer/app/index.html'), packagedHtml);
  cpSync(path.join(root, 'src/renderer/app/app.css'), at('renderer/app/app.css'));
  cpSync(path.join(root, 'src/renderer/shared/shared.css'), at('renderer/shared/shared.css'));
  cpSync(path.join(root, 'src/renderer/assets'), at('renderer/assets'), { recursive: true });
  for (const d of ['character', 'logo']) cpSync(path.join(repo, 'assets/hallym', d), at('renderer/hallym', d), { recursive: true });
  cpSync(path.join(root, 'src/main/preload.cjs'), at('preload.cjs'));
  for (const [name, source] of Object.entries(LICENSE_SOURCES)) cpSync(path.join(root, source), at('licenses', name));

  writeFileSync(at('package.json'), JSON.stringify({
    name: 'hallym-circuit-studio', productName: 'Hallym Circuit Studio', version: pkg.version,
    description: 'Circuit editor and simulator for Hallym University (based on Logisim 2.7.1)',
    author: 'AIAC Lab, Hallym University', license: 'GPL-2.0-or-later', type: 'module', main: 'main.js',
  }, null, 1));
}

export const config: Configuration = {
  appId: APP_ID,
  productName: 'Hallym Circuit Studio',
  executableName: 'HallymCircuitStudio',
  electronVersion,
  directories: { app: stage, output: path.join(root, 'dist'), buildResources: path.join(root, 'packaging') },
  files: ['**/*', '!node_modules/**'],
  publish: null,
  asar: true,
  electronLanguages: ['ko', 'en-US'],
  npmRebuild: false,
  nodeGypRebuild: false,
  // The engine and its runtime, once there (N-03, N-04).
  extraResources: [
    ...(existsSync(path.join(root, 'build/package/engine')) ? [{ from: path.join(root, 'build/package/engine'), to: 'engine' }] : []),
    ...(existsSync(path.join(root, 'build/package/runtime')) ? [{ from: path.join(root, 'build/package/runtime'), to: 'runtime' }] : []),
  ],
  // The notices next to the executable as well as in About.
  extraFiles: [{ from: path.join(repo, 'LICENSE'), to: 'LICENSE.txt' }, { from: path.join(repo, 'NOTICE'), to: 'NOTICE.txt' }],
  win: {
    target: ['nsis'],
    icon: path.join(repo, 'assets/hallym/logo/app.ico'),
    signAndEditExecutable: true,
  },
  nsis: {
    oneClick: true,
    perMachine: false,
    allowElevation: false,
    shortcutName: 'Hallym Circuit Studio',
    createDesktopShortcut: false,
    createStartMenuShortcut: true,
    deleteAppDataOnUninstall: false,
    runAfterFinish: false,
    include: path.join(root, 'packaging/installer.nsh'),
    artifactName: 'HallymCircuitStudio-${version}-win-x64-setup.${ext}',
    uninstallDisplayName: 'Hallym Circuit Studio ${version}',
  },
  linux: { target: ['dir'], icon: path.join(repo, 'assets/hallym/logo/app-256.png'), category: 'Education' },
};

if (import.meta.main) {
  await stageApp();
  await electronBuild({ config, dir: dirOnly, publish: 'never' });
}
