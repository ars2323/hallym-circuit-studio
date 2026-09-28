/* Packages the app with electron-builder (derived from Hallym MIPS v2.3.0
   electron/tools/package.ts).  The engine and its bundled Java runtime
   (N-04) go in, built first on the OS packaged for:
   ./gradlew :engine:stage :engine:runtime

     node tools/package.ts            Windows: the setup exe, one file (N-23, D-148)
     node tools/package.ts --dir      this platform, unpacked only (a check)
       --version <v>                  another version than package.json's (CI: an
                                      "earlier" installer to install over)
       --out <dir>                    the installer somewhere else than dist/
       --win                          on Linux: the Windows installer, to check the
                                      NSIS script (the .exe keeps Electron's icon and
                                      version: editing them needs Windows)

   1. Stages build/package/app/: the main process bundled by esbuild
      (HCS_BUNDLE defined: src/main/paths.ts then looks next to the bundle),
      the window, its assets, the university's marks and characters (the
      repository's originals, copied byte for byte), and the notices.  No
      node_modules: everything is bundled.
   2. Stages the engine beside it (tools/stage-engine.ts):
      build/package/engine/ (hcs-engine.jar, hcs-mips.jar from
      ../engine/build/stage) and build/package/runtime/ (the jlink runtime
      with its AppCDS archive, ../engine/build/runtime).
   3. Runs electron-builder on it with tools/package-config.ts: those two go
      into resources/engine and resources/runtime (extraResources), where
      src/main/engine-locate.ts looks for them -- packaged, the app runs the
      engine on that runtime only.  On Windows the result is the one-click,
      per-user NSIS installer HallymCircuitStudio-<version>-win-x64-setup.exe
      (packaging/installer.nsh: its folder, the v1.0.x MSI removed, its
      words). */

import { Arch, build as electronBuild, Platform } from 'electron-builder';
import * as esbuild from 'esbuild';
import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { LICENSE_SOURCES } from '../src/main/paths.ts';
import { nodeOptions, rendererOptions, writeThirdParty } from './build-ui.ts';
import { APP_ID, packageConfig } from './package-config.ts';
import { DEFAULT_STAGE_PATHS, stageEngine } from './stage-engine.ts';

const root = path.join(import.meta.dirname, '..');
const repo = path.join(root, '..');
const pkg = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'));
const electronVersion = JSON.parse(readFileSync(path.join(root, 'node_modules/electron/package.json'), 'utf8')).version;
const stage = path.join(root, 'build/package/app');
const at = (...p: string[]) => path.join(stage, ...p);
const dirOnly = process.argv.includes('--dir');
const option = (name: string): string | null => {
  const i = process.argv.indexOf(name);
  return i >= 0 && i + 1 < process.argv.length ? process.argv[i + 1] : null;
};
const version: string = option('--version') ?? pkg.version;
const output = path.resolve(root, option('--out') ?? 'dist');
const crossWindows = process.argv.includes('--win') && process.platform !== 'win32';

export { APP_ID };
// The marks and characters the window shows, from the page (renderer/app/): renderer/hallym/.
export const PACKAGED_HALLYM = '../hallym';

async function stageApp(): Promise<void> {
  rmSync(stage, { recursive: true, force: true });
  mkdirSync(stage, { recursive: true });
  const define = { 'process.env.HCS_BUNDLE': '"1"', 'process.env.HCS_VERSION': JSON.stringify(version) };
  const main = await esbuild.build({ ...nodeOptions('src/main/main.ts', at('main.js')), define });
  const ui = await esbuild.build({ ...rendererOptions(PACKAGED_HALLYM), outfile: at('renderer/app/app.js'), sourcemap: false });
  await writeThirdParty([ui.metafile!, main.metafile!]);

  const html = readFileSync(path.join(root, 'src/renderer/app/index.html'), 'utf8');
  const packagedHtml = html.replace('src="../../../build/renderer/app.js"', 'src="app.js"');
  if (packagedHtml === html) throw new Error('index.html: the script tag to rewrite was not found');
  writeFileSync(at('renderer/app/index.html'), packagedHtml);
  cpSync(path.join(root, 'src/renderer/app/app.css'), at('renderer/app/app.css'));
  cpSync(path.join(root, 'src/renderer/app/find.css'), at('renderer/app/find.css'));
  cpSync(path.join(root, 'src/renderer/shared/shared.css'), at('renderer/shared/shared.css'));
  cpSync(path.join(root, 'src/renderer/canvas/canvas.css'), at('renderer/canvas/canvas.css'));
  cpSync(path.join(root, 'src/renderer/shared/panels.css'), at('renderer/shared/panels.css'));
  cpSync(path.join(root, 'src/renderer/app/cycle.css'), at('renderer/app/cycle.css'));
  cpSync(path.join(root, 'src/renderer/assets'), at('renderer/assets'), { recursive: true });
  for (const d of ['character', 'logo']) cpSync(path.join(repo, 'assets/hallym', d), at('renderer/hallym', d), { recursive: true });
  cpSync(path.join(root, 'src/main/preload.cjs'), at('preload.cjs'));
  for (const [name, source] of Object.entries(LICENSE_SOURCES)) cpSync(path.join(root, source), at('licenses', name));

  writeFileSync(at('package.json'), JSON.stringify({
    name: 'hallym-circuit-studio', productName: 'Hallym Circuit Studio', version,
    description: 'Circuit editor and simulator for Hallym University (based on Logisim 2.7.1)',
    author: 'AIAC Lab, Hallym University', license: 'GPL-2.0-or-later', type: 'module', main: 'main.js',
  }, null, 1));
}

export const config = packageConfig({ root, repo, stage, engineOut: DEFAULT_STAGE_PATHS.out, output, electronVersion });

if (import.meta.main) {
  stageEngine(crossWindows ? { ...DEFAULT_STAGE_PATHS, platform: 'win32' } : DEFAULT_STAGE_PATHS);
  await stageApp();
  if (crossWindows) {
    await electronBuild({ config: { ...config, win: { ...config.win, signAndEditExecutable: false } }, targets: Platform.WINDOWS.createTarget('nsis', Arch.x64), publish: 'never' });
  } else {
    await electronBuild({ config, dir: dirOnly, publish: 'never' });
  }
}
