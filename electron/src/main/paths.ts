/* Where the app's own files are (derived from Hallym MIPS v2.5.0
   electron/src/main/paths.ts).  Run from the source tree (npm run electron,
   the e2e tests) they are where the repository keeps them; in the packaged
   app, tools/package.ts bundles the main process into one file (defining
   HCS_BUNDLE) and puts everything else next to it:

     main.js  preload.cjs  renderer/app/{index.html, *.css, app.js}
     renderer/assets/  licenses/                      (resources/app.asar)
     engine/engine.jar  runtime/                      (resources/, N-04, N-23)
     LICENSE  NOTICE  LICENSE.electron.txt  LICENSES.chromium.html   (next to the executable) */

import { readFileSync } from 'node:fs';
import path from 'node:path';

export const bundled = process.env.HCS_BUNDLE === '1';
const here = import.meta.dirname;
export const electronRoot = bundled ? here : path.join(here, '..', '..');
const electronDist = () => path.dirname(process.execPath); // the Electron binary's folder, in both cases

export const paths = {
  page: bundled ? path.join(here, 'renderer/app/index.html') : path.join(electronRoot, 'src/renderer/app/index.html'),
  preload: path.join(here, 'preload.cjs'),
  // The source tree's root (the engine's jar is under it); none when packaged.
  repoRoot: bundled ? null : path.join(electronRoot, '..'),
  icon: bundled ? null : path.join(electronRoot, '..', 'assets/hallym/logo/app-256.png'),
  // A license file by its name in licenses/ (the packaged name; see LICENSES).
  license: (name: string) => (bundled ? path.join(here, 'licenses', name) : path.join(electronRoot, LICENSE_SOURCES[name])),
  electronLicense: () => path.join(electronDist(), bundled ? 'LICENSE.electron.txt' : 'LICENSE'),
  chromiumCredits: () => path.join(electronDist(), 'LICENSES.chromium.html'),
};

export const version: string = bundled
  ? (process.env.HCS_VERSION as string)
  : JSON.parse(readFileSync(path.join(electronRoot, 'package.json'), 'utf8')).version;

/* The notices About shows, in order: title, and the file in the source tree.
   tools/package.ts copies each into licenses/ under the same key. */
export const LICENSES: { name: string; title: string }[] = [
  { name: 'LICENSE', title: 'Hallym Circuit Studio — GNU General Public License, version 2 or later' },
  { name: 'NOTICE', title: 'NOTICE — Logisim 2.7.1, Hallym MIPS, Electron, fonts, icons, the university\'s marks' },
  { name: 'LICENSE.hallym-mips.txt', title: 'Hallym MIPS Simulator — BSD 3-Clause License (the screen code taken from it)' },
  { name: 'hallym-assets.md', title: 'Hallym University assets (marks, characters, the first screen\'s video, app icon)' },
  { name: 'LICENSE.openjdk.txt', title: 'OpenJDK runtime (Eclipse Temurin 21.0.12) — GNU General Public License, version 2, with the Classpath Exception' },
  { name: 'OFL-Pretendard.txt', title: 'Pretendard — SIL Open Font License 1.1' },
  { name: 'OFL-D2Coding.txt', title: 'D2Coding — SIL Open Font License 1.1' },
  { name: 'lucide-LICENSE.txt', title: 'Lucide icons — ISC License' },
  { name: 'third-party.txt', title: 'Bundled npm packages' },
];

// Relative to electron/ (LICENSE and NOTICE are the repository's, at its root).
export const LICENSE_SOURCES: Record<string, string> = {
  'LICENSE': '../LICENSE',
  'NOTICE': '../NOTICE',
  'LICENSE.hallym-mips.txt': 'LICENSE.hallym-mips.txt',
  'hallym-assets.md': 'hallym-assets.md',
  'LICENSE.openjdk.txt': 'LICENSE.openjdk.txt',   // the bundled runtime's (N-04); its full texts are in runtime/legal/
  'OFL-Pretendard.txt': 'src/renderer/assets/fonts/OFL-Pretendard.txt',
  'OFL-D2Coding.txt': 'src/renderer/assets/fonts/OFL-D2Coding.txt',
  'lucide-LICENSE.txt': 'src/renderer/assets/icons/lucide/LICENSE.txt',
  'third-party.txt': 'build/licenses/third-party.txt', // tools/build-ui.ts writes it
};
