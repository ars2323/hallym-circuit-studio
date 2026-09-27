/* Every e2e test at the window sizes the app is made for (derived from
   Hallym MIPS v2.3.0 electron/tools/e2e-widths.ts): the lab PCs' 1920x1080
   maximised over the taskbar at 100 % (1920x1032), 125 % (1536x816) and
   150 % (1280x672), and half of a 1920 screen (960x1032: narrow, Attributes
   in the left panel's tabs).  Tests that size their own windows keep theirs;
   every other one runs at the size given (tests/e2e/harness.ts,
   HCS_E2E_SIZE).

     xvfb-run -a -s '-screen 0 2400x1400x24' node tools/e2e-widths.ts [playwright args]

   Prints a line per size and fails if any size failed. */

import { spawnSync } from 'node:child_process';
import path from 'node:path';

const root = path.join(import.meta.dirname, '..');
export const SIZES = ['1920x1032', '1536x816', '1280x672', '960x1032'];
const results: string[] = [];
let failed = false;
for (const size of SIZES) {
  const run = spawnSync('npx', ['playwright', 'test', ...process.argv.slice(2)], {
    cwd: root, env: { ...process.env, HCS_E2E_SIZE: size }, encoding: 'utf8', shell: process.platform === 'win32',
  });
  const out = `${run.stdout}${run.stderr}`;
  process.stdout.write(out);
  const count = (word: string) => Number(new RegExp(`(\\d+) ${word}`).exec(out)?.[1] ?? 0);
  results.push(`${size.padEnd(9)} passed ${count('passed')}, failed ${count('failed')}, flaky ${count('flaky')}, skipped ${count('skipped')}`);
  if (run.status !== 0) failed = true;
}
console.log(`\n${results.join('\n')}`);
process.exit(failed ? 1 : 0);
