/* Bundles the window's script (derived from Hallym MIPS v2.5.0
   electron/tools/build-ui.ts): src/renderer/app/app.ts and what it imports
   into build/renderer/app.js, which src/renderer/app/index.html loads.
   Also writes build/licenses/third-party.txt: the licenses of every npm
   package the app bundles (the window's and the main process's; the main
   process is bundled only by tools/package.ts).

     node tools/build-ui.ts [--watch]

   __HALLYM__ is where the window finds the university's marks and
   characters: in the source tree, the repository's assets/hallym/ (the
   originals, not copied); tools/package.ts puts them next to the page. */

import * as esbuild from 'esbuild';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { thirdPartyText } from './licenses.ts';

const root = path.join(import.meta.dirname, '..');
// From src/renderer/app/index.html to the repository's assets/hallym/.
export const HALLYM_FROM_PAGE = '../../../../assets/hallym';

export const rendererOptions = (hallym = HALLYM_FROM_PAGE): esbuild.BuildOptions => ({
  entryPoints: [path.join(root, 'src/renderer/app/app.ts')],
  outfile: path.join(root, 'build/renderer/app.js'),
  bundle: true,
  format: 'iife',
  target: 'chrome140',
  sourcemap: true,
  metafile: true,
  logLevel: 'info',
  define: { __HALLYM__: JSON.stringify(hallym) },
});
export const nodeOptions = (entry: string, outfile: string): esbuild.BuildOptions => ({
  entryPoints: [path.join(root, entry)],
  outfile,
  bundle: true,
  platform: 'node',
  format: 'esm',
  target: 'node24',
  external: ['electron'],
  metafile: true,
  banner: { js: "import { createRequire as __cr } from 'node:module'; const require = __cr(import.meta.url);" },
});

export async function writeThirdParty(metafiles: esbuild.Metafile[]): Promise<void> {
  const out = path.join(root, 'build/licenses/third-party.txt');
  mkdirSync(path.dirname(out), { recursive: true });
  writeFileSync(out, thirdPartyText(metafiles));
}

if (import.meta.main) {
  if (process.argv.includes('--watch')) {
    await (await esbuild.context(rendererOptions())).watch();
  } else {
    const ui = await esbuild.build(rendererOptions());
    const main = await esbuild.build({ ...nodeOptions('src/main/main.ts', 'main.js'), write: false, logLevel: 'silent' });
    await writeThirdParty([ui.metafile!, main.metafile!]);
  }
}
